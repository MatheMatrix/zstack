# 错误显示规范 (Error Display Contract)

本文档定义了客户端开发者应当如何处理并展示来自 ZStack API 的错误信息。通过遵循此规范，可以确保用户在遇到问题时获得一致、准确且易于理解的反馈。

## 1. 错误显示层次 (Error Display Hierarchy)

根据错误的影响范围和严重程度，建议采用以下四个层次进行展示：

-   **轻提示 (Toast)**：仅展示 `localizedMessage`。适用于后台操作反馈或不中断用户流程的次要错误。
-   **对话框 (Dialog)**：展示 `localizedMessage`，并提供“更多信息”折叠面板展示 `code` 和 `category`。适用于需要用户明确感知并可能需要采取行动的错误。
-   **详情面板 (Detail Panel)**：完整展示 ErrorCode 结构，包括递归的 `cause` 链。适用于任务详情页或操作失败的历史记录。
-   **调试日志 (Log/Debug)**：展示完整的 JSON 报文，包括 `requestId` 和 `timestamp`（如果可用）。用于协助管理员或技术支持定位复杂问题。

## 2. 字段用途说明 (Field Usage Guide)

ErrorCode 包含以下关键字段，开发者应根据场景选择使用：

| 字段 | 说明 | 示例 |
| :--- | :--- | :--- |
| `code` | 错误码标识符，用于程序识别或文档查阅。 | `SYS.1001` |
| `description` | 错误类型的简短描述（通常为英文大写）。 | `OPERATION_ERROR` |
| `details` | 包含上下文信息的错误详情。 | `VM [uuid:xxx] cannot be stopped...` |
| `elaboration` | 扩展的解释性文本，通常包含解决建议。 | `请检查云主机当前状态是否允许停止。` |
| `localizedMessage` | **首选展示字段**。已根据请求语言预本地化的文本。 | `虚拟机停止失败：当前状态不允许此操作` |
| `category` | 错误分类，用于决定 UI 的展示风格或逻辑。 | `OPERATION_ERROR` |
| `retryable` | 布尔值，指示该操作是否可以通过重试解决。 | `false` |
| `httpStatus` | 建议的 HTTP 状态码。 | `503` |
| `messageKey` | 国际化查找键，用于客户端自定义翻译。 | `vm.stop.failed` |
| `params` | 国际化格式化参数，配合 `messageKey` 使用。 | `{"vmName": "test-vm"}` |
| `cause` | 嵌套的错误对象，指向问题的根因。 | `{ "code": "KVM.100", ... }` |
| `globalErrorCode` | 内部统一错误码，用于跨组件的国际化映射。 | `err.vm.invalid_state` |

## 3. SDK 使用示例

### TypeScript (前端)

```typescript
// 使用新的信封字段进行错误展示
function displayError(error: ErrorCode) {
    // 1. 优先使用 localizedMessage 进行轻提示
    const message = error.localizedMessage || error.details || error.description;
    showToast(message);
    
    // 2. 根据分类处理逻辑
    if (error.category === 'AUTH_ERROR') {
        // 权限错误，重定向至登录页
        redirectToLogin();
    } else if (error.retryable) {
        // 可重试错误，展示带重试按钮的对话框
        showRetryDialog(message, () => retryOperation());
    }
}
```

### Java (后端/SDK 客户端)

```java
ErrorCode error = result.getError();

// 使用 SDK 提供的便捷方法获取展示信息
// 优先级：localizedMessage -> details -> description
String displayMsg = error.getDisplayMessage();

if (Boolean.TRUE.equals(error.getRetryable())) {
    // 执行重试逻辑
}
```

## 4. 变更前后对比 (Before/After Comparison)

### 旧版本格式 (仅基本信息)

```json
{
    "code": "SYS.1001",
    "description": "OPERATION_ERROR",
    "details": "VM cannot be stopped in its current state [Running]"
}
```

### 新版本格式 (增强信封)

```json
{
    "code": "SYS.1001",
    "description": "OPERATION_ERROR",
    "details": "VM cannot be stopped in its current state [Running]",
    "category": "OPERATION_ERROR",
    "messageKey": "vm.stop.failed.invalid_state",
    "localizedMessage": "虚拟机停止失败：当前状态 [运行中] 不允许此操作",
    "retryable": false,
    "httpStatus": 409,
    "params": {
        "state": "Running"
    }
}
```

## 5. 分类与 UI 行为映射 (Category → UI Behavior)

通过 `category` 字段，客户端可以实现更智能的错误处理：

| 分类 (Category) | 推荐 UI 行为 |
| :--- | :--- |
| `OPERATION_ERROR` | 展示错误信息；若 `retryable` 为 true，提供重试选项。 |
| `TIMEOUT` | 展示超时提示，建议用户稍后重试。 |
| `INTERNAL` | 展示通用错误提示（如“系统内部错误”），建议联系管理员。 |
| `AUTH_ERROR` | 引导用户登录或提示权限不足。 |
| `INVALID_ARGUMENT` | 在表单项下方高亮显示具体的校验失败信息。 |
| `RESOURCE_NOT_FOUND` | 展示“资源不存在”页面或提示，更新列表状态。 |
| `IO_ERROR` | 提示存储或网络 I/O 异常，检查物理环境。 |
| `HTTP_ERROR` | 处理底层的网络通信错误。 |
| `CANCEL_ERROR` | 默默处理或展示操作已取消的轻提示。 |


## 6. 硬性契约 — 不可违反 (Mandatory Contract Rules)

> ⚠️ 以下三条规则为前端团队与后端的硬性约定，任何变更必须经过前后端双方 code review 确认。违反将导致前端返工。

### 规则 1: message/error 字段必须与响应状态一致

- **失败响应**：`error` 字段 **必须** 包含完整的 ErrorCode 对象（含 `code`、`description`、`details` 等）
- **成功响应**：`error` 字段 **必须** 为 `null`，**绝对禁止** 出现任何错误相关信息
- **灰色地带禁令**：不允许出现"成功但携带警告/失败原因"的混合态。如需传递警告，必须使用独立的 `warnings` 字段（当前不存在，需另行设计）

```
// ✅ 正确：失败响应
{"success": false, "error": {"code": "SYS.1001", "description": "...", ...}}

// ✅ 正确：成功响应
{"success": true, "error": null}

// ❌ 禁止：成功但带错误信息
{"success": true, "error": {"code": "SYS.1001", ...}}
```

### 规则 2: 成功态绝对禁止 message

- 成功响应（`success=true`）中，**不得** 出现 `message`、`errorMessage`、`failReason` 或任何语义上表示"失败原因"的字段
- `localizedMessage` 字段 **仅** 存在于 ErrorCode 对象内部，成功响应中 ErrorCode 对象为 null，因此 `localizedMessage` 自然不会出现
- 如果业务需要在成功响应中携带提示信息，**必须** 使用明确区分于错误的字段名（如 `notice`、`tip`），且需单独设计、单独评审

### 规则 3: globalErrorCode 的唯一归属层级 — API 级

- `globalErrorCode` 是 **API 级** 的全局错误标识符，由 `ErrorFacadeImpl` 统一注册和分配
- **禁止** 在 action 级、task 级或 UI 组件级重新定义或覆盖 `globalErrorCode` 的含义
- 前端 **仅** 通过 API 响应中的 `globalErrorCode` 进行国际化映射，不会也不应该从其他层级获取此字段
- 任何新增 `globalErrorCode` 必须在 `ErrorFacadeImpl` 中注册，并同步更新 i18n 资源文件
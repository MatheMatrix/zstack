# VM 元数据 — 集成测试计划

> 集成测试需真实 DB（H2 内存/MySQL）和模拟 Agent，验证跨模块协作正确性。
> 单元测试见 [Part 7a](vm-metadata-07a-单元测试计划.md)，故障注入见 [Part 7c](vm-metadata-07c-故障注入测试.md)，性能测试见 [Part 7d](vm-metadata-07d-性能与补充测试.md)。

## 目录

1. [sblk 写入与读取](#1-sblk-写入与读取)
2. [local/NFS JSON 读写](#2-localnfs-json-读写)
3. [Poller 端到端流程](#3-poller-端到端流程)
4. [API 拦截器与 markDirty 联动](#4-api-拦截器与-markdirty-联动)
5. [存储迁移元数据链路](#5-存储迁移元数据链路)
6. [注册端到端流程](#6-注册端到端流程)
7. [路径指纹巡检端到端](#7-路径指纹巡检端到端)
8. [API 端到端](#8-api-端到端)

---

## 1. sblk 写入与读取

**覆盖约束**：Part 4a–4e, C-01C-2

### 1.1 基础写入读取

| 用例 ID | 场景 | 前置条件 | 步骤 | 期望 |
|---------|------|----------|------|------|
| IT-SBLK-01 | 首次写入 + 读取 | 新建 4MB LV（mock Agent） | writeMetadata(vmUuid, payload) → readMetadata(vmUuid) | 读取内容与写入 payload 完全一致 |
| IT-SBLK-02 | 覆盖写入 A/B Slot 切换 | 已写入 v1 | writeMetadata(v2) → 验证 Header.ActiveSlot 切换 | ActiveSlot 从 0→1（或 1→0） |
| IT-SBLK-03 | 连续 3 次写入后读取 | 空 LV | 写 v1→v2→v3 → readMetadata | 读取到 v3；WriteSequence=3 |
| IT-SBLK-04 | LV 命名格式验证 | — | initializeMetadata(vmUuid) | LV name = `{vm_uuid}_vmmeta`，长度 ≤ 39 字符 |

### 1.2 Payload 大小与自动扩容

| 用例 ID | 场景 | 前置条件 | 步骤 | 期望 |
|---------|------|----------|------|------|
| IT-SBLK-10 | 小 payload（1KB） | 4MB LV | 写入 → 读取 | 成功，LV 未扩容 |
| IT-SBLK-11 | payload 超过 4MB LV 容量 | 4MB LV | 写入 2.5MB payload | Agent 触发 `lvextend` → LV 变为 8MB → 写入成功 |
| IT-SBLK-12 | payload 达 30MB 阈值 | 64MB LV | 写入 30MB + 1 payload | 返回 `VM_METADATA_PAYLOAD_TOO_LARGE` 错误 |
| IT-SBLK-13 | 扩容后 Header 布局更新 | 4MB→8MB | 写入 → 读取 Header | SlotA/B Offset 和 Capacity 反映 8MB 布局 |

### 1.3 op_type 与 storageStructureChange

| 用例 ID | 场景 | 前置条件 | 步骤 | 期望 |
|---------|------|----------|------|------|
| IT-SBLK-20 | CONFIG_UPDATE 写入 | dirty 行 storageStructureChange=false | flush → 观察 Agent 调用 | Agent 收到 op_type=1 (CONFIG_UPDATE) |
| IT-SBLK-21 | STORAGE_CHANGE 写入 | dirty 行 storageStructureChange=true | flush → 观察 Agent 调用 | Agent 收到 op_type=2 (STORAGE_CHANGE) |

### 1.4 deleteMetadata 幂等性

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-SBLK-30 | 删除存在的 LV | deleteMetadata(vmUuid) | Agent lvremove 成功 |
| IT-SBLK-31 | 删除不存在的 LV | deleteMetadata(vmUuid) | 不抛异常（C-01C-9） |
| IT-SBLK-32 | 双重删除 | delete → delete | 第二次幂等成功 |

---

## 2. local/NFS JSON 读写

**覆盖约束**：Part 1c §1.2, C-01C-10

### 2.1 基础读写

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-JSON-01 | 首次写入 | writeMetadata(vmUuid, payload) | 创建 `{mountPath}/.zstack-vm-metadata/{vmUuid}.json` |
| IT-JSON-02 | 读取刚写入的文件 | 写入 → readMetadata | 读取内容 == 写入 payload |
| IT-JSON-03 | 覆盖写入 | 写入 v1 → 写入 v2 → 读取 | 读取到 v2 |
| IT-JSON-04 | 容器目录自动创建 | `.zstack-vm-metadata/` 目录不存在 | writeMetadata 自动 mkdir -p（Δ-4） |

### 2.2 原子写入验证

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-JSON-10 | tmp+fsync+rename 原子性 | 写入过程中观察文件系统 | 先出现 `.sc.tmp` 文件 → rename 后仅有 `.json` |
| IT-JSON-11 | tmp 残留（Agent 重启前） | 手动创建 `.sc.tmp`，Agent 启动 | Agent 启动清理所有 `.sc.tmp` 文件（C-01C-10） |
| IT-JSON-12 | readMetadata 遇到 `.sc.tmp` | 只有 `.sc.tmp` 无 `.json` | 读取返回空/NOT_FOUND（不读 tmp） |

### 2.3 deleteMetadata 幂等性

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-JSON-20 | 删除存在的 JSON | deleteMetadata → 检查文件 | 文件已删除 |
| IT-JSON-21 | 删除不存在的文件 | 文件本不存在 → deleteMetadata | 不抛异常 |

---

## 3. Poller 端到端流程

**覆盖约束**：Part 2 §4, C-CL-02, C-TM-03, C-RB-04

### 3.1 正常 flush 链路

| 用例 ID | 场景 | 前置条件 | 步骤 | 期望 |
|---------|------|----------|------|------|
| IT-POL-01 | markDirty → Poller 认领 → flush → 成功删除 | 一个 UserVm | markDirty(vmUuid) → 等待 Poller 周期 | dirty 行被删除；存储有元数据；PathFingerprint 已记录 |
| IT-POL-02 | 多 VM 并发 flush | 5 个 VM 各有 dirty 行 | Poller 运行 | 5 个 VM 全部 flush 成功 |
| IT-POL-03 | Poller 无 dirty 行时空转 | 无 dirty 行 | Poller 运行 | SELECT 0 rows，正常返回 |
| IT-POL-04 | lastClaimTime 写入 | 认领一行 | 检查 DB | `lastClaimTime` 非 null（C-CL-02） |

### 3.2 flush 失败与重试

| 用例 ID | 场景 | 前置条件 | 步骤 | 期望 |
|---------|------|----------|------|------|
| IT-POL-10 | Agent 超时 → 退避重试 | mock Agent 超时 | Poller 认领 → flush 超时 | dirty 行保留，retryCount+1，nextRetryTime 设置退避 |
| IT-POL-11 | 重试 5 次耗尽 | mock Agent 持续失败 | 5 轮 Poller | dirty 行删除 + PathFingerprint.lastFlushFailed=true（C-SR-05） |
| IT-POL-12 | StaleRecoveryTask 重入队 | lastFlushFailed=true | 等待 stale recovery 周期 | markDirty(retryCount=0) + lastFlushFailed=false |

### 3.3 dirtyVersion 不匹配

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-POL-20 | flush 期间新 markDirty | flush 开始 → 另一线程 markDirty → flush 完成 | onFlushSuccess 检测 dirtyVersion 不匹配 → 释放认领（不删除 dirty 行） |
| IT-POL-21 | flush 后 dirtyVersion 匹配 | 正常 flush | dirty 行删除 |

### 3.4 并发控制

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-POL-30 | per-VM ChainTask maxPending=1 | 对同一 VM 快速提交 3 次 flush | running=1, pending=1, 第 3 次 exceedMaxPendingCallback 释放 |
| IT-POL-31 | globalFlushInFlight 上限 | mock 11 个 VM flush 中（maxConcurrent=10） | 第 11 个 submitFlushTask 时 AtomicInteger >= max → releaseClaim + 跳过 |
| IT-POL-32 | per-PS syncLevel=5 | 6 个 VM 在同一 PS | 同时只有 5 个在执行 Agent 写入 |

---

## 4. API 拦截器与 markDirty 联动

**覆盖约束**：Part 1b, C-PA

### 4.1 API 成功触发 markDirty

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-INT-01 | APIUpdateVmInstanceMsg（改名） | 发送 API → 成功 | afterCompletion → markDirty(vmUuid, CONFIG) → dirty 行创建 |
| IT-INT-02 | APICreateVolumeSnapshotMsg | 快照创建成功 | markDirty(vmUuid, STORAGE) + storageStructureChange=true |
| IT-INT-03 | APIDeleteVolumeMsg | 删除数据盘成功 | markDirty(vmUuid, STORAGE) |

### 4.2 API 失败不触发 markDirty

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-INT-10 | APIStopVmInstanceMsg 失败 | mock 停止失败 | afterCompletion 检测 reply.isSuccess()=false → 不 markDirty |
| IT-INT-11 | updateOnFailure=true 的 API 失败 | 批量 API 失败 | 仍然 markDirty |

### 4.3 pendingApis 超时清理

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-INT-20 | API 45 分钟无 afterCompletion | 注入 pending → 等待 timeout | pendingApis 自动清理 + 补偿 markDirty（C-PA） |
| IT-INT-21 | 正常 API 不超时 | API 正常完成 | pendingApis 正常移除，无超时触发 |

---

## 5. 存储迁移元数据链路

**覆盖约束**：Part 1c §1.4, C-01C-4 ~ C-01C-8

### 5.1 完整迁移 8 步

| 用例 ID | 场景 | 前置条件 | 期望 |
|---------|------|----------|------|
| IT-MIG-01 | sblk→sblk 迁移 | VM 在 PS-A 有元数据 | 8 步全部成功：PS-B 有完整元数据，PS-A 已清理 |
| IT-MIG-02 | local→NFS 迁移 | VM 在 local PS 有 JSON | PS-NFS 有 JSON，local 已清理 |
| IT-MIG-03 | 迁移期间 Poller 暂停 | 迁移开始 | nextRetryTime='2099-12-31'，Poller 跳过该 VM |
| IT-MIG-04 | 迁移完成后 Poller 恢复 | 迁移成功 | nextRetryTime=NULL + markDirty(storageStructureChange=true) |

### 5.2 迁移失败回滚

| 用例 ID | 场景 | 前置条件 | 期望 |
|---------|------|----------|------|
| IT-MIG-10 | Step 5 写入失败 | mock 目标 Agent 失败 | 回滚：deleteMetadata(目标) + nextRetryTime=NULL + markDirty(true) |
| IT-MIG-11 | Step 8 清理源端失败 | mock 源 Agent 失败 | WARN 日志，孤儿检测兜底（不阻塞迁移成功） |

### 5.3 MN 重启恢复

| 用例 ID | 场景 | 前置条件 | 期望 |
|---------|------|----------|------|
| IT-MIG-20 | MN 重启时 nextRetryTime=2099 | DB 有暂停行 | managementNodeReady 重置 nextRetryTime=NULL（C-01C-8） |

---

## 6. 注册端到端流程

**覆盖约束**：Part 3, C-03-1 ~ C-03-8

### 6.1 正常注册

| 用例 ID | 场景 | 前置条件 | 期望 |
|---------|------|----------|------|
| IT-REG-01 | 最小 VM 注册（根盘 only） | 有效 metadataContent JSON | VmInstanceVO(state=Stopped) + VolumeVO(Root) 已创建 |
| IT-REG-02 | 含快照链的 VM 注册 | 根盘 + 5 个快照 | 所有 VolumeSnapshotVO 按树结构创建 |
| IT-REG-03 | 含数据盘的 VM 注册 | 根盘 + 2 数据盘 | 3 个 VolumeVO 创建 |
| IT-REG-04 | 注册后 markDirty 触发 | 注册成功 | dirty 行已创建（storageStructureChange=true） |
| IT-REG-05 | 注册后 ConsistencyCheck | 注册成功 | 异步触发 ConsistencyCheck（C-03-7） |
| IT-REG-06 | registered.not.started ResourceConfig | 注册成功 | ResourceConfig 存在 → 后续 API 不触发 markDirty |

### 6.2 注册拒绝

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| IT-REG-10 | UUID 冲突（正常资源） | vmUuid 已存在且 state≠Registering | 拒绝 + 错误码 |
| IT-REG-11 | 跨存储拒绝 | 根盘在 PS-A，数据盘在 PS-B | `CROSS_STORAGE_REJECTED` + expected/actual PS UUIDs（C-03-2） |
| IT-REG-12 | Root installPath 不存在 | mock Agent 返回 false | BLOCK（拒绝注册）（C-03-6） |
| IT-REG-13 | readStatus=CORRUPTED | metadata.__readStatus="CORRUPTED" | 拒绝注册 |
| IT-REG-14 | schemaVersion 不匹配 | version=999 | 拒绝注册（未设 forceVersionMismatch） |
| IT-REG-15 | forceVersionMismatch=true | version=999 | 允许注册，warnings 列出忽略字段 |

### 6.3 注册回滚

| 用例 ID | 场景 | 前置条件 | 期望 |
|---------|------|----------|------|
| IT-REG-20 | 变基失败触发回滚 | mock qemu-img rebase 失败 | 所有 VO 按"由外到内"删除（C-03-4） |
| IT-REG-21 | Registering 遗留 UUID 冲突 → 回滚重试 | DB 有 state=Registering 的 VM | 自动回滚 → 重新注册 |
| IT-REG-22 | MN 重启扫描 Registering | DB 有 Registering VM | managementNodeReady 触发回滚 |
| IT-REG-23 | 回滚保留 TreeVO（其他 VM 共享） | TreeVO 下有其他 VM 的 ReferenceVO | 仅删除当前 VM 的 ReferenceVO，TreeVO 保留 |

---

## 7. 路径指纹巡检端到端

**覆盖约束**：Part 2b §8.2, C-02B-3

### 7.1 正常巡检

| 用例 ID | 场景 | 前置条件 | 期望 |
|---------|------|----------|------|
| IT-FP-01 | 无漂移 | VM 已 flush + fingerprint 记录 | 巡检通过，不 markDirty |
| IT-FP-02 | installPath 变更 | 手动修改 VolumeVO.installPath | 巡检检测 drift → markDirty |
| IT-FP-03 | keyset 分页遍历 | 510 个 VM（batchSize=500） | 分 2 批遍历完所有 VM |

### 7.2 边界

| 用例 ID | 场景 | 前置条件 | 期望 |
|---------|------|----------|------|
| IT-FP-10 | VM 从未 flush | 无 fingerprint 记录 | 巡检跳过 |
| IT-FP-11 | VM 已销毁（FK CASCADE） | VM 物理删除 | fingerprint 行自动级联删除 |

---

## 8. API 端到端

**覆盖约束**：Part 5

### 8.1 扫描 API

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-API-01 | 扫描 sblk PS | APIScanVmInstanceMetadataMsg(psUuid) | 返回 vmUuid + vmName + vmCategory 列表 |
| IT-API-02 | 扫描空 PS | 无元数据的 PS | 返回空列表 |

### 8.2 读取 API

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-API-10 | 读取正常元数据 | APIReadVmInstanceMetadataMsg | readStatus=OK + 完整 JSON |
| IT-API-11 | 读取损坏元数据 | 双 Slot 损坏 | readStatus=CORRUPTED |

### 8.3 一致性检查 API

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-API-20 | 一致时 | APICheckVmInstanceMetadataConsistencyMsg | 报告一致 |
| IT-API-21 | 不一致 + autoRepair=true | DB 与存储不一致 | 检测到差异 + 自动 markDirty |

### 8.4 手动更新 API

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-API-30 | APIUpdateVmMetadataMsg | 指定 vmUuid | markDirty 触发 → Poller flush → 存储更新 |

### 8.5 清理 API

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| IT-API-40 | enabled=false 时清理 | APICleanupVmInstanceMetadataMsg | 存储 + DB 清理成功 |
| IT-API-41 | enabled=true 时拒绝 | 同上 | `METADATA_CLEANUP_REJECTED_WHILE_ENABLED`（C-02B-12） |

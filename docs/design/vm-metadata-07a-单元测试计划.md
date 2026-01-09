# VM 元数据 — 单元测试计划

> 本文档为 VM 元数据功能的单元测试计划。单元测试聚焦**纯 Java 逻辑**，不依赖 DB/Agent/存储，通过 mock 隔离外部依赖。
> 集成测试见 [Part 7b](vm-metadata-07b-集成测试计划.md)，故障注入见 [Part 7c](vm-metadata-07c-故障注入测试.md)，性能测试见 [Part 7d](vm-metadata-07d-性能与补充测试.md)。

## 目录

1. [元数据序列化 Round-Trip](#1-元数据序列化-round-trip)
2. [DTO 字段完整性](#2-dto-字段完整性)
3. [路径指纹与漂移检测](#3-路径指纹与漂移检测)
4. [markDirty 逻辑](#4-markdirty-逻辑)
5. [MetadataImpact 注解覆盖率](#5-metadataimpact-注解覆盖率)
6. [VM UUID Resolver 链](#6-vm-uuid-resolver-链)
7. [Payload 容量计算](#7-payload-容量计算)
8. [sblk 二进制布局编解码](#8-sblk-二进制布局编解码)
9. [注册字段映射](#9-注册字段映射)
10. [installPath 前缀替换](#10-installpath-前缀替换)

---

## 1. 元数据序列化 Round-Trip

**覆盖约束**：Part 1a §2–§3

### 1.1 基础 Round-Trip

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-SER-01 | 最小 VM（单根盘、无快照、无数据盘） | 构造最小 `VmInstanceMetadataDTO` | Gson 序列化 → 反序列化 → 与原对象 `equals` |
| UT-SER-02 | 完整 VM（根盘 + 3 数据盘 + 快照链 + NIC + SystemTag + ResourceConfig） | 构造满字段 DTO | Round-Trip 后所有字段一致 |
| UT-SER-03 | 含 null 字段的 VM（imageUuid=null, instanceOfferingUuid=null） | DTO 部分字段为 null | Gson 序列化跳过 null 字段（`serializeNulls=false`），反序列化后 null 字段仍为 null |
| UT-SER-04 | 空快照列表 | `snapshots = Collections.emptyList()` | 序列化为 `"snapshots":[]`，反序列化后 `.size()==0` 且非 null |
| UT-SER-05 | 深度快照链（256 层嵌套 parentUuid） | 构造 depth=256 的链式快照 | Round-Trip 后 parentUuid 链完整保留 |

### 1.2 编码管线

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-SER-10 | SystemTag Base64 编码 Round-Trip | `List<SystemTagVO>` → JSON → Base64 → DTO.systemTags (String) | 解码后还原为等价 `List<SystemTagVO>` |
| UT-SER-11 | ResourceConfig Base64 编码 Round-Trip | 同上 | 解码后还原 |
| UT-SER-12 | 空 SystemTag 列表 | `systemTags = []` → Base64 | 编码非空字符串，解码还原为空列表 |
| UT-SER-13 | 含特殊字符的 tag（中文、emoji、`=` 分隔符） | `tag::key::中文值(fire)` | Round-Trip 后完整保留 |

### 1.3 JSON 确定性

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-SER-20 | 同一 DTO 多次序列化字节一致 | 同一对象序列化 10 次 | 10 次 `byte[]` 完全相同 |
| UT-SER-21 | 字段声明顺序稳定性 | 检查 Gson 输出中 `uuid` 在 `name` 前（按声明顺序） | JSON key 顺序与 Java 字段声明顺序一致 |
| UT-SER-22 | `@SerializedName` 注解生效 | DTO 中带 `@SerializedName("vm_uuid")` 的字段 | JSON key 为 `vm_uuid` 而非 Java 字段名 |

### 1.4 版本兼容

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-SER-30 | 反序列化缺失字段（旧版本数据） | 不含新版字段 `vmCategory` 的 JSON | 反序列化成功，`vmCategory==null` |
| UT-SER-31 | 反序列化多余字段（新版本数据） | JSON 含当前 DTO 无对应的 `futureField` | Gson 默认忽略未知字段，不报错 |
| UT-SER-32 | schemaVersion 精确匹配检查 | `expected=3, actual=2` | `isVersionMatch()` 返回 false |

---

## 2. DTO 字段完整性

**覆盖约束**：Part 1a §1, §4

### 2.1 快照树构建

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-DTO-01 | 空快照列表构建树 | `List<VolumeSnapshotVO> = []` | 返回空树列表 |
| UT-DTO-02 | 单棵树、线性链（A→B→C） | 3 个 VO，parentUuid 链式指向 | 树结构正确：root=A, A.children=[B], B.children=[C] |
| UT-DTO-03 | 多棵独立树 | 2 棵树各 3 个节点，volumeSnapshotTreeUuid 不同 | 返回 2 棵独立树 |
| UT-DTO-04 | 分叉树（A→B, A→C） | 3 个 VO，B 和 C 的 parentUuid 都指向 A | A.children 包含 B 和 C |
| UT-DTO-05 | 共享磁盘快照排除 | 快照列表含 `VolumeVO.isShareable=true` 的卷快照 | 构建时跳过该卷的快照 |

### 2.2 VolumeSnapshotReferenceVO 查询范围

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-DTO-10 | 按 referenceVolumeUuid 查询（非 volumeUuid） | mock DB 返回：ref.referenceVolumeUuid 匹配当前 VM 卷 | 仅返回当前 VM 的引用记录，不含父模板的引用 |
| UT-DTO-11 | VM 无引用记录 | `referenceVolumeUuid` 无匹配 | 返回空列表 |

### 2.3 SystemTag/ResourceConfig 白名单过滤

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-DTO-20 | 白名单内 tag 保留 | `bootMode::UEFI` | 保留在构建结果中 |
| UT-DTO-21 | 白名单外 tag 过滤 | `ephemeral::true`（假设不在白名单） | 不在构建结果中 |
| UT-DTO-22 | ResourceConfig 按类型分组 | VM 级 + Volume 级 config | 分别归入 `vmConfigs` 和 `volumeConfigs` |

---

## 3. 路径指纹与漂移检测

**覆盖约束**：Part 2b §8.2

### 3.1 路径快照构建

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-FP-01 | 正常路径快照 JSON | 2 个 Volume + 3 个 Snapshot（有序） | JSON 中 volumes/snapshots 按 uuid ASC 排列 |
| UT-FP-02 | 相同拓扑的确定性 | 两次构建（不同对象实例，相同内容） | JSON 字符串 `byte[]` 完全相同 |
| UT-FP-03 | 空快照列表 | 仅有 Volume 无 Snapshot | `"snapshots":[]`，不影响比对 |

### 3.2 路径漂移检测

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-FP-10 | 无漂移 | recorded == current | 不触发 markDirty |
| UT-FP-11 | Volume installPath 变更 | current 中 vol-aaa 路径不同 | 检测到 drift → 调用 markDirty |
| UT-FP-12 | Snapshot 新增 | current 中多一个 snapshot | 检测到 drift |
| UT-FP-13 | Snapshot 删除 | current 中少一个 snapshot | 检测到 drift |

---

## 4. markDirty 逻辑

**覆盖约束**：C-DM-01, C-SC-07, C-FL-08

### 4.1 标脏语义

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-MD-01 | 首次 markDirty | vmUuid 不存在 dirty 行 | INSERT IGNORE 创建新行，`dirtyVersion=1` |
| UT-MD-02 | 重复 markDirty | vmUuid 已有 dirty 行 | UPDATE `dirtyVersion+1`，行唯一 |
| UT-MD-03 | storageStructureChange OR 语义 | 先 markDirty(false) 再 markDirty(true) | dirty 行 `storageStructureChange=true`（不会被覆盖回 false） |
| UT-MD-04 | storageStructureChange 反向不降级 | 先 markDirty(true) 再 markDirty(false) | 仍为 `storageStructureChange=true` |
| UT-MD-05 | vm.metadata.enabled=false 时 markDirty | 开关关闭 | markDirty 直接 return，不创建 dirty 行 |
| UT-MD-06 | Destroyed VM 不标脏 | vmState=Destroyed | markDirty 直接 return（C-FL-08 前置过滤） |

### 4.2 retryCount 与退避

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-MD-10 | 新 markDirty 的 retryCount | 首次创建 | `retryCount=0, nextRetryTime=NULL` |
| UT-MD-11 | markDirty 不重置已有 retryCount | dirty 行 retryCount=3 时再次 markDirty | retryCount 不变（仅递增 dirtyVersion） |
| UT-MD-12 | 指数退避计算 | baseDelay=10s, maxExponent=10, retryCount=3 | `nextRetryTime = now + 10 * 2^3 = 80s` |
| UT-MD-13 | 退避上限 | retryCount=15（超过 maxExponent=10） | `nextRetryTime = now + 10 * 2^10 = 10240s`（封顶） |

---

## 5. MetadataImpact 注解覆盖率

**覆盖约束**：C-IM

### 5.1 CI 扫描验证

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-AN-01 | 所有 APIMessage 子类有 @MetadataImpact | 反射扫描所有 `APIMessage` 子类 | 每个子类都标注了 `@MetadataImpact`（NONE/CONFIG/STORAGE） |
| UT-AN-02 | STORAGE 级 API 不误标为 CONFIG | 检查 APIDeleteVolumeSnapshotMsg 等 | 快照/迁移/删盘类 API 标注为 `Impact.STORAGE` |
| UT-AN-03 | 纯查询 API 标注 NONE | 检查 QueryVmInstanceMsg 等 | `Impact.NONE` |

### 5.2 内部消息白名单

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-AN-10 | INTERNAL_METADATA_MESSAGES 包含所有 STORAGE 级内部消息 | 反射扫描 + 对比白名单 | 白名单完整 |
| UT-AN-11 | 白名单中每个消息的 handler 调用了 markDirty | 静态分析 / mock 验证 | 所有 handler 成功路径包含 markDirty 调用 |

---

## 6. VM UUID Resolver 链

**覆盖约束**：C-RS, Part 1b §3

### 6.1 Resolver 选择

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-RS-01 | VmInstanceMessage 直接获取 vmUuid | APIStopVmInstanceMsg(vmUuid="vm-1") | Resolver 返回 `["vm-1"]` |
| UT-RS-02 | VolumeMessage 通过 Volume→VM 查询 | APIDeleteVolumeMsg(volumeUuid="vol-1") + mock vol-1.vmInstanceUuid="vm-1" | 返回 `["vm-1"]` |
| UT-RS-03 | TagMessage 按 resourceType 路由 | APICreateSystemTagMsg(resourceUuid="vol-1", resourceType="VolumeVO") | 返回 vol-1 关联的 VM UUID |
| UT-RS-04 | 无法解析的 API | 自定义 API 无匹配 Resolver | 返回空列表 + WARN 日志 |
| UT-RS-05 | 删除/卸载类 API 使用 pre-capture | APIDetachVolumeMsg | `resolveVmUuids()` 在 `beforeDeliveryMessage` 阶段调用（C-RS） |
| UT-RS-06 | 单个 API 关联多个 VM | 批量 Tag 操作涉及多个 VM | 返回所有关联 VM UUID（去重） |

---

## 7. Payload 容量计算

**覆盖约束**：Part 2b §10.0, C-02B-5, C-02B-7

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-CAP-01 | 4MB LV 的 slotCapacity | `lvSize = 4*1024*1024` | `slotCapacity = ((4MB - 4096) / 2 / 4096) * 4096 = 2,093,056` |
| UT-CAP-02 | 64MB LV（上限）的 slotCapacity | `lvSize = 64*1024*1024` | `slotCapacity = 33,550,336` |
| UT-CAP-03 | payload 可用空间 | `slotCapacity - 36 (SlotHeader)` | 4MB LV → 2,093,020 bytes；64MB LV → 33,550,300 bytes |
| UT-CAP-04 | WARN 阈值判定 | payloadSize = 8MB + 1 | 触发 WARN |
| UT-CAP-05 | REJECT 阈值判定 | payloadSize = 30MB + 1 | 触发 ERROR + 拒绝写入 |
| UT-CAP-06 | 常量集中定义验证 | 反射检查 `VmMetadataConstants` 类 | HEADER_SIZE/SLOT_HEADER_SIZE/MAX_LV_SIZE 均为 static final |

---

## 8. sblk 二进制布局编解码

**覆盖约束**：Part 4b

### 8.1 Header 编解码

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-BIN-01 | Header 序列化 Round-Trip | 构造 Header（Magic, ActiveSlot=0, WriteSequence=1, PendingOp=0） | 序列化为 4096 bytes → 反序列化还原 |
| UT-BIN-02 | Magic 校验 | `0x5A534D54` | 读取 Header 校验通过 |
| UT-BIN-03 | Magic 错误 | `0xDEADBEEF` | 读取 Header 抛出 `InvalidHeaderException` |
| UT-BIN-04 | ControlChecksum 校验 | 正常 Header + SHA-256 | checksum 验证通过 |
| UT-BIN-05 | ControlChecksum 篡改 | 修改 Header 一个字节后不更新 checksum | checksum 验证失败 |

### 8.2 Slot 编解码

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-BIN-10 | Slot Header 序列化 | SlotMagic + SeqNum + Offset + Capacity + PayloadLen | 序列化为 36 bytes |
| UT-BIN-11 | Payload 写入与读取 | 1KB payload → 写入 Slot → 读取 | payload 内容一致 |
| UT-BIN-12 | Payload 恰好填满 Slot | payloadLen == slotCapacity - 36 | 写入成功，无溢出 |
| UT-BIN-13 | Payload 超过 Slot 容量 | payloadLen > slotCapacity - 36 | 抛出 `PayloadTooLargeException` |

### 8.3 VM 摘要区

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-BIN-20 | 摘要区序列化 | vmUuid(32) + vmName(256) + vmCategory(1) | 写入 [96:928) 区域 |
| UT-BIN-21 | vmName 超过 256 bytes | UTF-8 编码后 > 256 | 截断到 256 bytes 边界（不截断 UTF-8 多字节字符中间） |
| UT-BIN-22 | SummaryChecksum 验证 | 正常摘要区 | SHA-256 通过 |

### 8.4 WriteSequence 边界

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-BIN-30 | 正常递增 | seq=100 → 写入 → seq=101 | WriteSequence +1 |
| UT-BIN-31 | Long.MAX_VALUE 溢出 | seq=Long.MAX_VALUE | 写入后 seq=Long.MIN_VALUE（Java long 自然溢出），比较逻辑使用 `Long.compareUnsigned` 或差值判断 |

---

## 9. 注册字段映射

**覆盖约束**：Part 3 §1

### 9.1 VmInstanceVO 映射

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-REG-01 | uuid 保留 | 元数据 vmUuid="vm-aaa" | 注册后 VmInstanceVO.uuid="vm-aaa" |
| UT-REG-02 | hostUuid / lastHostUuid 置 null | 元数据含原始 hostUuid | 注册后两者均为 null |
| UT-REG-03 | state 硬编码 Registering→Stopped | — | 创建时 state=Registering，成功后改为 Stopped |
| UT-REG-04 | imageUuid 不存在时置 null | mock `dbf.findByUuid(imageUuid)` 返回 null | imageUuid=null + warnings 含提示信息 |
| UT-REG-05 | accountUuid 替换为当前调用者 | 元数据 accountUuid="old-admin" | 注册后 accountUuid = 当前 session 的 accountUuid |

### 9.2 VolumeVO 映射

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-REG-10 | primaryStorageUuid 替换 | 元数据 psUuid="old-ps" + 目标 psUuid="new-ps" | VolumeVO.primaryStorageUuid="new-ps" |
| UT-REG-11 | diskOfferingUuid 置 null | 元数据含原始 diskOfferingUuid | 注册后为 null |

### 9.3 快照 VO 映射

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-REG-20 | 快照 parentUuid 保留 | 链式快照 A→B→C | parentUuid 关系完整 |
| UT-REG-21 | SnapshotGroupVO accountUuid 替换 | 元数据含原始 accountUuid | 注册后替换为当前调用者 |
| UT-REG-22 | SnapshotGroupRefVO 路径替换 | volumeSnapshotInstallPath 含旧前缀 | 替换为新前缀 |
| UT-REG-23 | ReferenceVO parentId 统一置 null | 元数据含 parentId=5 | 注册后 parentId=null（C-03-1） |

---

## 10. installPath 前缀替换

**覆盖约束**：C-03-3, Part 3 §3.4

| 用例 ID | 场景 | 输入 | 期望 |
|---------|------|------|------|
| UT-PATH-01 | sblk VG UUID 替换 | `/dev/123xxx/vol-aaa` → oldPrefix=`/dev/123xxx/`, newPrefix=`/dev/456xxx/` | `/dev/456xxx/vol-aaa` |
| UT-PATH-02 | NFS 挂载路径替换 | `/mnt/old-nfs/vm-data/vol-aaa` → `/mnt/new-nfs/vm-data/vol-aaa` | 替换成功 |
| UT-PATH-03 | 分隔符边界保护 | oldPrefix=`/dev/oldVg`（无尾 `/`） | 替换拒绝或自动补 `/`（C-03-3） |
| UT-PATH-04 | 子串误命中防护 | oldPrefix=`/dev/vg1/`, 路径=`/dev/vg12/vol` | 不匹配，不替换（`startsWith` 精确匹配） |
| UT-PATH-05 | installPath 不匹配 oldPrefix | 路径前缀与 oldPrefix 不一致 | 报错（明确提示哪个路径不匹配） |
| UT-PATH-06 | 批量路径替换一致性 | 10 个 Volume + 20 个 Snapshot 的 installPath | 全部按相同规则替换，无遗漏 |

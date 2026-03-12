# VM 元数据 — 故障注入测试计划

> 故障注入测试验证系统在异常/极端条件下的安全恢复能力。需要 mock Agent 故障、模拟 MN 重启、注入 DB 异常。
> 单元测试见 [Part 7a](vm-metadata-07a-单元测试计划.md)，集成测试见 [Part 7b](vm-metadata-07b-集成测试计划.md)，性能测试见 [Part 7d](vm-metadata-07d-性能与补充测试.md)。

## 目录

1. [sblk 写入中断与 Crash Recovery](#1-sblk-写入中断与-crash-recovery)
2. [MN 重启恢复](#2-mn-重启恢复)
3. [双 MN 故障转移](#3-双-mn-故障转移)
4. [DB 异常](#4-db-异常)
5. [Agent 异常](#5-agent-异常)
6. [功能开关切换竞态](#6-功能开关切换竞态)

---

## 1. sblk 写入中断与 Crash Recovery

**覆盖约束**：Part 4c §3 三阶段写入, Part 4d §4.1 崩溃场景矩阵

### 1.1 三阶段崩溃点

sblk 写入分 3 个阶段：Phase 1（写 Inactive Slot）→ Phase 2（更新 Header: WriteSequence+1, PendingOp 设置）→ Phase 3（切换 ActiveSlot, 清除 PendingOp）。以下测试在每个阶段注入中断。

| 用例 ID | 崩溃点 | PendingOp | 恢复后 readStatus | 数据状态 |
|---------|--------|-----------|-------------------|----------|
| FI-SBLK-01 | Phase 1 中断（写 Slot 中途） | 0（Header 未更新） | OK | 旧 Slot 数据完好，未碰旧 Header |
| FI-SBLK-02 | Phase 1 完成、Phase 2 前中断 | 0 | OK | Inactive Slot 有新数据但 Header 未指向它 |
| FI-SBLK-03 | Phase 2 完成、Phase 3 前中断（CONFIG_UPDATE） | 1 | NEED_REPAIR | Inactive Slot 有新数据，Header PendingOp=1 未清除 |
| FI-SBLK-04 | Phase 2 完成、Phase 3 前中断（STORAGE_CHANGE） | 2 | NEED_REPAIR 或 DEGRADED | 取决于 Slot 数据完整性 |
| FI-SBLK-05 | Phase 3 部分写入（Header 4KB 写未完成） | 不确定 | DEGRADED | ControlChecksum 失败 → 降级使用另一 Slot |

### 1.2 恢复操作

| 用例 ID | 场景 | 注入方式 | 步骤 | 期望 |
|---------|------|----------|------|------|
| FI-SBLK-10 | PendingOp=1 恢复 | 手动构造 Header(PendingOp=1, ActiveSlot=0) | readMetadata → 触发 repair | repair 完成 Phase 3 → ActiveSlot 切换 → PendingOp=0 → readStatus=OK 或 RECOVERED |
| FI-SBLK-11 | PendingOp=2 恢复 | 手动构造 Header(PendingOp=2) | readMetadata → 触发 repair | repair 尝试 Phase 3 + read-back 校验 → 成功则 OK，否则 DEGRADED |
| FI-SBLK-12 | 双 Slot 均损坏 | 篡改两个 Slot 的 checksum | readMetadata | readStatus=CORRUPTED |
| FI-SBLK-13 | Active Slot 损坏、Inactive 完好 | 篡改 Active Slot checksum | readMetadata | 降级使用 Inactive Slot → readStatus=DEGRADED |

### 1.3 LV Extend 期间崩溃

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-SBLK-20 | lvextend 成功、Phase 1 前中断 | 扩容后未写入新数据 | 下次写入基于新布局，旧数据仍通过旧布局可读 |
| FI-SBLK-21 | lvextend 失败（空间不足） | mock lvextend 失败 | 写入返回错误，Header 保持旧布局不变 |
| FI-SBLK-22 | 扩容后读取尝试双布局 | 扩容后 Header 更新前中断 | readMetadata 先尝试 old-layout → 失败 → retry new-layout |

---

## 2. MN 重启恢复

### 2.1 Registering 状态清理

**覆盖约束**：Part 3 §4

| 用例 ID | 场景 | 初始状态 | MN 重启后行为 | 期望 |
|---------|------|----------|--------------|------|
| FI-MN-01 | 本 MN 的 Registering VM | VmInstanceVO(state=Registering, registeringMnUuid=本MN) | managementNodeReady 回滚 | 所有关联 VO 删除（由外到内），VmInstanceVO 删除 |
| FI-MN-02 | 其他 MN（已离线）的 Registering VM | registeringMnUuid=MN-B且MN-B不在线 | managementNodeReady 回滚 | 同上 |
| FI-MN-03 | 其他 MN（仍在线）的 Registering VM | registeringMnUuid=MN-B且MN-B在线 | 跳过 | 不回滚（MN-B 仍在处理） |
| FI-MN-04 | 注册 Step 3 后崩溃（VmInstanceVO + VolumeVO 已创建，快照未创建） | DB 含部分 VO | 回滚 | VolumeVO + VmInstanceVO 删除，无快照残留 |
| FI-MN-05 | 注册 Step 5 后崩溃（所有 VO 已创建，变基未执行） | DB 含全部 VO | 回滚 | 所有 VO 按序删除 |

### 2.2 Poller 暂停行恢复

**覆盖约束**：C-01C-8

| 用例 ID | 场景 | 初始状态 | MN 重启后行为 | 期望 |
|---------|------|----------|--------------|------|
| FI-MN-10 | 迁移暂停行存在 | dirty 行 nextRetryTime='2099-12-31T00:00:00' | managementNodeReady 重置 | nextRetryTime=NULL，Poller 恢复处理 |
| FI-MN-11 | 正常退避行不受影响 | dirty 行 nextRetryTime=明天 | managementNodeReady | 不修改（仅匹配 2099 魔数值） |

### 2.3 lastFlushFailed 恢复链

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-MN-20 | MN 重启后 StaleRecoveryTask 启动 | lastFlushFailed=true 行存在 | StaleRecoveryTask 扫描 → markDirty(retryCount=0) → Poller 重新 flush |
| FI-MN-21 | staleRecoveryCount 达上限 | staleRecoveryCount=10 | StaleRecoveryTask 不再重入队 → WARN 日志 → 等待手动 APIUpdateVmMetadataMsg |

### 2.4 升级全量刷新

**覆盖约束**：Part 2b §9.1

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-MN-30 | 升级刷新中途 MN 崩溃 | 处理到第 500 个 VM 时崩溃 | 重启后 lastRefreshVersion 仍为旧值 → 重新触发全量刷新（Δ-8 保障） |
| FI-MN-31 | 滚动升级 recent-nodeLeft 防护 | MN-A(v2) 启动，15 分钟内有 MN-B(v1) nodeLeft | 延迟 10 分钟重新检查，不立即执行全量刷新 |

---

## 3. 双 MN 故障转移

**覆盖约束**：Part 2b §7, C-02B-1, C-02B-2

### 3.1 MN 宕机接管

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-HA-01 | MN-A 认领 dirty 行后宕机 | MN-A claim dirty(vm-1) → MN-A 下线 | FK SET NULL → MN-B nodeLeft 延迟 5s → claimAndFlush → vm-1 flush 成功 |
| FI-HA-02 | 接管延迟验证 | 同上 | 总接管时间 ≈ 心跳超时(~30s) + 5s ≈ 35s |
| FI-HA-03 | Fence Check 拦截 zombie 写入 | MN-A GC pause 恢复后尝试写入 | dirty 行 managementNodeUuid 已不是 MN-A → abort（C-02B-2） |

### 3.2 脑裂防护

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-HA-10 | 两个 MN 同时 CAS 认领同一行 | 并发 UPDATE WHERE uuid=x AND managementNodeUuid IS NULL | 只有一个 affected_rows=1，另一个=0 |
| FI-HA-11 | GC pause 期间对端接管后并发写入 | MN-A pause → MN-B 接管写入 → MN-A 恢复写入 | sblk WriteSequence 保证最终一致（更高 SeqNum 胜出） |

### 3.3 nodeLeft 延迟配置

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-HA-20 | nodeLeft.delaySec=0 | 配置延迟为 0 | 立即接管（增大竞态风险，仅验证可配置性） |
| FI-HA-21 | nodeLeft.delaySec=10 | 配置延迟为 10s | 10s 后接管 |

---

## 4. DB 异常

### 4.1 markDirty 并发竞态

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-DB-01 | INSERT IGNORE 竞态（两个 MN 同时 INSERT 同一 VM） | 并发 markDirty | 一个 INSERT 成功，一个 IGNORE → 两者 UPDATE 均安全（C-DM-01） |
| FI-DB-02 | INSERT=0 且 UPDATE=0 | 极端竞态：INSERT IGNORE 后 UPDATE 前行被删除 | 重新 INSERT IGNORE（C-DM-01 保障） |

### 4.2 FK CASCADE 验证

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-DB-10 | VM 物理删除级联清理 dirty 行 | DELETE VmInstanceEO | VmMetadataDirtyVO 行自动删除 |
| FI-DB-11 | VM 物理删除级联清理 fingerprint | DELETE VmInstanceEO | VmMetadataPathFingerprintVO 行自动删除 |
| FI-DB-12 | MN 离线级联释放认领 | DELETE ManagementNodeVO | dirty 行 managementNodeUuid=NULL（FK SET_NULL） |

---

## 5. Agent 异常

### 5.1 Agent 不可达

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-AGT-01 | 写入时 Agent 超时 | mock Agent 不响应 | UpdateVmInstanceMetadataOnHypervisorMsg 超时（2min） → onFlushFailure |
| FI-AGT-02 | Agent 返回未知错误码 | mock Agent 返回 500 | onFlushFailure → 进入退避 |
| FI-AGT-03 | 扫描时 Agent 超时 | APIScanVmInstanceMetadataMsg + mock Agent 不响应 | API 超时返回错误 |

### 5.2 Agent 部分成功

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-AGT-10 | 写入 Agent 成功但 MN 在收到响应前崩溃 | mock：Agent 写入完成 → MN 崩溃 | 存储有新数据但 dirty 行未删除 → MN 重启后 Poller 重新 flush（幂等覆盖写） |
| FI-AGT-11 | 读取 Agent 返回损坏数据 | mock Agent 返回非 JSON | readMetadata 报错，readStatus=CORRUPTED |

### 5.3 PS 不可达

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-AGT-20 | PS 卸载期间 flush | PS Detached | flush 失败 → 退避 → stale → 最终熔断（staleRecoveryCount >= maxCycles） |
| FI-AGT-21 | PS 重新挂载后恢复 | PS Reattach + API 触发 | markDirty → Poller flush 成功 |

---

## 6. 功能开关切换竞态

**覆盖约束**：Part 2b §9a, C-02B-11 ~ C-02B-13

### 6.1 快速 toggle

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-TOG-01 | false→true→false 快速切换 | 启用后立即禁用 | 初始化任务检测到 enabled=false → 中止（C-02B-13） |
| FI-TOG-02 | false→true→false→true | 两次启用 | 第二次初始化：LEFT JOIN 排除已有 dirty 行 → 仅初始化新 VM |

### 6.2 true→false 清理

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| FI-TOG-10 | 禁用时清理 PathFingerprint | true→false | 异步批量删除所有 VmMetadataPathFingerprintVO（Δ-10） |
| FI-TOG-11 | 禁用时 dirty 行保留 | true→false | VmMetadataDirtyVO 行不删除 |
| FI-TOG-12 | 禁用时存储元数据保留 | true→false | sblk LV / JSON 文件不删除 |

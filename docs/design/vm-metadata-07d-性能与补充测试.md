# VM 元数据 — 性能与补充测试计划

> 性能基准测试和未归入前三类的补充测试场景。
> 单元测试见 [Part 7a](vm-metadata-07a-单元测试计划.md)，集成测试见 [Part 7b](vm-metadata-07b-集成测试计划.md)，故障注入见 [Part 7c](vm-metadata-07c-故障注入测试.md)。

## 目录

1. [性能基准：全量元数据更新](#1-性能基准全量元数据更新)
2. [性能基准：升级批次压力](#2-性能基准升级批次压力)
3. [性能基准：注册流程](#3-性能基准注册流程)
4. [性能基准：Poller 吞吐](#4-性能基准poller-吞吐)
5. [性能基准：sblk 读写延迟](#5-性能基准sblk-读写延迟)
6. [补充：E2E 场景测试](#6-补充e2e-场景测试)
7. [补充：数据迁移与兼容性](#7-补充数据迁移与兼容性)
8. [补充：安全与权限](#8-补充安全与权限)
9. [补充：可观测性验证](#9-补充可观测性验证)
10. [补充：GlobalConfig 动态生效](#10-补充globalconfig-动态生效)

---

## 1. 性能基准：全量元数据更新

### 1.1 1000 VM 全量 markDirty

| 用例 ID | 场景 | 配置 | 度量指标 | 基准（P95） |
|---------|------|------|----------|------------|
| PERF-01 | 1000 VM 批量 markDirty 耗时 | 单 MN，MySQL | markDirty 全部完成时间 | < 10s |
| PERF-02 | 1000 VM 批量 markDirty（Galera 双节点） | 双 MN | 同上 + 无死锁 | < 15s |
| PERF-03 | 1000 VM markDirty 后 Poller 全部消化 | pollInterval=5s, maxConcurrent=10, ps.maxConcurrent=5 | 从首个 markDirty 到最后一个 dirty 行删除 | < 15 分钟 |

### 1.2 10000 VM 大规模验证

| 用例 ID | 场景 | 配置 | 度量指标 | 基准（P95） |
|---------|------|------|----------|------------|
| PERF-04 | 10000 VM markDirty | 双 MN | 插入完成时间 | < 60s |
| PERF-05 | 10000 VM Poller 消化 | 双 MN, maxConcurrent=10 | 全部 flush 完成时间 | < 2.5 小时 |

### 1.3 单 VM flush 延迟分布

| 用例 ID | 场景 | 度量指标 | 基准 |
|---------|------|----------|------|
| PERF-06 | 普通 VM（1 根盘、少量快照）flush 延迟 | buildMetadata + Agent 写入总耗时 | P50 < 500ms, P99 < 3s |
| PERF-07 | 大 VM（24 盘、256 快照）flush 延迟 | 同上 | P50 < 5s, P99 < 15s |

---

## 2. 性能基准：升级批次压力

**覆盖约束**：Part 2b §9.2, C-02B-4

### 2.1 升级全量刷新

| 用例 ID | 场景 | 配置 | 度量指标 | 基准（P95） |
|---------|------|------|----------|------------|
| PERF-10 | 1000 VM 升级全量 markDirty | batchSize=1000 | INSERT IGNORE + UPDATE 总耗时 | < 5s |
| PERF-11 | 10000 VM 升级全量 markDirty | batchSize=1000 | 10 批总耗时 | < 30s |
| PERF-12 | 升级全量 markDirty 期间业务 API 影响 | PERF-11 同时运行 100 个 API | API 响应延迟增幅 | < 20% |

### 2.2 false→true 初始化

| 用例 ID | 场景 | 配置 | 度量指标 | 基准 |
|---------|------|------|----------|------|
| PERF-15 | 5000 VM 初始化 | initBatchSize=200, batchDelay=5s | 初始化完成时间 | ≈ 25 批 × 5s = ~125s |
| PERF-16 | 初始化期间 Poller 吞吐 | 同上 | dirty 行积压量峰值 | < 500 行（证明批间延迟有效） |

---

## 3. 性能基准：注册流程

### 3.1 注册耗时

| 用例 ID | 场景 | 输入规模 | 度量指标 | 基准（P95） |
|---------|------|----------|----------|------------|
| PERF-20 | 最小 VM 注册 | 1 根盘、0 快照 | 注册总耗时（Step 1-7） | < 3s |
| PERF-21 | 中等 VM 注册 | 4 盘、50 快照 | 同上 | < 10s |
| PERF-22 | 极端 VM 注册 | 24 盘、256 快照 + Group + Ref | 同上 | < 60s |
| PERF-23 | 极端 VM UUID 冲突检测 | ~7000 UUID（分批 1000/批） | 冲突检测耗时 | < 2s |

### 3.2 注册回滚耗时

| 用例 ID | 场景 | 输入规模 | 度量指标 | 基准 |
|---------|------|----------|----------|------|
| PERF-25 | 大 VM 回滚 | 24 盘、256 快照全部已创建 | 由外到内删除总耗时 | < 30s |

---

## 4. 性能基准：Poller 吞吐

### 4.1 Poller 轮询效率

| 用例 ID | 场景 | 度量指标 | 基准 |
|---------|------|----------|------|
| PERF-30 | 空 Poller 周期（0 dirty 行） | SELECT 查询耗时 | < 1ms |
| PERF-31 | 满载 Poller 周期（50 行认领） | claim + submit 总耗时 | < 100ms |
| PERF-32 | 大量退避行跳过 | 500 dirty 行中 450 行有 nextRetryTime > now | WHERE 过滤效率 | < 5ms |

### 4.2 路径指纹巡检效率

| 用例 ID | 场景 | 度量指标 | 基准 |
|---------|------|----------|------|
| PERF-35 | 1000 VM 巡检（无 drift） | 全量巡检耗时 | < 5s |
| PERF-36 | 5000 VM 巡检 keyset 分页 | 分页查询 + 比对总耗时 | < 20s |
| PERF-37 | 巡检期间零存储 I/O 验证 | Agent 调用计数 | 0 次 Agent 调用 |

---

## 5. 性能基准：sblk 读写延迟

### 5.1 写入延迟

| 用例 ID | 场景 | Payload 大小 | 度量指标 | 基准 |
|---------|------|-------------|----------|------|
| PERF-40 | 小 payload 写入 | 10KB | Agent pwrite 耗时 | < 10ms |
| PERF-41 | 中等 payload 写入 | 500KB | 同上 | < 50ms |
| PERF-42 | 大 payload 写入 | 5MB | 同上（含 lvextend） | < 500ms |

### 5.2 读取延迟

| 用例 ID | 场景 | 度量指标 | 基准 |
|---------|------|----------|------|
| PERF-45 | 正常读取（PendingOp=0） | pread + 解析耗时 | < 20ms |
| PERF-46 | 带 repair 的读取（PendingOp=1） | repair + pread 总耗时 | < 100ms |

### 5.3 扫描效率

| 用例 ID | 场景 | 度量指标 | 基准 |
|---------|------|----------|------|
| PERF-50 | 100 LV 扫描（仅读 Header 摘要区） | scanMetadataVmUuids 总耗时 | < 2s |
| PERF-51 | 1000 LV 扫描 | 同上 | < 15s |

---

## 6. 补充：E2E 场景测试

### 6.1 完整生命周期

| 用例 ID | 场景 | 步骤 | 验证点 |
|---------|------|------|--------|
| E2E-01 | VM 创建→运行→改名→加盘→快照→迁移→销毁 | 全流程 | 每步后元数据正确反映 DB 状态 |
| E2E-02 | VM 创建→销毁→恢复→再销毁→Expunge | Destroy→Recover→Destroy→Expunge | Recover 后元数据恢复更新；Expunge 时 deleteMetadata |
| E2E-03 | 链式克隆子 VM 注册 | 从存储扫描→读取→注册子 VM | ReferenceVO(parentId=null) + TreeVO 幂等 |
| E2E-04 | VM 注册→首次启动→markDirty 触发 | 注册完成→启动 VM→Running | `registered.not.started` Config 删除 → markDirty → Poller flush |

### 6.2 多 MN 协同场景

| 用例 ID | 场景 | 步骤 | 验证点 |
|---------|------|------|--------|
| E2E-10 | 双 MN 分摊 dirty 行处理 | 20 个 VM markDirty + 双 MN Poller | 所有 VM 最终 flush 成功，无遗漏 |
| E2E-11 | MN-A flush 中 → MN-A 宕机 → MN-B 接管 | in-flight flush 场景 | MN-B 接管并成功 flush |

### 6.3 存储迁移 + 元数据联动

| 用例 ID | 场景 | 步骤 | 验证点 |
|---------|------|------|--------|
| E2E-20 | 根盘迁移 sblk→sblk | 迁移成功后读取目标 PS 元数据 | 元数据内容完整 + 源 PS 已清理 |
| E2E-21 | 根盘迁移失败回滚 | Step 5 写入失败 | 源 PS 元数据不变 + 目标 PS 残留清理 + Poller 恢复 |
| E2E-22 | 仅数据盘迁移 | 数据盘从 PS-A → PS-B（根盘不动） | 元数据更新到根盘所在 PS，storageStructureChange=true |

---

## 7. 补充：数据迁移与兼容性

### 7.1 版本兼容

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| COMPAT-01 | 旧版 ZStack 写入的 sblk → 新版读取 | 用旧格式写入 → 新版 readMetadata | 正常读取（schemaVersion 向后兼容） |
| COMPAT-02 | schemaVersion 低于当前的元数据注册 | forceVersionMismatch=true | 注册成功 + warnings 列出差异字段 |
| COMPAT-03 | schemaVersion 高于当前的元数据注册 | 来自更新版本的 JSON | 默认拒绝；forceVersionMismatch=true 时允许 |

### 7.2 DB 升级

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| COMPAT-10 | 首次部署（新建表） | 全新安装 | VmMetadataDirtyVO + VmMetadataPathFingerprintVO 表创建成功 |
| COMPAT-11 | 升级部署（ALTER TABLE） | 从无元数据版本升级 | 新表正确创建；GlobalConfig 默认值生效 |

---

## 8. 补充：安全与权限

### 8.1 API 权限

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| SEC-01 | 普通用户调用 APIRegisterVmInstanceFromMetadataMsg | 非 admin 账户 | 权限拒绝 |
| SEC-02 | 普通用户查询 Registering VM | QueryVmInstance | Registering VM 不可见 |
| SEC-03 | admin 查询 Registering VM | QueryVmInstance | Registering VM 可见 |
| SEC-04 | 普通用户调用 APIScanVmInstanceMetadataMsg | 非 admin 账户 | 权限拒绝 |

### 8.2 注册安全

| 用例 ID | 场景 | 步骤 | 期望 |
|---------|------|------|------|
| SEC-10 | 恶意 JSON 注入 | metadataContent 含 SQL 注入 payload | ORM 参数化查询隔离，不影响 DB |
| SEC-11 | 超大 JSON（> 100MB） | metadataContent 超大 | API 层大小限制拦截 |
| SEC-12 | installPath 路径遍历 | installPath 含 `../../etc/passwd` | 前缀锚定替换 + 正则预校验拦截（C-03-3） |

---

## 9. 补充：可观测性验证

**覆盖约束**：Part 2b §14

### 9.1 Prometheus 指标

| 用例 ID | 指标 | 场景 | 期望 |
|---------|------|------|------|
| OBS-01 | `vm_metadata_flush_total{status=success}` | 正常 flush | Counter 递增 |
| OBS-02 | `vm_metadata_flush_total{status=fail}` | Agent 失败 | Counter 递增 |
| OBS-03 | `vm_metadata_flush_duration_seconds` | 正常 flush | Histogram 记录耗时 |
| OBS-04 | `vm_metadata_dirty_queue_size` | markDirty 后 | Gauge > 0 |
| OBS-05 | `vm_metadata_registration_total{status=success}` | 注册成功 | Counter 递增 |
| OBS-06 | `vm_metadata_registration_total{status=rollback}` | 注册回滚 | Counter 递增 |

### 9.2 日志验证

| 用例 ID | 场景 | 期望日志 |
|---------|------|----------|
| OBS-10 | flush 失败且重试耗尽 | ERROR 日志含 vmUuid + 失败原因 + retryCount |
| OBS-11 | Fence Check 拦截 | WARN `"Lost claim on vm {uuid}, abort flush write"` |
| OBS-12 | 路径漂移检测 | WARN `"path drift detected for VM [{uuid}]"` + 新旧 snapshot 对比 |
| OBS-13 | 孤儿元数据检测 | WARN `"orphan metadata detected: ps={}, vm={}, reason={}"` |
| OBS-14 | stale recovery 熔断 | WARN `"VM [{}] metadata stale recovery exceeded {} cycles, entering permanent-stale"` |

---

## 10. 补充：GlobalConfig 动态生效

**覆盖约束**：C-RB-04, C-M4, 各 §13 配置项

| 用例 ID | 配置项 | 变更方式 | 期望 |
|---------|--------|----------|------|
| CFG-01 | `vm.metadata.dirty.pollIntervalSec` | 5→10 | 下轮 Poller 间隔变为 10s |
| CFG-02 | `vm.metadata.maxRetry` | 5→3 | 3 次失败后即标记 stale |
| CFG-03 | `vm.metadata.global.maxConcurrent` | 10→5 | AtomicInteger 上限立即生效 |
| CFG-04 | `vm.metadata.retry.baseDelaySeconds` | 10→20 | 退避间隔加倍 |
| CFG-05 | `vm.metadata.nodeLeft.delaySec` | 5→10 | nodeLeft 事件后延迟 10s 再接管 |
| CFG-06 | `vm.metadata.enabled` | true→false | Poller 停止处理 + PathFingerprint 异步清理 |
| CFG-07 | `vm.metadata.enabled` | false→true | 分批初始化启动 |
| CFG-08 | `vm.metadata.pendingApi.timeoutMinutes` | 45→30 | pendingApis 超时缩短 |
| CFG-09 | `vm.metadata.pathCheck.intervalSec` | 300→60 | 巡检频率加快 |
| CFG-10 | `vm.metadata.staleRecovery.maxCycles` | 10→3 | 熔断更快触发 |

---

## 附录：测试用例 ID 编号规则

| 前缀 | 类别 | 文档 |
|------|------|------|
| UT-* | 单元测试 | Part 7a |
| IT-* | 集成测试 | Part 7b |
| FI-* | 故障注入测试 | Part 7c |
| PERF-* | 性能基准测试 | Part 7d |
| E2E-* | 端到端场景测试 | Part 7d §6 |
| COMPAT-* | 兼容性测试 | Part 7d §7 |
| SEC-* | 安全与权限测试 | Part 7d §8 |
| OBS-* | 可观测性验证 | Part 7d §9 |
| CFG-* | 配置动态生效 | Part 7d §10 |

**总计**：约 **190+ 条测试用例**，覆盖序列化、存储协议、并发控制、故障恢复、性能基准、安全权限和可观测性全维度。

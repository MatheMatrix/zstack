# VM 元数据 — 设计约束索引

> 本文档汇总所有 vm-metadata 设计文档中的约束条目（C-*），提供跨文档查找的统一入口。
> 每条约束标注 ID、简要描述和来源文档/章节。

## Part 1b — 拦截器（vm-metadata-01b-API拦截与VM解析.md）

| ID | 约束 | 来源 |
|----|------|------|
| C-IC | `INTERNAL_METADATA_MESSAGES` 与 handler `markDirty()` 调用点一一可追溯 | §5 |
| C-IM | 所有 `APIMessage` 子类必须标注 `@MetadataImpact`（可为 NONE），CI 扫描全量子类 | §5 |
| C-PA | `pendingApis` 超时清理 + afterCompletion null-safe + 清理时补 markDirty | §5 |
| C-RS | Resolver 选择匹配 API 资源语义；删除/卸载类 API 使用 pre-capture | §5 |
| C-H1 | STORAGE 级内部消息 handler 必须调用 `markDirty()`；CI ERROR 阻断 | §5 |
| C-M4 | `pendingApis` 超时通过 GlobalConfig 配置，不得硬编码 | §5 |

## Part 1c — 存储层（vm-metadata-01c-存储层与模板虚拟机.md）

| ID | 约束 | 来源 |
|----|------|------|
| C-01C-2 | sblk LV 名称 `{vm_uuid}_vmmeta`，长度 39，< LVM 128 上限 | §4 |
| C-01C-3 | 模板 VM 元数据锚定 RootVolume 所在 PS | §4 |
| C-01C-4 | 存储迁移：目标端同步写入 + read-back 校验后才能清理源端 | §4 |
| C-01C-5 | 清理时校验根盘 `primaryStorageUuid` 仍在源 PS | §4 |
| C-01C-6 | flush 路径动态解析，不缓存历史路径 | §4 |
| C-01C-7 | 迁移 `nextRetryTime` 暂停/恢复成对；失败回滚恢复 Poller | §4 |
| C-01C-8 | MN 启动时重置 `nextRetryTime='2099-...'` 的暂停行（Poller 启动前） | §4 |
| C-01C-9 | `deleteMetadata` 幂等（不存在不抛异常） | §4 |
| C-01C-10 | local/NFS tmp 文件固定命名，Agent 启动时清理残留 | §4 |
| C-01C-11 | `MetadataStorageHandler` 包含 `scanMetadataVmUuids()`（Q15） | §4 |
| C-01C-12 | `deleteMetadata` 重试参数通过 GlobalConfig 配置（Q12） | §4 |

## Part 2 — Dirty Mark & Poller（vm-metadata-02-脏标记与Poller.md）

| ID | 约束 | 来源 |
|----|------|------|
| C-DM-01 | `markDirty` 使用 `INSERT IGNORE + UPDATE` 两步，禁止 `ON DUPLICATE KEY`（Q19） | §7 |
| C-CL-02 | claim 成功必须写入 `lastClaimTime`；僵尸清理 15 分钟（独立任务 DP-05） | §7 |
| C-TM-03 | `doFlush` 超时 ≥ 5 分钟，超时进入 `onFlushFailure` | §7 |
| C-RB-04 | 指数退避参数来自 GlobalConfig（baseDelay/maxExponent） | §7 |
| C-SR-05 | 重试耗尽必须标记 `lastFlushFailed=true`，不得静默放弃 | §7 |
| C-SR-06 | StaleRecoveryTask 的 markDirty 使用 retryCount=0，验证返回值后才清除 lastFlushFailed（DP-03） | §7 |
| C-SC-07 | `storageStructureChange` 仅在存储拓扑操作时设置；升级场景始终 true | §7 |
| C-FL-08 | `doFlush` 过滤 Destroyed VM dirty 行，主动删除释放 Poller（Q34） | §7 |
| C-TF-09 | `triggerFlushForVm` stale claim 接管阈值通过 `vm.metadata.triggerFlush.staleMinutes` 配置（默认 10 min），不得与 `staleClaim.thresholdMinutes`（30 min 后台扫描）混淆 | §7, DP-06 |

## Part 2b — HA & 运维（vm-metadata-02b-高可用与运维.md）

| ID | 约束 | 来源 |
|----|------|------|
| C-02B-1 | `nodeLeft()` 延迟 5s 后触发 `claimAndFlush()`，不立即抢占 | §15 |
| C-02B-2 | sblk 写入前校验 `managementNodeUuid == 本 MN`（Fence Check） | §15 |
| C-02B-3 | 路径巡检禁止 `listAll`，必须 keyset 分页 | §15 |
| C-02B-4 | 升级刷新分批执行（默认 1000），避免单次超大事务 | §15 |
| C-02B-5 | payload 上限：静态 30MB + 运行时 slot 容量校验 | §15 |
| C-02B-6 | `storageStructureChange` OR 语义，dirty 行删除前不降级 | §15 |
| C-02B-7 | 容量常量集中定义，禁止硬编码散落 | §15 |
| C-02B-8 | `lastFlushFailed` 仅 retry 耗尽时 true，仅 StaleRecoveryTask 重置 false | §15 |
| C-02B-9 | 升级刷新前检查 15 分钟内无 nodeLeft（M3） | §15 |
| C-02B-10 | nodeLeft 延迟调整需与 Fence Check 配合评估 | §15 |
| C-02B-11 | `false→true` 初始化分批 + 批间延迟 | §15 |
| C-02B-12 | Cleanup API 仅 enabled=false 时允许 | §15 |
| C-02B-13 | 初始化每批重检 enabled 开关 | §15 |
| C-02B-14 | 孤儿检测仅报告不自动删除 | §15 |

## Part 3 — 注册（vm-metadata-03-注册与运维.md）

| ID | 约束 | 来源 |
|----|------|------|
| C-03-1 | `parentId` 注册时统一置 null | §9 |
| C-03-2 | 跨存储拒绝注册，返回 expected/actual PS UUID | §9 |
| C-03-3 | installPath 前缀替换满足分隔符边界 | §9 |
| C-03-4 | 回滚"由外到内"+ 空树清理 SQL | §9 |
| C-03-5 | ChainTask 超时 35 分钟；LongJob cancel 触发 rollback | §9 |
| C-03-6 | Root Volume path 缺失 BLOCK；Data Volume WARN | §9 |
| C-03-7 | 注册成功后触发 ConsistencyCheck | §9 |
| C-03-8 | PreCheck 与 Register 共享校验方法 | §9 |

---

**总计**：6（Part 1b）+ 11（Part 1c）+ 8（Part 2）+ 14（Part 2b）+ 8（Part 3）= **47 条约束**

---

## Part 7 — 测试计划

测试计划分为 4 个文档，约 190+ 条用例，按约束 ID 交叉引用：

| 文档 | 范围 | 用例前缀 |
|------|------|----------|
| [Part 7a — 单元测试](vm-metadata-07a-单元测试计划.md) | 序列化 Round-Trip、DTO 构建、路径指纹、markDirty 逻辑、注解覆盖率、Resolver 链、容量计算、sblk 编解码、注册字段映射、installPath 替换 | UT-* |
| [Part 7b — 集成测试](vm-metadata-07b-集成测试计划.md) | sblk 写入读取、local/NFS JSON 读写、Poller 端到端、API 拦截器联动、存储迁移链路、注册端到端、路径巡检、API 端到端 | IT-* |
| [Part 7c — 故障注入](vm-metadata-07c-故障注入测试.md) | sblk 三阶段崩溃恢复、MN 重启清理、双 MN 故障转移、DB 异常、Agent 异常、功能开关竞态 | FI-* |
| [Part 7d — 性能与补充](vm-metadata-07d-性能与补充测试.md) | 1000/10000 VM 全量更新基准、升级批次压力、注册耗时、Poller 吞吐、E2E 场景、兼容性、安全权限、可观测性、GlobalConfig 动态生效 | PERF-*/E2E-*/COMPAT-*/SEC-*/OBS-*/CFG-* |

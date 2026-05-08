# Next Session — v5.5.18 PSC writer collapse landed + IT 3/3 GREEN

> **上轮 (2026-05-09 凌晨)**: PSC writer collapse plan 落地 + 4 commit fix + 3 新 IT case PASS。KVM/Container 容量管道 Layer1+Layer2 模型实装，PSC 真表 `recalculate(serverUuid)` 不再是孤儿 API。
> **当前位置**: PSC plan §6 Execution Phase 4（commit 收敛）✅；剩 Phase 3 真机回归 + STATUS.md 收口未做。

---

## 1. 已完成（本轮 commits）

**zstack 主仓** (origin/feature/unifi-host-dev):
```
65766d8eea <fix>[test]: KvmReportHostCapacityRecalcCase 5 fixes
449a81c858 <chore>[scripts]: add run-case.sh IT runner
```

**premium 子模块** (origin/feature/unifi-host-dev):
```
2e558edb9a <fix>[test]: Container IT cases zoneUuid + Math.max long cast
e607a7afe3 <fix>[allocator]: premium HostAllocatorManager.xml add psCapUpdater bean
```

**前序 PSC writer collapse 7 commit**（plan §3 实装本体）：zstack `4330abb0de`/`4eccc22000`/`5b29caac38` + premium `54d93269e6`/`a570c323bd`/`cccb5aea48`/`6db2738b64` 已 push。

**关键交付**：
- Plan: [docs/plans/2026-05-08-001-psc-writer-collapse-plan.md](../plans/2026-05-08-001-psc-writer-collapse-plan.md)（two-layer model + 5 U-unit + 4 IT case + 风险）
- IT harness: `scripts/run-case.sh`（locks `-Dmaven.repo.local`，pkill leftover surefire，strict outcome parsing）
- IT case 3/3 PASS: KvmReportHostCapacityRecalcCase / ContainerSyncRecalcCase / ContainerCordonReservedCase

---

## 2. 下一步（进入 session 后直接做这件事）

**单元**: PSC plan §6 Phase 3+4  **模块**: real-env regression + STATUS.md sync

**动作 3 件**（按先后）：
1. **真机 172.26.201.160 hot-deploy** PSC writer collapse 7+4 commit。endpoint `ef554bb8255d4ce0b891a1367841b88b` 还在；按 `docs/runbooks/testing-envs.md` 跑 hot-deploy；触发 K8s endpoint resync 让 Layer1 写 PSC（7 NativeHost PSC.totalCpu 期望从 0 → K8s 真值 8/120/16/192/8/192/8 cores）。
2. **真机回归三断言**：
   - `mysql -e "SELECT uuid, totalCpu, totalMemory FROM PhysicalServerCapacityVO"` — 7 Container 节点 totalCpu 不再是 0
   - KVM host PSC.availableCpu < totalCpu（recalculate 跑了减 buffer）
   - 触发 cordon 一个 NativeHost（`ContainerNodeCordonServiceImpl.cordon`）+ 再 sync → 该节点 PSC.availableCpu drop to 0（AC-CM-13 PSC fallback 兑现）
3. **STATUS.md 收口同步** 5 处（plan §5 列）：
   - §4.1 R3 ⚠️→✅；R4 ⚠️→✅
   - §4.2 U10 描述改写
   - §4.3 NB-7 ❌→✅
   - §5 line 190 同步；§5 ✅ 区新增「PSC writer collapse — Layer 1 (KVM/Container sync) + Layer 2 (recalculate sole writer)」

**可并行**: 动作 1+2 跟 3 并行（真机回归长跑可后台 ssh，期间写 STATUS.md）。

---

## 3. Blockers / 未补 gap

| # | 阻塞内容 | 影响 | 待办 / 归属 |
|---|---|---|---|
| 1 | sibling baseline `ContainerRoleProviderIntegrationCase` 在 case body 内 forked VM crash 无 hs_err（4:20 长跑后 VM 不见，K8s SelfSubjectAccessReview probe 连 127.0.0.1:80 fail-soft 已降级 ReadOnly 不该阻塞） | 整个 PremiumTest IT 链 long-running 不稳 | follow-up @container-module-architect 独立排查；不阻 PSC writer collapse ship |
| 2 | `HostCapacityUpdater` POJO 路径标 @Deprecated 但还活着（VM allocator flow 还用） | 下个 phase 才能砍 | 跟 plan §7 Out of scope 一致 |
| 3 | KVM agent 周期 `/host/capacity` timer 还在跑 | 心跳保留无害 | 用户决策保留；下个 phase 评估去掉 |

---

## 4. 本轮学到的 ⚠️（可能影响下轮）

1. **premium XML override 镜像**：`premium/conf/springConfigXml/X.xml` 在 premium classpath 上 override 主仓 `conf/springConfigXml/X.xml`（不 merge，整文件替换）。主仓加 bean 时 premium 那份**必须同步加**，否则 spring init NoSuchBeanDef → ApplicationContext 二次 build → ThreadFacade JMX dup（detached 表症）。`HostAllocatorManager.xml` 这次踩了；任何主仓 XML 改动都要 grep premium/conf 对应文件。
2. **同 worktree m2 + 并发 mvn = libzip SIGBUS**：concurrent mvn install 跟 surefire fork mmap 撞同 jar 触发 `BUS_ADRERR`，hs_err_pid log 显示 libzip 帧。run-case.sh 已加 pkill leftover surefire 防御。
3. **Groovy DSL closure trap**：local 变量名跟 SDK action setter 同名时，`x = y` 优先赋值给 local。统一用 `delegate.x = y`。本轮 `serverUuid` / `zoneUuid` 都踩过。
4. **`Math.max(long, BigDecimal)` ambiguous**：Groovy `/` 返 BigDecimal，让 `Math.max` 在 long/BigDecimal 之间无法选 long-long overload，必须 cast `(long)`。
5. **KVM `cpuOverProvisioningRatio` 默认 10**：PSC.totalCpu = `cpuNum × HOST_CPU_OVER_PROVISIONING_RATIO` (HostAllocatorManagerImpl:193 → cpuRatioMgr.calculateHostCpuByRatio)。case 期望值要乘 ratio。`HostGlobalConfig.HOST_CPU_OVER_PROVISIONING_RATIO.value(Integer.class)` 读真值。
6. **`KvmRoleProvider.getCapacityConsumption` feedback loop**：返 `hcv.getUsedCpu()` = `total - available`；recalculate 写 `available = total - consumed - buffer`，每跑一次 available 收敛 -buffer。case 别用 `==` 断言精确值，用 boundary（`available <= total - cpuBuffer`）。

---

## 5. 不要重议的决策（引用 ADR / plan）

- [docs/plans/2026-05-08-001-psc-writer-collapse-plan.md](../plans/2026-05-08-001-psc-writer-collapse-plan.md) — Two-Layer Capacity Model + 5 U-unit
  - Layer 1：各模块 sync 入口写 PSC.total*（不动）
  - Layer 2：`PhysicalServerCapacityUpdater.recalculate(serverUuid)` 唯一虚拟量入口
  - HCV VIEW KVM 专属语义保留（用户决策；Container 不进 VIEW，cordon ext 改读 PSC）
  - HostCapacityUpdater POJO 路径暂留 @Deprecated（VM allocator 还用）
- [ADR-001/002] PSC POJO 例外（不动）
- [ADR-012] PSC PESSIMISTIC_WRITE serverUuid 单 lock key（NB-30 兑现）

---

## 6. Session 间保留的文件

```
conf/db/upgrade/V5.5.18__schema.sql     ← 唯一 schema 权威（不动，PSC plan 0 schema 改动）
docs/plans/2026-05-08-001-psc-writer-collapse-plan.md   ← PSC writer collapse 完整 plan
scripts/run-case.sh                      ← IT runner harness（CLAUDE.md §13 自动遵守）
docs/runbooks/testing-envs.md            ← 真机 endpoint / 凭据
docs/brainstorms/next-session.md         ← 本文件
docs/STATUS.md                           ← 待 §5 收口同步（动作 3）
```

可删：`/tmp/run-case-*.log`、`/home/mj/zstack-workspace/zstack-unifi-host/hs_err_pid*.log`（早期 SIGBUS dump）。

---

## 7. 下 session 入口建议（直接粘贴给 Claude）

```
继续 v5.5.18 PSC writer collapse Phase 3 收尾。上轮 4 commit + 3 IT case 全 PASS
push 完毕，剩真机回归 + STATUS.md 收口。读 docs/brainstorms/next-session.md
拿完整上下文。

本 session 做第 2 节"下一步"3 个动作：
1. 真机 172.26.201.160 hot-deploy 7+4 commit + endpoint resync
2. 三断言（PSC.total / available / cordon→reserved）
3. STATUS.md §4.1 R3/R4 / §4.2 U10 / §4.3 NB-7 / §5 line 190 + ✅ 区新增

铁律见 CLAUDE.md，IT 环境跑 case 强制走 ./scripts/run-case.sh（CLAUDE.md §13 + 第 4 节
踩点 1-6 已在 harness 里防御）。premium XML 改动配套 grep premium/conf/ 同步。
```

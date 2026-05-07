---
title: Wave 1 二阶段 — 未通过的集成测试 case
date: 2026-04-27
status: open
related_commits:
  - parent 728d1fafda  U1a (squashed)
  - parent d8f577d773  U4
  - parent 8da90b2f92  U2+U3
  - premium fc619bf5e4 U1b
  - premium 273f24f23e U5+U6
  - premium 70de8d58af U1c
  - premium 64c28f9ab8 U2 BM2 power dispatch
---

# Wave 1 二阶段集成测试 — 未通过的 case

Wave 1 二阶段（U1c / U2+U3）production code 已落地 + push。集成测试中 U2+U3 已通过；**U1c 集成测试仍未通过**，原因在 case 自身（不是 production 代码 bug）。

## 通过状态

| Unit | Production code | 集成测试 | 备注 |
|---|---|---|---|
| U1a | ✅ commit `728d1fafda` (squashed 4→1) | ✅ AddKvmHostPath2Case PASS (Wave 1 一阶段已绿) | |
| U1b | ✅ premium `fc619bf5e4` | ✅ AddBm2ChassisPath2Case PASS | |
| U4 | ✅ commit `d8f577d773` | ✅ PhysicalServerCapacityUpdaterTest 6/6 unit PASS | |
| U5+U6 | ✅ premium `273f24f23e` | ✅ PodAggregationCase + ContainerRoleProvider 扩 PASS (Wave 1 一阶段已绿) | |
| U2+U3 | ✅ parent `8da90b2f92` + premium `64c28f9ab8` | ✅ PowerAndDiscoverPhysicalServerCase **PASS** (146.7s) — 本轮修复 4 个 case 自身 bug 后绿 | 见下文修法 |
| U1c | ✅ premium `70de8d58af` | ❌ ProcessNodeTransactionalCase **未过** | 详见下文 |

## U2+U3 修复历程（已绿，记录 case 自身的坑）

PowerAndDiscoverPhysicalServerCase.groovy 写出来时 agent 犯的 4 处 bug，全部已修+commit：

1. **`def dbf` 未定义** —— 测试 spec 里 `dbf` 不自动注入（CLAUDE.md §0 4个常见坑#4）。修法：
   ```groovy
   DatabaseFacade dbf = bean(DatabaseFacade.class)
   ```
2. **`evt.inventory.X` 错误访问** —— SDK Groovy DSL 的 `powerOnPhysicalServer { ... }` 直接返回 `PhysicalServerInventory`（不是 Event 包装），没 `.inventory` 子属性。修法：所有 `evt.inventory.X` → `evt.X`（4 处）。
3. **PSC 残留导致 EnvSpec 抛 FATAL System.exit** —— `PhysicalServerCapacityVO` 没 cascade extension（U4 commit `51c6234d3e` 有记），env.delete() 不清。修法：`cleanup(server)` helper 加 `SQL.New(PhysicalServerCapacityVO).eq(uuid).delete()`，并 override `clean()` 加 `SQL.New(PhysicalServerCapacityVO).hardDelete()` 兜底（任何 fail 路径都安全）。

## U1c 当前未过原因（未完全修复）

`premium/test-premium/src/test/groovy/org/zstack/test/integration/container/ProcessNodeTransactionalCase.groovy` 写时 agent 犯了至少 3 处 bug：

| # | bug | 修法 | 状态 |
|---|---|---|---|
| 1 | `vo.url = ...` 访问 ContainerManagementEndpointVO 不存在的 `.url` 字段（实际字段是 managementIp + managementPort） | 改为 `vo.managementIp + vo.managementPort` | ✅ 已修 (uncommitted) |
| 2 | `accessKeyId/Secret` NOT NULL 但 case 没设 → `Column 'accessKeyId' cannot be null` | 加 `vo.accessKeyId/Secret = "test-..."` stub | ✅ 已修 (uncommitted) |
| 3 | clean() 没清 ContainerManagementEndpointVO/NativeClusterVO/NativeHostVO（test 直接 `dbf.persist` 写的不在 env 内）→ EnvSpec FATAL | override `clean()` 加 3 表 hardDelete | ✅ 已修 (uncommitted) |
| 4 | 反射调 `processNodeTransactional` 抛 `java.lang.reflect.InvocationTargetException: null` — 包装层吞了真因（log: 23:40:32）| 看 `/tmp/u1c-fix3.log` 完整 stack；可能：(a) AspectJ 没 weave `@Configurable` 给 `new ContainerEndpointBase(vo)`，导致 `dbf`/`containerUtils` 没注入（test 直接 new 而非 Spring 创建）；(b) `SQLBatch` 程式化事务在 test classpath 没 weave；(c) `containerUtils.toNativeHostVO(node)` NPE。建议：`InvocationTargetException.getCause()` 先打印再判断 | ❌ 未修 |

修复 1+2+3 落到 `ProcessNodeTransactionalCase.groovy` 的 uncommitted 改动里。bug #4 需要 next session 起 mvn 单 case rerun 看 stack。

## 重新跑 U1c 案例的 cmd

env 必须是干净的（mvn clean install 全 reactor 8G heap，sequential，参 `feedback_agent_dispatch_discipline.md`）：

```bash
echo "org.zstack.test.integration.container.ProcessNodeTransactionalCase" > /tmp/u1c-cases.list
MAVEN_OPTS="-Xmx4g" mvn test -pl premium/test-premium -P premium -e \
  -Dmaven.repo.local=$PWD/.m2/repository \
  -Dtest=ContainerTest \
  -DsubCaseCollectionStrategy=Designated \
  -DcaseFilePath=/tmp/u1c-cases.list \
  -DfailIfNoTests=false 2>&1 | tee /tmp/u1c-rerun.log
```

如果再报新 fail，继续 case-self bug 的修法循环（同 U2+U3 模式：修 → 跑 → 修）。

## 旁路修复（已 uncommitted，独立价值）

### iam2 `ResourceStopper.java` Wrap 改 static 内部类

本轮跑测试时撞 `iam2.attribute.project.ResourceStopper.lambda$1` `VerifyError: Bad type on operand stack`：javac/Groovy-Eclipse + AspectJ 给 method-local `class Wrap`（捕 outer this）+ 静态 lambda 的组合生成不一致 bytecode。修法：把 `Wrap` 从 `stopResources()` 方法内提到 class 级 `private static class`（不引用 ResourceStopper 实例字段，能 static）。**这个修法不属 Wave 1 范围，是 env-blocker 的 production fix**，建议单独 commit 收。

### U2+U3 production code 的相关验证

- 4 个 jar 重新 install 后干净：`header` (12.6M, +SPI), `physicalServer` (87.4K, +4 handlers), `baremetal2` (3.9M, +Power msgs), `container` (797.9K, +processNodeTransactional)
- 这些 jar 的内容跟 commit 一致，下次 session 不需重 install（除非 git 切分支）

## next session 入口

1. mvn clean install 整 reactor + 8G heap（sequential，**不要并行**）
2. 跑 U1c case rerun cmd 看 bug #4 stack
3. 修 + 跑直到 PASS
4. iam2 fix 单独 commit `<fix>[iam2]: ResourceStopper Wrap → static (VerifyError)`
5. U1c case 修齐后 commit `<fix>[test]: U1c ProcessNodeTransactional case fixes`

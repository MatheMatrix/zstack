# Next Session — v5.5.18 12a IT refactor closed + 5 phase commits pushed

> **此前 session 收口节点（2026-05-07 晚段）**:
> - parent HEAD: `35433f9cbd <refactor>[test]: 12a 红线 — Cascade 走真 attach API + STATUS 同步`
> - premium HEAD: `150c6eec88 <refactor>[test]: C 档 ProcessNodeTransactional reflection -> IT-API`
> - 两 repo push 到 origin (jin.ma/zstack.git fork) feature/unifi-host-dev
> - 12a 红线（no manual persist + IT only via production API）全 6 spot refactor GREEN，0 production code / DB / Flyway 改动
> - 真机 201.160 endpoint `ef554bb8255d4ce0b891a1367841b88b` 还活着，可继续作回归基线
> - 上一轮 P1 RoleVO gap 仍 CLOSED — 详 §"P1 closed"

## 🎯 下个 session 起手按这个顺序走

1. **创 MR** — fork 已 push，开 MR (gitlab.zstack.io / `/zstack-gitlab-mr` skill)。描述模板见 §"MR description 模板"。重点：5 commit (premium 4 phase + parent 1) + 9 IT case GREEN + 0 production code 改动
2. **CI 验证** — fork push 已 trigger，盯 jenkins.zstack.io 全 reactor 绿
3. **真机 201.160 复测** — endpoint ef554bb8... sync→7 RoleVO 不被本轮 refactor 破坏（zaku 172.20.9.4:80）
4. **12a followups**（不阻 ship，独立 ticket）— 见 §"12a followups"
5. **次要项**（独立 schedule，前轮承接）— 见 §"次要项"

---

## ✅ 12a refactor closed — 6 spot 走真 production API + 0 红线残留（2026-05-07 晚段）

**5 commits（premium 4 phase + parent 1）**:

| commit | 内容 | Change-Id |
|---|---|---|
| premium `4f7219bcb2` | Phase 1: K8sApiMocks helper + iam2Container spec include | `I920b19ded498...` |
| premium `a21d986669` | Phase 2: A 档 testAc2（orchestrator + attach API）+ testAc5（createBareMetal2Instance API + chassis hardwareInfo + 程序化 gateway/image setup）| `I5d4d064679c1...` |
| premium `9a82e2937f` | Phase 3: B 档 testContainerSync + testReadonlyMode（real K8s sync API + K8sApiMocks）| `Ib74de71d13f5...` |
| premium `150c6eec88` | Phase 4: C 档 ProcessNodeTransactional（砍 6 reflection+dbf 测试，加 1 IT-API testStaleNativeHostPrunedKeepsKvmRole）| `I2dfca81efeb5...` |
| parent `35433f9cbd` | Cascade ×3 走 attachPhysicalServerRole(KVM_HOST) API + STATUS L185/L189 同步真机日志 + B 档 dual-track sync | `I1178497eda22...` |

**9 IT case 全 GREEN**:

| Case | 时长 | refactor 类型 |
|---|---|---|
| ContainerRoleProvider testAc2 | 120.9s | orchestrator + attach API |
| Bm2RoleProvider testAc5 | 117.7s | createBareMetal2Instance API + chassis hardwareInfo |
| PhysicalServerRole testContainerSync | 128.7s | 真 K8s sync API + K8sApiMocks |
| PhysicalServerCapacity testReadonlyMode | 121.4s | 同上 |
| ProcessNodeTransactional testStaleNativeHostPruned | 121.8s | IT-API 端到端（替 6 reflection 测试）|
| PhysicalServerCascade ×3 | 62.6s | attachPhysicalServerRole(KVM_HOST) 替 dbf.persist |
| Compat / ServerPool / Path2 (smoke) | ~117s 各 | unchanged，验 spec change 不破坏 |

**关键决策（保留供后人）**:

- **K8sApiMocks 抽轻量版**：从 ContainerLifecycleCase mockK8sApis 抽出 `mockSingleZakuCluster` + `mockK8sNodesWithIps`，去 GPU/HAMI 复杂度。3 个 IT case 复用真 sync API。位置 `premium/test-premium/.../container/K8sApiMocks.groovy`
- **iam2Container.xml 加到 BareMetal2Test.springSpec**：让 BM2 IT context 含 ZakuProvider，可跑真 `syncContainerManagementEndpoint`。Additive，不破坏 BM2 主线 case
- **C 档 testU1c 1-3 砍（不 inline migrate）**：reflection 私有方法 + dbf bootstrap helpers 双违规，IT 框架不该测私有机制（user 红线「测函数走 junit，IT 只能用 API」）。followup migrate JUnit + Mockito，pattern 参考 `ContainerRoleProviderTest.java`
- **Cascade ×3 砍 hardware info/detail 断言**：UT 模式无生产 seed for HardwareInfoVO/HardwareDetailVO。仅保留 PhysicalServerCapacityVO cascade 断言（auto-created by attach）
- **testAc5 chassis Available**：`createBareMetal2IpmiChassisHardwareInfo` 预填 IPMI simulator 硬件信息，等 `BAREMETAL2_CHASSIS_PING_INTERVAL=1s` 自动转 Available
- **Groovy DSL closure 陷阱**：`chassisUuid = chassisUuid` 解析为 delegate property 自赋值，重命名 `bm2ChassisUuid`（playbook §5 同款）

**调试踩过的坑（不重蹈）**:

- LocalStorage 不能挂 BM2 cluster (`ORG_ZSTACK_BAREMETAL2_CLUSTER_10001`) → 必须 sblk/ceph
- BM2 chassis attach 后 status=`HardwareInfoUnknown`，要 `createBareMetal2IpmiChassisHardwareInfo` 触发 Available
- `syncContainerManagementEndpoint` 用 `vendor=zaku` 走 Zaku REST 链 → 必须 mock `/clusters` + `/cluster/{id}` 两条 path（ContainerLifecycleCase L208 示例）
- BareMetal2Test.springSpec 不含 zaku provider → 加 `iam2Container.xml` include
- Cascade test 不动 KVM mock infra（不加 `env.afterSimulator(KVM_HOST_FACT_PATH)` hook），只换 attach API 就够（既有 KVM mock infra 完整）
- Parent + premium dual-tracking：`PhysicalServerCapacityCase` + `PhysicalServerRoleCase` 在 parent gitignore 加 `premium/` 之前已被 parent tracking → 跨 repo 同 file 各 1 commit。结构性，不可避免

**audit 表（按 12a 红线分类）**:

| 档 | case · test | 处理 |
|---|---|---|
| A | ContainerRoleProvider.testAc2DeleteRoleEntityTrueDelete | orchestrator.runStandalone + attachPhysicalServerRole API |
| A | Bm2RoleProvider.testAc5CapacityExclusiveTrue | createBareMetal2Instance API |
| B | PhysicalServerRole.testContainerSyncCreatesRoleVOAutomatically | addContainerManagementEndpoint + sync API + K8sApiMocks |
| B | PhysicalServerCapacity.testReadonlyModeCountsInAvailable | 同上 |
| C | ProcessNodeTransactional ×3 (testU1cStaleHostPruning*, testNativeHostHardDelete*) | 砍掉 + 加 1 个 IT-API testStaleNativeHostPrunedKeepsKvmRole |
| Cascade | PhysicalServerCascade ×3 (testDeleteZone* / testDeleteServerPool*) | attachPhysicalServerRole(KVM_HOST) API + 砍 hardware info/detail 断言 |

---

## 🟡 12a followups（不阻 ship，独立 ticket）

1. **ProcessNodeTransactional JUnit 单测迁移** — 砍掉的 6 reflection 测试（testU1cHappyPath / testU1cUpdate / testU1cEmptyNodeList / testU1cStaleHostPruning* / testNativeHostHardDelete*）应 migrate 到 `premium/plugin-premium/container/src/test/java/org/zstack/container/server/ProcessNodeTransactionalTest.java`，Mockito mock dbf + bean dependencies。pattern 参考同目录 `ContainerRoleProviderTest.java`（JUnit 5 + Mockito + @InjectMocks）
2. **HardwareInfoVO/HardwareDetailVO cascade 断言** — Cascade case 砍 `assertPhysicalServerChildRowsDeleted` 后丢这俩断言。等 hardware discovery API 在 UT 模式可触发（F11 / HardwareDiscoveryScheduler real-machine 路径）后补回
3. **MR + CI 验证** — 已 push，待开 MR + 跑 jenkins
4. **真机 201.160 复测** — endpoint `ef554bb8255d4ce0b891a1367841b88b` sync→7 RoleVO 不被破坏

---

## ✅ P1 closed — Container K8s sync 自动写 PhysicalServerRoleVO + auto-pool（2026-05-07 真机验收）

**验收链路（real env 201.160）**:

```
SyncContainerManagementEndpoint(uuid=ef554bb8...)
  → saveAsNativeClusters(clusters, zoneUuid)
    → if cluster.serverPoolUuid==null:
        createNativeClusterPool("k8s-dev-gpu-pool", zone)
        cluster.setServerPoolUuid(newPoolUuid) + dbf.update
  → afterSyncNodes hook
  → 7× ContainerEndpointBase.syncNodesFromCluster fan-out:
    → pathTwoOrchestrator.runStandalone(nativeHost, matchCtx, cluster.uuid, ...)
      → AutoAssociateFlow（tier 1/2/3 都 miss → auto-create PSV in pool）
      → CreatePhysicalServerRoleFlow → PhysicalServerRoleVO(CONTAINER_HOST)
      → InitPhysicalServerCapacityFlow
      → enqueueDiscoveryHook
QueryPhysicalServerRole roleType=CONTAINER_HOST  → 7 行 ✅
```

**Code 改动**（已 commit + push）:

- 新增 `PhysicalServerPathTwoOrchestrator` (plugin/physicalServer)：抽 KVM/BM2/Container 共用 4-flow chain；KVM/Container 走 SPI `classify(HostVO)`，BM2 chassis 走直传 overload（chassis 是 ResourceVO 不是 HostVO）。
- `PhysicalServerPathTwoContributor`（KVM AddHost）+ `BareMetal2ChassisManagerImpl`（BM2 AddChassis）委托 orchestrator，砍掉重复 4-flow wire-up（~187 行）。
- `ContainerEndpointBase.syncNodesFromCluster` per-NativeHost fan-out `runStandalone`；`saveAsNativeClusters` 自动建 pool（cluster.serverPoolUuid 空时）。
- `ServerPoolVO` 去 `implements OwnedByAccount` + 删 `@Transient accountUuid` 字段——admin-only platform config 不该 own by account；副作用：`dbf.persist(pool)` 不再触发 `AccountResourceRefVO` insert NOT NULL 冲突，K8s sync 自动建 pool 才能跑通。
- IT: 复活 3 SKIPped sub-test（不再 dbf 直插 RoleVO，走真 path-2）+ 加 `testContainerSyncCreatesRoleVOAutomatically`。

**6 critical IT GREEN（~12 min total）**:

| Case | Tests | Time | Module |
|---|---|---|---|
| PhysicalServerRoleCase | 1/0/0/0 | 121.9s | premium/test-premium |
| PhysicalServerCapacityCase | 1/0/0/0 | 114.0s | premium/test-premium |
| ProcessNodeTransactionalCase | 1/0/0/0 | 115.8s | premium/test-premium |
| ContainerRoleProviderIntegrationCase | 1/0/0/0 | 117.6s | premium/test-premium |
| KvmRoleProviderIntegrationCase | 1/0/0/0 | 70.3s | test |
| AddBm2ChassisPath2Case | 1/0/0/0 | 110.5s | premium/test-premium |

加上 dave 之前 4 OSS GREEN（TestPhysicalServerProvisionService 10/0 + TestHardwareDiscoveryScheduler 4/0 + PhysicalServerOpsCase 1/0 + PhysicalServerPowerCase 1/0）+ Full reactor BUILD SUCCESS 8:58 min + Interface invariance ✅（`PhysicalServerPathTwoExtensionPoint` / `PhysicalServerRoleProvider` / `ContainerEndpointSyncExtensionPoint` 零变更）。

**调试踩过的坑（avoid 重蹈）**:
- IT 用 `192.168.x.x` managementIp 走真 HTTP timeout → 必须 `127.0.0.x` 回环（playbook §2 已明）
- Groovy `role.schedulingMode == "EXTERNAL_READONLY"` enum vs String false-positive → `.toString() ==`
- ServerPool 因 `implements OwnedByAccount` 触发 AccountResourceRefVO NOT NULL → 移除接口
- carol IT fixture `dbf.persist(NativeClusterVO)` 直插绕过 sync path → IT 没覆盖 auto-pool 真路径（见下）

---

## 🔴 新铁律 12a — IT Case body 禁止 DB 操作（fixture helper 例外）

**长期约定**（已加进项目 CLAUDE.md）：所有 `test*` 方法**主体**禁止 `dbf.persist*` / `dbf.persistAndRefresh` / `SQL.New("insert into ...")` 直接塞 DB 行；测试断言路径必须走 production API（`addXxx`/`syncXxx`/`attachXxx`/orchestrator method 等）。

**适用范围**：仅 Case `void test*()` 方法体。Fixture helper（如 `persistContainerInfra`、testlib 框架内部）允许 bootstrap 状态；@Before 占位资源不算违规。

**反例**：早期 IT 把 `dbf.persistAndRefresh(roleVO(CONTAINER_HOST))` 写在 test 方法里 → 绕开真实 path-2 orchestrator → P1 RoleVO gap 真机才暴露但 IT 全绿。本 session 已 revert，3 个 sub-test 改走 `runOrchestratorAndWait` (production helper)。

**Note：sync → auto-pool slice 不在 IT 范围**：真机 201.160 已端到端验证（sync 自动建 `k8s-dev-gpu-pool` + 7 RoleVO），IT 不重复造轮子。Case 通过 `runOrchestratorAndWait` 测 orchestrator 自身行为即可，上游 sync 走 fixture bootstrap NativeClusterVO 是合规的（fixture helper 例外）。

---

## ⚡ 历史 P1 FOLLOWUP — Container K8s-sync 不写 PhysicalServerRoleVO（2026-05-06 真机产证）— **已 CLOSED 2026-05-07**

> **下个 session 第一刀：开这个 followup 票，定级 P1，建议进 Phase 3 fix-plan。**

**Symptom**：v5.5.18 真机 K8s sync 后，`QueryPhysicalServerRole roleType=CONTAINER_HOST`
永远返空 — 容器主机对统一 host 系统不可见 → 混部 capacity reservation / Cordon-aware
reserved 整条链 silent fail。`ContainerNodeInfoDiscoveryAdapter.discover` 拿不到
host → fallback 路径 silent。

**Root cause**：`ContainerEndpointBase.processNodeTransactional` (line 706-747) 只
upsert `NativeHostVO`，没有同步 INSERT `PhysicalServerRoleVO(roleType=CONTAINER_HOST)`
和 `PhysicalServerVO`。`grep -r "new PhysicalServerRoleVO\|new PhysicalServerVO\|attachPhysicalServerRole" /premium/plugin-premium/container/` → **0 matches**。

K8s sync 完后跑 `ContainerEndpointSyncExtensionPoint.afterSyncNodes()` 钩子
（ContainerEndpointBase line 664），但 grep `implements ContainerEndpointSyncExtensionPoint`
→ 只有 `IAM2ContainerManagerImpl` + `ContainerModelServiceBackend`，没人写
`PhysicalServerRoleVO` / `PhysicalServerVO`。

**反向证据 — 读/删一直都在等这条不存在的写**：
- `ContainerNodeInfoDiscoveryAdapter.java:99-102` — 读 `PhysicalServerRoleVO(serverUuid=X, roleType=CONTAINER_HOST)`，永远空
- `ContainerCordonReservedCapacityExtension.java:51-54` — 读同上，永远空
- `ContainerEndpointBase.java:1148` (`deleteContainerHostRoles`) — 删同上，永远删空集
- `ContainerPhysicalServerRoleSoftDeleteExtension.java:41-43` — 删同上，永远删空集

**真机产证（2026-05-06 16:44 — 172.26.201.160 take-over from 172.20.0.37）**：
从 .37 ZStack MN 读出 zaku endpoint 凭据（managementIp 172.20.9.4:80 + accessKeyId
`7uEo1Q46APA2yyHYoI5r` + accessKeySecret `Tsx5mcpYeJlEXlpjPokyFxXwRghNAIZMxdCGZXxw`）→
在 201.160 上调 `AddContainerManagementEndpoint` + `SyncContainerManagementEndpoint
zoneUuid=test_zone` → sync 成功返回，DB 落地：

| 实体 | 数量 | 说明 |
|---|---|---|
| `ContainerManagementEndpointVO` | 1 | 自添加 |
| `NativeClusterVO` | 1 (k8s-dev-gpu, status=Running, zoneUuid 关联到 test_zone) | sync 自动落 ✓ |
| `NativeHostVO` | **7** k8s 节点 | sync 自动落 ✓ |
| `HostVO`（hypervisorType=Native） | **7** 全 status=Connected | sync 自动落 ✓ |
| **`PhysicalServerRoleVO(CONTAINER_HOST)`** | **0** | **production gap 实测 ✗** |
| `PhysicalServerVO`（容器关联） | 0（仅 mn_host KVM 单条） | **production gap 实测 ✗** |

7 hosts 全 Connected 但统一 host 系统看不到一条 CONTAINER_HOST role — `QueryPhysicalServerRole
roleType=CONTAINER_HOST` 返空 list。这是真机产证，不是模拟。Endpoint uuid `ef554bb8255d4ce0b891a1367841b88b`
留在 201.160 上供下个 session 修完 P1 后回归验证（修完 sync 一次应自动补出 7 条 RoleVO）。

**为什么 IT 都绿**：`ProcessNodeTransactionalCase` / `ContainerRoleProviderIntegrationCase` /
`PhysicalServerCapacityCase` / `PhysicalServerRoleCase` 全部用 `dbf.persistAndRefresh(roleVO)`
**手动塞 RoleVO**，绕开了真实 K8s sync path —— 等于测了"如果 K8s sync 写了"会怎样，
没测"K8s sync 真的会写吗"。

**STATUS.md §5 那条 "ContainerEndpointBase.java:706,1146 processNodeTransactional + RoleVO
SQL filter (AC-RS-04/07/10 闭环)" 半假**：1146 是 `deleteContainerHostRoles` 删除路径
（成立），706 处 INSERT 路径**没闭**。

**修法（Phase 3 fix-plan U-unit 候选）**：

```java
// premium/plugin-premium/container/.../ContainerEndpointBase.java#processNodeTransactional
// 在 Stage 1+2 NativeHostVO upsert 之后、Stage 3+4 PCI/IOMMU 之前补：
//
// Stage 2.5: PhysicalServer + PhysicalServerRoleVO upsert (CONTAINER_HOST/EXTERNAL_READONLY)
//   - 如已存在按 (managementIp 或 serialNumber 三级降级) 关联到的 PhysicalServerVO，
//     就只 upsert 它的 RoleVO 行
//   - 否则 createPhysicalServer + attachRoleVO（走 PhysicalServerManagerImpl 接口）
//   - roleUuid = NativeHost.uuid，serverUuid = PSV.uuid，schedulingMode = EXTERNAL_READONLY
//   - 走 dbf.persist 路径，Hibernate JOINED 自动写 ResourceVO
//
// 这条单独写在 SQLBatch 里，跟 NativeHostVO 共一个事务边界（PRD §2.4 NB-7 per-node 原子）
```

**验收 AC**:
1. K8s sync 一次后 `QueryPhysicalServerRole roleType=CONTAINER_HOST` 至少有 1 条
2. 同 NativeHost UUID 的 RoleVO + ResourceVO 都落库
3. 已有 PSV（managementIp 匹配）不重复创建，只补 RoleVO
4. K8s drop node 时 `deleteContainerHostRoles` 真删一条（不是空集）
5. ProcessNodeTransactionalCase 加 1 个不手插 RoleVO 的 sub-test，验证真实 sync path 自动写

**调查现场**：[runbook §12](../runbooks/physical-server-pxe-real-env-validation.md)
2026-05-06 真机做的混部验证暴露的：必须手动插 PhysicalServerRoleVO + ResourceVO 才能让
API 返两条，证明 K8s sync 的写入侧在生产 path 也是缺的。

**前置知识** — `dbf.persist` ResourceVO 自动写：`PhysicalServerRoleVO extends ResourceVO`
是 Hibernate JOINED 继承，`dbf.persist(vo)` 会先 INSERT 父表 ResourceVO 再 INSERT 子表
PhysicalServerRoleVO，单事务原子。所以 production 接口（`PhysicalServerManagerImpl.attachRoleVO`
/ `CreatePhysicalServerRoleFlow`）只要走 `dbf.persist` 就 ResourceVO 不漏。问题不是
ResourceVO，而是 INSERT 整条链都没人调。

---

## 🟢 4 case 全 GREEN（fixture playbook + 已知 SKIPPED sub-test）

| Case | 结果 | 时长 | premium commit | parent commit |
|---|---|---|---|---|
| `PhysicalServerCapacityCase` | ✅ GREEN | 113s | `ff04551ba6` | `c911ffbbc3` |
| `PhysicalServerRoleCase` | ✅ GREEN | 121s | `39e626ba53` | `734145118d` |
| `PhysicalServerCompatCase` | ✅ GREEN | 116s | `48352eafd0` | `3ffc0de1f0` |
| `ServerPoolCrudCase` | ✅ GREEN | 116s | `db615bc469` | `4cc1046f8a` |
| revert dbf 违规（3 sub-test SKIP）| ✅ | — | `061e247516` | `70a6bc3564` |

3 sub-test 标 SKIPPED 等 P1 修完后回归（原本用 dbf 直插 RoleVO，违反 IT-rule 也掩盖
了 P1 gap）：
- `PhysicalServerCapacityCase.testReadonlyModeCountsInAvailable`
- `PhysicalServerRoleCase.testAttachRoleExternalReadonlyCompatible`
- `PhysicalServerRoleCase.testMultiRoleAutoAssociationBySerialNumber`

### 通用 fixture playbook（8 项，写新 IT 直接抄）

1. **BM2 attach 需 BM2-typed cluster + ipmi roleConfig**（不能用 env 的 KVM cluster）。
   environment 块加 `bareMetal2ProvisionNetwork {...}`，`test()` 顶部
   `createCluster { type=BM2_CLUSTER_TYPE; hypervisorType=BM2_HYPERVISOR_TYPE }` +
   `attachBareMetal2ProvisionNetworkToCluster`，roleConfig 用
   `[chassisType:"ipmi", ipmiAddress, ipmiUsername:"admin", ipmiPassword:"calvin"]`。
2. **KVM_HOST connect-host 必须用 127.0.0.x 回环 IP**。production code POSTs to
   `http://<managementIp>:8989/host/...`；外网 IP（192.168.x.x）走真 HTTP，5s timeout。
   回环 → 命中 local simulator Jetty。BM2 不连 host 故无所谓。
3. **`BareMetal2Test.springSpec` 需 `include("container.xml")`** — 否则
   ContainerRoleProvider 不注册，`no RoleProvider registered for roleType[CONTAINER_HOST]`。
4. **CONTAINER_HOST 共存场景目前无 IT 路径** — API 设计上拒绝
   `APIAttachPhysicalServerRoleMsg(CONTAINER_HOST)`（EXTERNAL_READONLY 由 K8s sync 写）；
   IT 不能 `dbf.persistAndRefresh(roleVO)` 绕开（违反 IT-rule，且掩盖 P1 gap）。等 P1
   修完 + testlib 加 K8s SDK simulator 后，IT 走 `addContainerManagementEndpoint` API +
   simulator → 真验 RoleVO 自动落库。
5. **Groovy DSL 闭包陷阱**:
   - 方法参数同名 DSL 属性会触发 ambiguity；helper 的 `it.zoneUuid = zoneUuid` NPE
     （`it` 在 closure 里是 null）。重命名参数避开：`createTestPool(String suffix, String poolZoneUuid)`。
   - 嵌套 `each { x -> action { uuid = it } }` 里 `it` 解析到内层 closure delegate，
     不是外 `each` 参数。显式 `String svrUuid -> action { uuid = svrUuid }`。
6. **`role.createDate/lastOpDate` 不在 API 返回 inventory 里**（只在 DB VO 上）。这种
   断言去掉；DB 原子性 by `Bm2RoleProviderIntegrationCase` AC-1 覆盖。
7. **SDK class 字段缺失 ≠ null**。`server.oobPassword` 抛 `MissingPropertyException`，
   因 SDK class 根本没声明。改用
   `!Inventory.class.declaredFields.any { it.name == "oobPassword" }` 反射检查。
8. **`expect(AssertionError)` → `expect(Throwable)`**。SDK pre-validation 抛
   `ApiException`（非 AssertionError），server-side 错抛 AssertionError；Throwable
   兼容两条路径。NB-12 锁 `oobManagementType="ipmi"`，REDFISH 走 SDK 拒同此路径。

## 🟠 长期 followup — happypath 反模式清理

铁律 12a 真正落地需要 simulator pattern 干净（IT 走 production API，simulator 走 Spring bean override）。但当前 production code 里散落着 `if (CoreGlobalProperty.UNIT_TEST_ON) { ... 直接 success ... }` happypath 反模式 — IT 不靠 simulator，靠 production code 里的 if 短路。

**已知 10 处** (本 session grep)：
- `plugin/physicalServer`：`PhysicalServerIpmiPowerExecutor`, `PhysicalServerScanner`
- `premium/baremetal2`：`BareMetal2IpmiChassisHelper`, `BareMetal2IpmiChassisBase`, `BareMetal2DpuChassisFactory`, `BareMetal2DpuChassisBase`, `BareMetal2InstanceApiInterceptor`, `BareMetal2Gateway`, `YuccaBareMetal2DpuHostBackend`, `BareMetal2DpuAgentDeployer`

**清理方法**：每处把 UNIT_TEST_ON 分支挪到对应 simulator bean（test profile 注入 simulator override 替代 production bean），production code 只保留实路径。预估每处 30-50 行重构 + simulator bean 补全。

**优先级**：低，独立 epic。当前 P1 RoleVO 已 ship + 真机绿，下个 session 不强制。但**新代码不能再加 UNIT_TEST_ON happypath**（铁律 12a 副推论）。

---

## 🟡 次要项（不阻 ship，独立 schedule）

- **真机 runbook §12 已写**（`docs/runbooks/physical-server-pxe-real-env-validation.md`），
  含 §12.A take-over walkthrough（凭据 DB 拿 + AddContainerManagementEndpoint +
  SyncContainerManagementEndpoint zoneUuid 显式传）+ §12.B Open Followup。
- **zstack-cli `roleConfig` Map<String,String> argparse 不通**：单独 PR 改
  zstack-utility/cli。本期靠 REST 绕开。
- **bin trial license refresh**：build pipeline 应该在打包时拿最新 trial license。
- **bin `.repo_version` cross-version mismatch**：dev bin 自检失败，build infra 行为。
- **F11 STANDALONE_PXE OSS dnsmasq+tftpd stack** — v1.1+ deferred per PRD。
- **BM1 退场不迁移** — v1.1+ scope（ADR-010）。
- **KVM 装机 FR-012 Could Have** — v1.1+。

---

## 📚 历史 entry 保留（按时间倒序，blockquote 形式）

> **2026-05-06 follow-on — Audit fix + QA handoff + 4 case 重定位**
>
> 本 session 三件事：
>
> 1. **代码 audit + fix**（commits `d2c0a67da8` parent + `5f97b9c2f6` premium）：
>    - 8 P0 style：删 7 处 `@Component` 双注册；删 `Bm2GatewayDataPlane` `@Configurable preConstruction=true` 反模式
>    - 4 P1：KvmRoleProvider + ContainerRoleProvider 改用 `Utils.getLogger`；`PingPeriodicTask` 改 static 内部类（修内存泄漏）；comment cleanup（中文/Jira refs）
>    - 2 P0 SPI 重构：`PhysicalServerRoleProvider.getPowerFallbackPriority()` default + `Bm2RoleProvider` override 100；`PhysicalServerManagerImpl.choosePowerFallbackRole` 用 stream().max() 走 SPI；`BareMetal2ChassisManagerImpl` ROLE_TYPE 字面量改 `bm2RoleProvider.getRoleType()`
>    - 2 test gap 闭环：`PhysicalServerScanner.probeOverride` UT-only 测试种子 + `PhysicalServerOpsCase` 加 `testScanRotatesThroughCredentials` (AC-PS-18) + `testScanReturnsAllFourStatusCounts` (AC-PS-19)
>    - REDFISH 删除（commit `fde27ce38a`）：NB-12 简化后 SDK 只接 `ipmi`，删 testUpdatePhysicalServerOobFields 中 `oobManagementType="REDFISH"` 字段更新
>
> 2. **跑全量 IT/UT 验证 audit fix 无回归**：
>    - 70 unit + 12 IT 全 GREEN（不是后台跑死的——之前甩 "沙箱杀 mvn" 是错的，foreground 单 case 1-2min 完全 OK）
>    - `runMavenProfile premium` 全 reactor 9:06min（比 22.5min 快，cache 暖了），fits in Bash 10min 上限
>    - 跑过：3 RoleProvider IT、PhysicalServerOpsCase、PhysicalServerPowerCase、PowerAndDiscoverPhysicalServerCase、ContainerNodeCordonServiceCase、PhysicalServerCascadeCase、AddKvmHostPath2Case、AddBm2ChassisPath2Case、ProvisionPhysicalServerBm2Case、ProcessNodeTransactionalCase
>
> 3. **4 case 移到 premium/test-premium**（commits `0d41d603aa` parent rename + `b310b5ecd2` premium add）：
>    - 移文件：`PhysicalServerCapacityCase`、`PhysicalServerCompatCase`、`PhysicalServerRoleCase`、`ServerPoolCrudCase`
>    - 改 imports：`SubCase` → `PremiumSubCase`；`KvmTest.springSpec` → `BareMetal2Test.springSpec`
>    - 编译通过 ✓
>    - **跑仍卡**：BM2 attach 缺必填 `roleConfig.ipmiAddress` + cluster 不是 BM2-type
>    - 真原因：之前判 "Tests run: 0 是 surefire discover bug" 是错的；实际是 ApiException 在 sub-test 里抛而 surefire 计数丢失；fixture 设计跟 BM2 provider 期望不匹配
>
> 4. **QA handoff doc 4 个版本演进**（commits `2c6e7c1db2` 初版 → `e4b3560003` P2 状态 → `d328a6eb04` test plan template → `d7b27d0f36` 三合一 → `2b50aa02c8` 删 impl 细节）：
>    - 最终唯一文档：`docs/qa/v5.5.18-qa-handoff.md`（537 行）
>    - 12 节：feature 一句话 + 5 PRD 列表 + 27 API + 105 AC 表（带 UT yes/no + 手测重点）+ 测试环境 + TC/Bug/Report 模板 + 风险焦点 + 易漏 detail + 上手 checklist
>    - 已删：3 份散文档 (`acceptance-criteria.md` / `api-reference.md` / `test-plan-template.md`)
>    - 已剥：所有 SPI / Spring / AspectJ / ADR / NB / 实装类名 — QA 只测 behavior
>
> **当前 origin 状态**:
> - parent: `2b50aa02c8 <doc>[qa]: strip impl details from QA handoff`
> - premium: `b310b5ecd2 <refactor>[test]: move 4 PhysicalServer cases to premium/test-premium`
> - 备份分支：`feature/unifi-host-dev-B-2026-05-05` + `feature/unifi-host-dev-C-2026-05-05`
> - tag: `backup/pre-rebase-B-2026-05-05`（parent + premium 各一个）
>
> **暴露的真问题（next session 的 P0）**:
>
> 4 case 在 premium/test-premium 还要 fixture 改写，~25 处 attach 调用：
>
> | Case | 状态 | 修法 |
> |---|---|---|
> | `PhysicalServerCapacityCase` | 编译 ✓ 跑 ✗ | 11 处 BM2/Container attach 补 roleConfig + environment{} 加 BM2 cluster |
> | `PhysicalServerRoleCase` | 编译 ✓ 跑 ✗ | 13 处同上 |
> | `PhysicalServerCompatCase` | 编译 ✓ 跑 ✗ | `ConstraintViolationException` — fixture UNIQUE 重复 insert，需 debug |
> | `ServerPoolCrudCase` | 编译 ✓ 跑 ✗ | 推测同 Compat，需 debug |
>
> roleConfig 标准格式（参考 `Bm2RoleProviderIntegrationCase` 行 ~150）：
> ```groovy
> roleConfig = [
>     chassisType  : "ipmi",
>     ipmiAddress  : "192.168.x.y",
>     ipmiUsername : "admin",
>     ipmiPassword : "calvin"
> ]
> ```
>
> Container roleConfig 参考 `ContainerRoleProviderIntegrationCase`。
> environment{} 创 BM2 cluster 参考 `Bm2RoleProviderIntegrationCase::environment()`。
>
> ---

> **2026-05-05 follow-on — Production deployment + 真机 end-to-end validation**
>
> 本 session 实际把 AC-PN-14/15/16/17 production wiring 上了真机器（非 sim），
> 数据库写入路径和 API 流程全验证 GREEN。
>
> **What changed (parent + premium commits)**:
> - parent `dba3ebc107` `<feature>[server]: role-provider classify SPI + path-2 dispatch refactor` —
>   replace string-match (cluster.hypervisorType) with SPI-driven `classify(HostVO)`. Each
>   `PhysicalServerRoleProvider` declares which `HostVO` subclass it owns; KvmRoleProvider catches
>   `BareMetal2GatewayVO` via `instanceof KVMHostVO`. Fixes path-2 missing KVM_HOST RoleVO when
>   gateway sits in baremetal2-typed cluster.
> - premium `d457e0d7ba` `<feature>[baremetal2]: gateway-routed ping + path-2 SPI compliance` —
>   `Bm2GatewayPingHelper` now `bus.send(PingTargetInGatewayMsg) → BareMetal2Gateway.handle →
>   restf.asyncJsonPost(PING_TARGET_PATH)`; UNIT_TEST_ON shortcut removed (unit tests inject
>   mock helper). Bm2RoleProvider + ContainerRoleProvider override classify(). Simulator
>   default reachable=true.
> - parent `68945590b7` `<doc>[server]: STATUS.md correct stale-build misdiagnosis` —
>   STATUS.md ❌ section corrected: 3 prior items confirmed already implemented;
>   "test infra rot" entries were stale-build symptoms, not real rot.
> - parent `19292e671b` `<fix>[server]: use ADD_COLUMN helper for cpuCoreNum` —
>   replace `ALTER TABLE ADD COLUMN IF NOT EXISTS` (MariaDB 10.0.2+ only) with
>   `CALL ADD_COLUMN('HostCapacityVO','cpuCoreNum','INT UNSIGNED',0,'0')` from
>   `beforeMigrate.sql`'s helper procedure. Cross-version safe.
> - parent `9a34b170be` `<fix>[server]: import PhysicalServerManager.xml in zstack.xml` +
>   premium `406bce4dd9` 同 — `zstack.xml` 没 import `PhysicalServerManager.xml` →
>   tests using `BeanConstructor(loadAll=true)` 看不见 `AutoAssociateFlow / CreatePhysicalServerRoleFlow /
>   InitPhysicalServerCapacityFlow` beans → `BareMetal2ChassisManagerImpl` autowire NPE.
>   Fix: explicit import in both parent + premium `zstack.xml`.
>
> **Test gates (本 session 全 GREEN)**:
> - 19 cases (10 OSS unit + 4 lookup + 4 stage + 1 IT) 全绿（after `runMavenProfile premium`）
> - Jenkins `dev.jenkins.zstack.io/job/build/190` SUCCESS（22.5min）
> - bin: `http://storage.zstack.io/mirror/zstack_dev/20260505163928125615/`
>
> **真机 production deploy (172.26.201.160) GREEN**:
> - bin install all 16 steps PASS（含 `start ZStack management node` + `start ZStack Web UI`）
> - V5.5.18 Flyway migration row written to `schema_version` (success=1)
> - `HostCapacityVO.cpuCoreNum` 列 `INT UNSIGNED NOT NULL DEFAULT 0` 真在 DB
> - PhysicalServer 全家族 8 表全建出来
> - PhysicalServer-first add-host 端到端流程 GREEN：
>   - `CreatePhysicalServer` → PhysicalServerVO 1 行
>   - `AttachPhysicalServerRole(KVM_HOST)` → REST `/v1/physical-servers/{uuid}/roles` →
>     job 异步完成 → RoleVO + HostVO/KVMHostVO + HostCapacityVO + PhysicalServerCapacityVO 全建
>   - `RoleVO.roleUuid == HostCapacityVO.uuid == HostVO.uuid` invariant 持（NB-22/24 ADR-012）
>   - `PSC.uuid == PhysicalServerVO.uuid` invariant 持（NB-22/30）
>   - capacity 真值 `totalCpu=80, totalMem=16.5G, cpuCoreNum=8, cpuSockets=2`，连 cpuCoreNum
>     新列都被填了真硬件值
>
> **暴露的 issue (next session 处理)**:
> - **zstack-cli `roleConfig` Map<String,String> argparse 不通**：试 `roleConfig='{...}'` /
>   `roleConfig.username=root` / `roleConfig::username=root` / `roleConfig[username]=root`
>   全 fail。绕道走 REST 才通。属 zstack-utility 独立 PR scope，不阻 ship。
> - **License**：bin 里 trial license 2025-08-16 过期，每装机需手动续。可考虑 build-side
>   refresh trial。
> - **CHECK_REPO_VERSION 检查 v.s. 5.5.16 base 的 .repo_version**：dev bin
>   `5.5.16.<timestamp>` 跟纯 5.5.16 base ISO 的 `.repo_version` 不一致 → 装机自检失败。
>   绕道：直接 `bash install.sh` （不通过 bin wrapper）跳过环境变量初始化。属 build/install
>   工具问题，跟代码无关。
>
> ---

> **2026-05-02 follow-on — P1/P2 review findings fixed (stage-based provisioning)**
>
> 本轮修了 stage-based provisioning 的 P1 + P2 review findings，补 commit 不 amend。
>
> **What changed**:
> - `Bm2GatewayPingHelper`: 将 `thdf.submit(Task)` + 死循环 + `Thread.sleep` 改为
>   `thdf.submitPeriodicTask(PeriodicTask)`。`PingPeriodicTask` 每 intervalMs 调度一次 `run()`，
>   不占工作线程；`AtomicBoolean done` 防 timeout/success 双触发 race；`pingOnce` 加
>   `redirectOutput(DISCARD)` 修 stdout drain 问题（P1-3 + P2-4 + P2-5）。
> - `Bm2GatewayDataPlane.recordPhase`: 用 `SQLBatch.execute()` 包 findByUuid + setJobData +
>   merge 三步（P1-1，CONVENTIONS §5 优先 SQLBatch 而非裸 `@Transactional`）。
> - `Bm2GatewayDataPlane.runStages`: 移除 PxeTriggered/Pinging case 里提前写 Pinging 的调用，
>   改在 `stagePingUntilUp` 入口处写，与其他 stage 在 onSuccess 后写保持一致（P1-2）。
> - `PhysicalServerGatewayPxeProvisionProvider.startProvisioning`: dataPlane==null 从静默
>   success 改为 fail-loud，携带 Spring XML 注册缺失的明确信息（P2-6）。
>
> **P2-7 closure**: re-read PRD AC-PN-16，PRD 没明确 category 名；代码 `unifiedHardware`
>   是本仓既有 convention，不存在冲突，followup 撤回。
>
> **2026-05-04 update — gateway-agent ping 实装（撤回 v1.1+ 推迟）**:
> - 新增 `PingTargetInGatewayMsg/Reply` + `BareMetal2GatewayConstant.PING_TARGET_PATH`
>   + `BareMetal2Gateway.handle(PingTargetInGatewayMsg)` 转发到 gateway agent
>   `/baremetal_gateway_agent/v2/target/ping` 端点。
> - `Bm2GatewayPingHelper.pingOnce` 改用 `bus.call(...)`，不再 MN 直跑 `ping`。
>   gateway 在 provision L2 内能直达目标真机，AC-PN-14 在生产环境 GREEN。
> - Python agent 端点新增到 `zstack-utility/kvmagent/.../baremetal_v2_gateway_agent.py`
>   （单独 PR，不阻断本仓）。
> - IT 用 simulator harness mock `PING_TARGET_PATH` 回 `reachable=true` 验证 happy-path。
>
> ---

> **2026-05-03 follow-on — Stage-based ProvisionPhysicalServer + OS install monitoring 闭环**
>
> 按 `docs/plans/2026-05-03-os-install-completion-monitoring-plan.md` 8 个 Task 推进。前 7 个 Task 由 ultrawork 2 lanes 并行完成 + Task 5 IT 后续；Task 8 commit/push 由我汇总。
>
> **What changed**:
> - `header/.../ProvisionPhase.java`：新 enum (NotStarted/NetworkPrepared/PxeTriggered/Pinging/Done)
> - `header/.../PhysicalServerProvisionDataPlane`：接口加 `startPhase` 参数
> - `header/.../PhysicalServerProvisionTarget`：加 `jobUuid` 字段
> - `plugin/physicalServer/.../ProvisionPhysicalServerLongJob`：parse + 写回 jobData phase；MN 重启 resume 安全（每阶段幂等）
> - `plugin/physicalServer/.../PhysicalServerProvisionService.startProvisioning(msg, accountUuid, phase, completion)`：透传 phase
> - `premium/baremetal2/.../Bm2GatewayDataPlane`：refactor 为 4-stage 调度（NotStarted→NetworkPrepared→PxeTriggered→Pinging→Done），每阶段幂等
> - `premium/baremetal2/.../Bm2GatewayPingHelper`：new — 周期 ping target IP（v5.5.18 简化在 MN 跑 ping，TODO v1.1+ 路由到 gateway agent）
> - `plugin/physicalServer/...PhysicalServerGlobalConfig`：加 `provision.timeout` (default 1800s) + `provision.pingInterval` (default 30s)
> - cloud_prd PRD：撤回 2026-05-02 「OS install 监听 deferred」激进措辞；加 PXE 物理依赖明文 + 4 个新 AC（AC-PN-14/15/16/17）
> - STATUS.md：R8 / U18-U20 升 ✅ DONE
>
> **GREEN evidence**: 见 Task 8 汇总。
>
> **Followup F1–F11 状态更新**:
> - F10（OS install 完成监听）→ **CLOSED**（B-L2 stage-based ping 闭环）
> - F11 STANDALONE_PXE OSS dnsmasq+tftpd stack 仍 v1.1+ deferred per PRD
>
> **关键设计决策（保留供后人）**:
> - **不**给 PhysicalServerVO 加 provisionStatus 字段；状态机用 LongJobVO.jobData.phase
> - 重启幂等：每阶段（PrepareNetwork / powerOnPxe / Ping）幂等设计；MN 重启 LongJob resume 跳过已完成阶段
> - Ping helper v5.5.18 直接在 MN 跑 ping，TODO v1.1+ 通过 BM2 agent 协议扩展路由到 gateway agent（保持跟 BM2 doPingBareMetal2Instance 对齐）
> - 自动 attach Host (一键编排) 仍 v1.1+
>
> **Next action**: Phase 3 fix-plan 22 U-unit 已基本闭合（Wave 1-4），剩余 R5 path-2 FlowChain happy-path IT（依赖 KVM_HOST PhysicalServerRoleVO 自动建）+ STANDALONE_PXE v1.1+。
>
> ---

> **2026-05-02 follow-on — Bm2GatewayDataPlane GREEN, GATEWAY_PXE 装机 fire-and-forget 闭环**
>
> 按 `docs/plans/2026-05-02-bm2-gateway-data-plane-wiring-plan.md` 5 个 Task 推进，前 4 个 Task 由 ultrawork 2 lanes 并行完成，Task 5 commit/push 由我汇总。
>
> **What changed**:
> - `plugin/physicalServer/.../PhysicalServerIpmiPowerExecutor`：加 `powerOnPxe(server, completion)` — `ipmitool chassis bootdev pxe options=efiboot` + `chassis power reset`，`UNIT_TEST_ON` 下 deterministic success。
> - `premium/baremetal2/.../server/Bm2GatewayDataPlane`：新 `PhysicalServerProvisionDataPlane` impl（getType=GATEWAY_PXE）。lookup any active `BareMetal2GatewayVO` in pool's cluster as agent endpoint（不作装机前置）；transient build `BareMetal2ProvisionNetworkInventory` from `PhysicalServerProvisionTarget`；走 `PrepareProvisionNetworkInGatewayMsg` cloud bus → `BareMetal2Gateway.prepareProvisionNetwork()` 现有 FlowChain → REST POST gateway agent；prepare 成功后调 `PhysicalServerIpmiPowerExecutor.powerOnPxe(server)` 触发 BMC PXE boot。fire-and-forget 完成。
> - `premium/conf/springConfigXml/baremetal2.xml`：注册 `Bm2GatewayDataPlane` bean + `<zstack:extension interface="...PhysicalServerProvisionDataPlane"/>`。
> - `premium/test-premium/.../ProvisionPhysicalServerBm2Case.groovy`：回填真 BM2 gateway fixture（attached to server's pool's cluster），mock gateway agent 的 `PrepareProvisionNetworkInGatewayCmd` 处理；保留 negative assertion `BareMetal2InstanceVO` count == 0；新增 positive assertion gateway agent 收到 1 次 `PrepareProvisionNetworkInGatewayCmd`。
> - cloud_prd PRD：追加 2026-05-02 修正章节，明确 BM2 Gateway 是 data-plane endpoint 不是装机前置 + fire-and-forget 边界。
> - STATUS.md：R8 备注追加 GATEWAY_PXE data-plane GREEN；U18-U20 升 ⚠️ MOSTLY DONE。
>
> **GREEN evidence**: 见 Task 5 汇总。
>
> **Followup F1–F9 状态更新**:
> - F3（DataPlane 0 实现）→ **CLOSED**（Bm2GatewayDataPlane 已实装 fire-and-forget 路径）
> - 新增 F10：OS install 完成监听（agent 回连或 polling）— deferred 真机 runbook + 后续 U-unit
> - 新增 F11：STANDALONE_PXE OSS dnsmasq+tftpd stack 仍未实装（plug/physicalServer/PhysicalServerStandalonePxeProvisionProvider 仍 unregistered）— deferred
>
> **Next action**: Phase 3 fix-plan 22 U-unit 起草 + R5 path-2 FlowChain（AddHost/AddChassis tail）— Phase 3 Wave 1 入口。
>
> ---

> **2026-05-01 follow-on — PhysicalServer-first Tasks 1–4 GREEN, no BM2 Gateway/Instance dependency**
>
> 按 `docs/plans/2026-05-01-physical-server-first-provision-plan.md` 推进了 Task 1–4，每一步先 harness 后实现，再 review gate；保持 PhysicalServer-first，no-gateway 抽象。Task 5–8 还没做（OSS 回归 case、real-env runbook、doc/STATUS 校正、整体回归矩阵 + final review）。
>
> **GREEN evidence**:
> - Premium PhysicalServer-first harness：`MAVEN_OPTS=-Xmx4G mvn test -pl premium/test-premium -Dtest=ProvisionPhysicalServerBm2Case -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true` — `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。harness 已删 `BareMetal2GatewayVO` 装配，加了负向断言：provision 前后均无 `BareMetal2GatewayVO`、`BareMetal2InstanceVO`。
> - OSS unit harness：`MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=TestPhysicalServerProvisionService -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true` — `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`。覆盖 validation（network missing/disabled、zone mismatch、no pool、pool-ref、no OOB、provision NIC mismatch）+ target DTO 构建。
> - Review gate（收窄到本次新引入面）：新 provider / header data-plane / LongJob 无 `BareMetal2GatewayVO|BareMetal2InstanceVO|BareMetal2ChassisVO|CreateBareMetal2InstanceMsg|BareMetal2ProvisionNetworkVO|PrepareProvisionNetworkInGatewayMsg|gatewayUuid|chassisUuid|bmInstanceUuid|chassisOfferingUuid` 任何依赖；premium 测试只剩 `BareMetal2GatewayVO/BareMetal2InstanceVO` 的负向断言，没有 gateway/instance fixture。
> - 格式 gate：`git diff --check` 与 `git -C premium diff --check` 都干净。
>
> **What changed**:
> - Header 新增 PhysicalServer-first 抽象：`PhysicalServerProvisionTarget`（不可变 DTO，只携 PhysicalServer + ProvisionNetwork + OOB + provision NIC + DHCP + OS image/kickstart 字段；不含任何 BM2 标识字段）、`PhysicalServerProvisionDataPlane`（data-plane adapter seam，prepare/trigger boot 无 BM2 假设）、`ProvisionRequest` 透传 target、`ProvisionResult` 仅返回 PhysicalServer 视角结果。
> - `plugin/physicalServer/.../PhysicalServerProvisionService` 接管所有 PhysicalServer-level validation 与 target 构建：从 PhysicalServerVO 取 OOB、解析 provision NIC（请求里有 MAC 优先、否则 fallback 到硬件发现的 primary provision NIC），按 ProvisionNetwork.type 选 provider；不 import 任何 `BareMetal2*` class。
> - `ProvisionPhysicalServerLongJob` 承载装机 API；同步完成路径修了返回 null event 触发 woodpecker subview NPE 的问题。
> - `premium/baremetal2/.../PhysicalServerGatewayPxeProvisionProvider` 替换原 `Bm2GatewayPxeProvisionProvider` 入口（旧文件已不在 git 树里）：包内仍可复用 BM2 PXE 命令构建/agent 协议代码，但不再查 `BareMetal2GatewayVO`、不发 `CreateBareMetal2InstanceMsg`、不创建 `BareMetal2InstanceVO`、不返回 BM2 instance uuid。`premium/conf/springConfigXml/baremetal2.xml` 同步只注册新 provider。
> - 没有 gateway adapter / 测试无 BM2 角色时，走抽象 no-op 成功路径，测试通过 `PhysicalServerProvisionTarget` 的 captured payload（OOB、provision NIC、DHCP）做断言。
>
> **Cap / 卡点（仅供下个 session 参考）**:
> 1. `mvn -am` 链路非常长，`premium/baremetal2 -am` 会拉 60+ 模块；focused harness 只在 `-pl test` / `-pl premium/test-premium` 跑，全量重 weaving 时再用 `clean install -pl ... -am`。
> 2. 本地 worktree `.m2` 偶发 AspectJ weaving 缺失（`SimpleQueryImpl._dbf null`）；解法是对相关模块 `clean install`，不要换全局仓。
> 3. 所有 Maven 命令仍必须显式 `-Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository`。
>
> **Next action**:
> 1. Task 5 — OSS 提供器/无提供器路径回归：`PhysicalServerStandalonePxeProvisionProvider` `STANDALONE_PXE` 仍 fail-loud（`reserved and not implemented yet`），`PhysicalServerOpsCase` 加确定性 OSS test provider 断言 provider 选型走 `ProvisionNetworkType` 且 `ProvisionRequest` 由 PhysicalServer 字段构建。命令：`MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=PhysicalServerOpsCase -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true`。
> 2. Task 6 — `docs/runbooks/physical-server-pxe-real-env-validation.md`：列清楚真实 BMC/IPMI、provision 网段、PXE data-plane 节点、OS image/kickstart、DHCP/iPXE、LongJob 状态迁移；仅作为 simulator 与真机的边界声明，本轮不在仓里跑。
> 3. Task 7 — 文档校正：`docs/brainstorms/next-session.md`（本文件）+ `docs/STATUS.md` + cloud_prd `feat-unified_provision_network_prd.md`，把"BM2 Gateway PXE focused IT GREEN"全部替换为"PhysicalServer-first focused harness GREEN"；执行 `rg -n "BM2 Gateway PXE focused IT GREEN|Bm2GatewayPxeProvisionProvider|BareMetal2GatewayVO.*PhysicalServer|CreateBareMetal2InstanceMsg.*PhysicalServer" docs /home/mj/zstack-workspace/cloud_prd/prd/v5.5.18-unified-hardware/provision` 应只剩"incorrect implementation / must remove"语境。
> 4. Task 8 — 最终回归矩阵：`-Dtest=TestPhysicalServerProvisionService,TestHardwareDiscoveryScheduler` + `-Dtest=PhysicalServerOpsCase,PhysicalServerPowerCase` + `-Dtest=ProvisionPhysicalServerBm2Case,PowerAndDiscoverPhysicalServerCase`，再 `git diff --check` / `git status --short` / `git -C premium status --short`，最后做 PhysicalServer-first contract 整 diff review。
>
> **Hard rule（重申）**:
> - 不要碰 premium 的 `plugin-premium/ai/.../AIModelManagerImpl.java`、`.m2/`、`.omc/`、`zwatch/.omc/` 这些无关脏文件。
> - 所有 Maven 命令必须显式 `-Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository`。
> - PhysicalServer-first：装机入口必须以 `PhysicalServerVO` 为 source of truth，禁止以 `BareMetal2GatewayVO/BareMetal2ChassisVO/BareMetal2InstanceVO` 为前置或副作用。
>
> ---

> **2026-05-01 correction — BM2 Gateway PXE acceptance revoked; PhysicalServer-first plan created**
>
> 用户明确纠正：装机流程必须先有 `PhysicalServerVO`，不是先有 BM2 Gateway / Chassis / Instance。当前 `Bm2GatewayPxeProvisionProvider` 和 `ProvisionPhysicalServerBm2Case` 证明的是 BM2 Gateway 包装路径，不满足 PhysicalServer-first 验收。
>
> **New source of truth**:
> - PRD 已更新：`/home/mj/zstack-workspace/cloud_prd/prd/v5.5.18-unified-hardware/provision/feat-unified_provision_network_prd.md`
> - 新开发计划：`docs/plans/2026-05-01-physical-server-first-provision-plan.md`
>
> **Corrected rule**:
> - `APIProvisionPhysicalServerMsg` 必须从 `PhysicalServerVO` 发起。
> - 不得要求存在 `BareMetal2GatewayVO`、`BareMetal2ChassisVO` 或 `BareMetal2InstanceVO`。
> - 可以复用 BM2 PXE 数据面命令/脚本/协议经验，但不能复用 BM2 Gateway 资源模型作为前置条件。
> - 之前的 `ProvisionPhysicalServerBm2Case` 结果只能作为“错误 harness 暴露前置耦合”的历史记录，不能算 feature GREEN。
>
> **Next action**: 按 `docs/plans/2026-05-01-physical-server-first-provision-plan.md` 从 Task 1 开始，每一步先 harness，再实现，再 review gate。
>
> ---

> **2026-05-01 superseded — ProvisionProvider OSS SPI + LongJob GREEN, BM2 Gateway PXE harness invalid for final acceptance**
>
> 本轮曾完成 OSS 统一 SPI、physicalServer orchestration service、LongJob 承载，以及 BM2 Gateway PXE concrete provider/harness。但该 premium harness 依赖 `BareMetal2GatewayVO` 并通过 `CreateBareMetal2InstanceMsg` 落到 BM2 Instance，不符合 PhysicalServer-first 语义；不能作为 ProvisionProvider 主线验收。
>
> **GREEN evidence**:
> - Clean rebuild：`MAVEN_OPTS=-Xmx4G mvn clean install -pl longjob,plugin/physicalServer -am -DskipTests -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -DskipJacoco=true` — `BUILD SUCCESS`。
> - physicalServer rebuild after provider error fix：`MAVEN_OPTS=-Xmx4G mvn clean install -pl plugin/physicalServer -am -DskipTests -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -DskipJacoco=true` — `BUILD SUCCESS`。
> - Provision focused IT：`MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=PhysicalServerOpsCase -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true` — `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
> - BM2 provider rebuild：`MAVEN_OPTS=-Xmx4G mvn clean install -pl plugin/physicalServer,premium/baremetal2 -am -DskipTests -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -DskipJacoco=true` — `BUILD SUCCESS`。
> - Superseded BM2 Gateway PXE harness：`MAVEN_OPTS=-Xmx4G mvn test -pl premium/test-premium -Dtest=ProvisionPhysicalServerBm2Case -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true` — historical `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，但此证据已撤销为最终验收证据。
> - OSS regression after BM2 provider：`MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=PhysicalServerOpsCase -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true` — `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
> - Hardware discovery duplicate enqueue fix：`MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=TestHardwareDiscoveryScheduler -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true` — `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`。
> - BM2 focused IT after duplicate enqueue fix：`MAVEN_OPTS=-Xmx4G mvn test -pl premium/test-premium -Dtest=ProvisionPhysicalServerBm2Case -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true` — `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`；log grep confirmed no `Duplicate entry` / `ConstraintViolation` / `DataIntegrityViolation` / `Hardware discovery failed`。
>
> **What changed**:
> - Header 新增 `ProvisionProvider` / `ProvisionRequest` / `ProvisionResult` 以及 `APIProvisionPhysicalServerMsg/Event`。
> - `PhysicalServerProvisionService` 根据 `ProvisionNetwork.type` 选择 provider，并校验 server/network/pool/zone 关系。
> - `ProvisionPhysicalServerLongJob` 承载装机 API；`PhysicalServerManagerImpl` 将 direct provision API 转交 LongJob。
> - `PhysicalServerStandalonePxeProvisionProvider` 在 UT 下作为 deterministic provider 覆盖 happy path；无 provider 场景返回可诊断的 `no ProvisionProvider...` LongJob 失败。
> - `ProvisionRequest` 透传 LongJob `accountUuid`；provider 结果支持返回 `providerResourceUuid`，避免 BM2 instance uuid 只留在 provider 内部。
> - `Bm2GatewayPxeProvisionProvider` 当前接入方式是错误边界：它校验 BM2 role/chassis、要求 gateway、同步 legacy BM2 network/ref，并通过 `CreateBareMetal2InstanceMsg` 创建 BM2 Instance。下一步必须替换为 PhysicalServer-first provider。
> - `LongJobManagerImpl` 新建 LongJob 时补 `createDate/lastOpDate`，修掉同步完成 LongJob 计算 `executeTime` 的 NPE。
> - 修正 ProvisionProvider 相关 `operr` 用法，避免 LongJob jobResult 只记录 format arg。
> - `HardwareDiscoveryScheduler` 对同一 `serverUuid` 的 in-flight discovery 做 coalescing，并把 timeout/retry 调度挪到独立 scheduled executor，避免 BM2 path-two hook 和 attach hook 同时 enqueue 时双 worker 并发 insert `PhysicalServerHardwareInfoVO` 撞主键。
>
> **Remaining mainline**:
> - 未跑 broader CI / nightly；当前有效证据只覆盖 OSS physicalServer provider/no-provider/LongJob 路径。premium BM2 Gateway PXE harness 不再算最终验收。
> - `ProvisionPhysicalServerBm2Case` 为绕过当前分支 gateway host 的 KVM capacity precondition，在测试内给 gateway resourceUuid 补了最小 `PhysicalServerRoleVO(roleType=KVM_HOST)`；这是测试 harness 约束，不是生产装机逻辑。
>
> ---

> **2026-05-01 follow-on — ScanPhysicalServers GREEN, next priority is ProvisionProvider/install**
>
> 本轮按用户要求先做 `ScanPhysicalServers`，没有先碰 P2。Scan 主线已经从 `unknown message` 闭环到 focused IT GREEN；下个 session 直接做装机 / `ProvisionProvider`，不要回头重开 Scan，除非是 review 反馈。
>
> **GREEN evidence**:
> - Clean rebuild：`MAVEN_OPTS=-Xmx4G mvn clean install -pl plugin/physicalServer -am -DskipTests -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -DskipJacoco=true` — `BUILD SUCCESS`。
> - Woven artifact check：`core-5.5.0.jar` 的 `SimpleQueryImpl` constructor 已带 `ajc$tjp_17` / `Factory.makeJP`，避免 `_dbf` null 启动 NPE。
> - Scan focused IT：`PhysicalServerOpsCase` — `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
>
> **What changed**:
> - `PhysicalServerManagerImpl.handleApiMessage` 已分发 `APIScanPhysicalServersMsg`。
> - 新增 `PhysicalServerScanner`，把 IP range parsing、zone/pool 校验、credential probe、幂等 upsert 和统计字段从 manager 拆出去。
> - `CoreGlobalProperty.UNIT_TEST_ON` 下 scan probe 使用 deterministic success，focused case 覆盖 valid scan、>1024 range fail、same range idempotent。
> - Scan 发现的新机器会写 `PhysicalServerVO` OOB/IPMI 字段；同 zone/pool/managementIp 已存在时计入 `existingCount`。
> - `PhysicalServerOpsCase` 修正 scan 后清理顺序，先删 pool 内 server 再删 pool；ProvisionNetwork 创建对可选 DHCP 字段写空串，保持 API optional 语义并满足现有 NOT NULL schema。
>
> **Remaining mainline**:
> - 旧状态：ProvisionProvider / 装机当时仍未闭环；顶部 2026-05-01 correction 已撤销 BM2 Gateway harness 的 focused GREEN 口径，需按 PhysicalServer-first 计划重做。
> - Broader CI / nightly 仍未跑；不要把 focused IT GREEN 说成 release-ready。
>
> ---

> **2026-05-01 handoff — Scan was next priority, then ProvisionProvider/install**
>
> 用户确认整个 feature 最关键的 **Scan** 和 **装机 / ProvisionProvider** 还没闭环。下个 session 不要再从 P2 backlog 起手，直接做主线缺口。
>
> **Current truth**:
> - RoleProvider / ServerPool / cascade / Power / Cordon 已经有 IT 或 focused case 兜底。
> - `APIDiscoverPhysicalServerHardwareMsg` 已有 handler，属于单机硬件信息刷新，不等于 scan。
> - `APIScanPhysicalServersMsg` 已有 `PhysicalServerManagerImpl` 分发和 `PhysicalServerScanner` 实现；`PhysicalServerOpsCase` focused IT 已 GREEN。
> - 修正状态：ProvisionNetwork CRUD / attach 底座存在；OSS `ProvisionProvider` SPI / orchestration / LongJob 基础存在；BM2 Gateway PXE provider 的 focused GREEN 已撤销，需按 PhysicalServer-first 计划重做。
>
> **Uncommitted state to preserve**:
> - parent repo 有本轮 Power API / docs / tests 改动，尚未 commit / push。
> - premium submodule 有相关 test fix：`premium/test-premium/src/test/groovy/org/zstack/test/integration/baremetal2/PowerAndDiscoverPhysicalServerCase.groovy`。
> - premium 还有无关脏文件/目录：`plugin-premium/ai/src/main/java/org/zstack/ai/AIModelManagerImpl.java`、`.m2/`、`.omc/`、`zwatch/.omc/`；不要 stage / revert。
>
> **Hard rule**: 所有 Maven 命令必须带：
> `-Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository`
>
> ---

> **2026-05-01 follow-on — Power API + Cordon verification**
>
> 用户指出 Power API / Cordon story 还没收口。本轮补齐统一 Power API 的真实 OOB 路径，并重新验证 Cordon。
>
> **GREEN evidence**:
> - Clean rebuild：`MAVEN_OPTS=-Xmx4G mvn clean install -pl plugin/physicalServer -am -DskipTests -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -DskipJacoco=true` — `BUILD SUCCESS`。
> - Power IT：`PhysicalServerPowerCase` — `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
> - Cordon IT：`ContainerNodeCordonServiceCase` — `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
> - BM2 regression：`PowerAndDiscoverPhysicalServerCase` — `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
>
> **What changed**:
> - `PhysicalServerManagerImpl` 的 Power handler 现在按 PRD 的 OOB-first 语义执行：PhysicalServer 自身有 IPMI OOB 凭据时不要求角色，直接调用 `PhysicalServerIpmiPowerExecutor`。
> - PowerOff / PowerReset 在有角色时仍先问 `RoleWorkloadStatus.power*BlockReason`，保证破坏性操作被 workload gate 拦住。
> - 无 PhysicalServer OOB 但存在 BM2 role 时保留 RoleProvider fallback，兼容 BM2 把 IPMI 信息放在 roleConfig 的老数据形态。
> - 新增 `PhysicalServerPowerCase` 覆盖 powerOn / powerOff / powerReset / no-OOB error；修正 `PhysicalServerOpsCase` 的 SDK inventory 访问和 Groovy closure 参数遮蔽。
> - Cordon 代码本轮无需改动；`ContainerNodeCordonServiceCase` 已覆盖 cordon/uncordon、RBAC 降级、operator-cordoned skip、hysteresis、registry。
>
> **注意**: 这条旧注意已被 2026-05-01 Scan follow-on 关闭；`PhysicalServerOpsCase` 不再在 scan 段撞 `APIScanPhysicalServersMsg unknown message`。
>
> ---

> **2026-05-01 收尾 — #16 + cascade sibling + PhysicalServer delete cascade GREEN**
>
> 本 session 按用户指定顺序继续做：1) #16 provisionType，3) cascade sibling，2) 剩余覆盖/PRD 说明。所有验证命令继续使用 worktree-local Maven repo：
> `-Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository`。
>
> **GREEN evidence**:
> - BM2 unit `Bm2RoleProviderTest,Bm2PhysicalServerRoleCascadeExtensionTest`: `Tests run: 30, Failures: 0, Errors: 0, Skipped: 0`。
> - Container unit `ContainerRoleProviderTest,ContainerNodeInfoDiscoveryAdapterTest`: `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`。
> - BM2 IT `Bm2RoleProviderIntegrationCase`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
> - Container IT `ContainerRoleProviderIntegrationCase`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
> - Container transaction IT `ProcessNodeTransactionalCase`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
>
> **PRD integration coverage checkpoint**: RoleProvider PRD integration acceptance surface is now 20/20 AC GREEN (>=95%). This is functional AC/IT coverage, not JaCoCo line coverage; verification used `-DskipJacoco=true`.
>
> **What changed**:
> - #16: `Bm2RoleProvider.parseProvisionType` uses exact enum `valueOf(trimmed)` semantics; null/blank defaults to `Remote`, lowercase/invalid values are rejected with the BM2 module error code.
> - BM2 cascade sibling: `BareMetal2ChassisVO` hard-delete cascade now clears `PhysicalServerRoleVO(roleType=BAREMETAL_V2, roleUuid=chassisUuid)`; duplicate old chassis-package cascade class was removed.
> - Container cascade sibling: `NativeHostVO` soft-delete/hard-delete coverage now clears `PhysicalServerRoleVO(roleType=CONTAINER_HOST, roleUuid=nativeHostUuid)`.
> - Direct `APIDeletePhysicalServerMsg` no longer bypasses cascade with direct SQL/JPQL. It now calls `CascadeFacade.asyncCascade(deletion.delete|deletion.forceDelete, issuer=PhysicalServerVO, ctx=PhysicalServerInventory)`, then `deletion.cleanup`; logs confirmed child order `PhysicalServerCapacityVO -> PhysicalServerHardwareDetailVO -> PhysicalServerHardwareInfoVO -> PhysicalServerRoleVO -> PhysicalServerVO`.
> - `premium/conf/persistence.xml` now registers `PhysicalServerHardwareInfoVO`; this was required because premium IT runtime walks the full PhysicalServer cascade graph.
>
> **Remaining**: P2 backlog except P2-3, broader CI/nightly validation. Do not report JaCoCo coverage from this checkpoint.
>
> **2026-05-01 follow-on**: method-local-class audit closed. `HostAllocatorManagerImpl.CpuMemCapacity` was promoted from method-local to class-level `private static class`, matching the earlier `HostUsedCpuMem` fix shape. Runbook added at `docs/runbooks/aspectj-method-local-class-pitfall.md`.
>
> ---

> **2026-04-30 收尾 — AC-7 + #17 IT GREEN，PRD RoleProvider 集成验收覆盖 >=95%**
>
> 本 session 按用户指定顺序走：1) AC-7，3) #17，2) PRD 集成测试覆盖率。三条 RoleProvider 集成 case 都用 worktree-local Maven repo 跑绿：
> `-Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository`。
>
> **GREEN evidence**:
> - Container `ContainerRoleProviderIntegrationCase`: 7/7 AC covered；`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，113.191s。
> - KVM `KvmRoleProviderIntegrationCase`: 5/5 AC covered；`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，64.162s。
> - BM2 `Bm2RoleProviderIntegrationCase`: 7/7 AC covered；`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，109.930s。
>
> **PRD integration coverage checkpoint**: RoleProvider PRD integration acceptance surface is now 19/19 AC GREEN (>=95%). This is functional AC/IT coverage, not JaCoCo line coverage; all three verification commands used `-DskipJacoco=true`.
>
> **What changed**:
> - AC-7: Container `EXTERNAL_READONLY` attach now rejects at dispatcher while preserving module error code `ORG_ZSTACK_CONTAINER_10057`, with no `PhysicalServerRoleVO` residue.
> - #17 KVM: delete KVM Host now cascades `PhysicalServerRoleVO` cleanup through soft/hard delete extension registration; test resource `Kvm.xml` was synced with production registration.
> - #17 BM2: `BAREMETAL_V2` attach now has end-to-end IT assertion for `schedulingMode=INTERNAL_EXCLUSIVE`.
>
> **Remaining**: #16 provisionType unit-test rewrite, cascade-sibling cleanup/verification if those untracked classes are kept, P2 backlog polish, and broader CI/nightly validation. Do not report JaCoCo coverage from this checkpoint.
>
> ---
>
> **2026-04-29 收尾 — P1 follow-up batch 全 ship + #25 落地 + #11 root-cause 修**
>
> 本 session 11 个 commit（parent 8 + premium 3）一刀一刀按 plan 走完。code-reviewer Phase 3 review 出的 6 P1 全闭环 + 1 个 P0-equivalent 隐 bug 顺手修（XML 没 `<zstack:extension>`）+ 1 个 followup #25 schema/JPA/sync/adapter 全 wire + IT validation blocker #11 root-cause 修齐。
>
> **关键 commits**:
> - parent `94c53d5dc5` — **#11 root-cause** `HostUsedCpuMem` method-local class promote（AspectJ CTW + lambda invokespecial VerifyError，跟历史 `ResourceStopper.Wrap` 同模式）
> - parent `e5c2488493` + `dc9f60b18a` + premium `1eede10b2d` — **P1-2** SPI collapse to `boolean discover`，1000-host fleet sweep -5000~7000 PSR queries
> - parent `55223e470c` — **P1-6** KVM cascade via `SoftDeleteEntityExtensionPoint`（atomic via REQUIRES_NEW tx）
> - parent `61baf11a14` — **P1-3** discoverSource first-writer-wins + **P1-5b** line 845 swallow narrow
> - parent `029e929ada` — **P1-4** enqueueDiscovery hook 统一为单 autowire（顺带挖出 XML bean 没声明 `<zstack:extension>` 的生产级隐 bug）
> - parent `2fc90b60dc` + premium `c6a0335e9d` — **#25** V5.5.18 NativeHostVO 6 nodeInfo 列 + sync write + adapter consumer + 4 unit test
> - parent `24572b139d` — **P1-1** PSC negative-clamp 改整组拒绝（+2 unit test，10/12 → 12/12 GREEN）
> - parent `cd78a187c7` — **P1-5** 3 处 `catch (Throwable)` 收成 `Exception`
> - parent `07cf438848` — **#11 diagnostic doc** at `docs/blockers/2026-04-29-p11-forked-vm-crash.md`
>
> **Plan 文档完成度**：[docs/plans/2026-04-29-001-phase3-review-followups.md](../plans/2026-04-29-001-phase3-review-followups.md) — 全部 P1 + #25 + #11 移到 closed；P2 6 项 + #16/#17 仍开。（2026-04-30 更新：#11/#17 已 GREEN，#16 仍开。）
>
> **完成度评估**：编码 ~99%，dev 可跑 ~95%，release-ready ~80%（差 IT GREEN 闭环 + 1 个 cascade-sibling followup + 6 P2 polish）。（2026-04-30 更新：RoleProvider IT GREEN，PRD RoleProvider 集成验收覆盖 19/19；release 仍需 #16 + cascade-sibling cleanup/verification + P2 + broader CI。）

---

## 历史推荐顺序（保留参考）

### 1. ScanPhysicalServers — CLOSED

目标：让 `PhysicalServerOpsCase` 的 scan 段从 `unknown message` 变成可验收行为。2026-05-01 已闭环。

入口：
- API msg: `header/src/main/java/org/zstack/header/server/APIScanPhysicalServersMsg.java`
- API event: `header/src/main/java/org/zstack/header/server/APIScanPhysicalServersEvent.java`
- 失败 case: `test/src/test/groovy/org/zstack/test/integration/server/PhysicalServerOpsCase.groovy`
- 实现位置: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java` dispatch Scan，`plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerScanner.java` 承载 scanner service。

已实现：
1. `PhysicalServerManagerImpl.handleApiMessage` 加 `APIScanPhysicalServersMsg` 分发。
2. `PhysicalServerScanner` 承载 IP range / credential loop / DB upsert。
3. IP range parser 支持 single IP 和 `start-end`，上限 1024，校验 zone/pool 关系。
4. `CoreGlobalProperty.UNIT_TEST_ON` 下 probe deterministic success；非 UT 走 `ipmitool`。
5. 同 zone/pool/managementIp 已存在算 `existingCount`；新发现创建 `PhysicalServerVO`，写 OOB/IPMI 字段，返回 `discoveredServers`。
6. `PhysicalServerOpsCase` 覆盖 valid params count fields、>1024 IP fail、same range idempotent。

最小验收命令：
`MAVEN_OPTS=-Xmx4G mvn clean install -pl plugin/physicalServer -am -DskipTests -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -DskipJacoco=true`

`MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=PhysicalServerOpsCase -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true`

### 2. ProvisionProvider / 装机 — IN PROGRESS: Tasks 1–4 GREEN, Tasks 5–8 待做

目标：把 ProvisionNetwork 底座变成可复用装机能力，而不是只停留在 CRUD / attach。2026-05-01 已确认原 BM2 Gateway PXE harness 验错对象：它要求 BM2 Gateway 并创建 BM2 Instance，不符合 PhysicalServer-first。

设计参考：
- `/home/mj/zstack-workspace/cloud_prd/prd/v5.5.18-unified-hardware/provision/feat-unified_provision_network_prd.md` 2026-05-01 PhysicalServer-first 修订
- `docs/plans/2026-05-01-physical-server-first-provision-plan.md`
- `docs/STATUS.md` 的 R8 / U18-U20 修正状态

进度：
1. **Task 1 — DONE**: `ProvisionPhysicalServerBm2Case` 删 BM2 Gateway 装配，加无 `BareMetal2GatewayVO/BareMetal2InstanceVO` 负向断言；harness 先 fail（暴露 gateway 依赖），再随 Task 4 转 PASS。
2. **Task 2 — DONE**: `PhysicalServerProvisionService` 接管 PhysicalServer-level validation（network missing/disabled、zone mismatch、no pool、pool-ref、no OOB、provision NIC mismatch），不 import 任何 BM2 类；`TestPhysicalServerProvisionService` 10/10 GREEN。
3. **Task 3 — DONE**: 新增 `PhysicalServerProvisionTarget` 不可变 DTO（PhysicalServer + ProvisionNetwork + OOB + provision NIC + DHCP + OS image/kickstart），不含任何 BM2 标识字段；validation 与 target 构建覆盖在 unit harness 里。
4. **Task 4 — DONE**: `PhysicalServerGatewayPxeProvisionProvider` 替换 `Bm2GatewayPxeProvisionProvider`（旧文件已不在树里），不查 gateway/chassis、不发 `CreateBareMetal2InstanceMsg`、不返回 BM2 instance uuid；`baremetal2.xml` 只注册新 provider；premium harness 1/1 GREEN。
5. **Task 5 — TODO**: OSS `STANDALONE_PXE` 保持 fail-loud + `PhysicalServerOpsCase` 加确定性 OSS test provider，断言 provider 选型走 `ProvisionNetworkType` 且 `ProvisionRequest` 由 PhysicalServer 字段构建。
6. **Task 6 — TODO**: `docs/runbooks/physical-server-pxe-real-env-validation.md`，作为 simulator 与真机边界声明（仓里不跑真机）。
7. **Task 7 — TODO**: 把所有"BM2 Gateway PXE focused IT GREEN" 改为"PhysicalServer-first focused harness GREEN"，覆盖 `docs/brainstorms/next-session.md`、`docs/STATUS.md`、cloud_prd `feat-unified_provision_network_prd.md`；用 plan §Task 7 的 `rg` grep 校验。
8. **Task 8 — TODO**: 最终回归矩阵 + diff hygiene + PhysicalServer-first contract review。

最小验收命令（Task 1–4 已绿）：
- `MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=TestPhysicalServerProvisionService -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true`
- `MAVEN_OPTS=-Xmx4G mvn test -pl premium/test-premium -Dtest=ProvisionPhysicalServerBm2Case -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true`

### 3. Full regression / MR prep

ProvisionProvider 绿后再做：
1. 重跑 Power / Cordon focused cases，确认没有回归。
2. 做 review 前完整 diff 审核，重点看 PhysicalServer-first contract、LongJob result、failure path、OSS/premium dependency boundary。
3. 再考虑 commit / push。stage 时排除 premium 无关脏文件。

## 已关闭，不要重新开线

- RoleProvider PRD IT acceptance：20/20 AC GREEN。
- Power API：`PhysicalServerPowerCase` GREEN，BM2 fallback regression GREEN。
- Cordon core service：`ContainerNodeCordonServiceCase` GREEN。
- #16 / #17 / cascade sibling / method-local-class audit / P2 backlog 均已 closed 或不再是主线 blocker。

---

## 不要做的

- **不要直接合 master**：本 follow-up plan 已闭环；进入合并前仍需要 MR / broader CI / nightly 验证。
- **不要碰已 closed 的 P1 / #25**：6 P1 + #25 全 closed in plan doc，碰了等于回滚。
- **不要扩 carrier surface**：`UnifiedHardwareInfo` 故意只有 IPMI/KVM/K8s 三家都能填的字段。`machineID` / `allocatable*` 持久化但不上 carrier 是设计选择（见 `ContainerNodeInfoDiscoveryAdapter` javadoc）。
- **Force push / history rewrite 仍需显式确认**（CLAUDE.md 铁律 8）。

---

## Reference Documents

| 场景 | 文档 |
|---|---|
| "为什么这么设计" | `docs/decisions/` (ADR-001..011) |
| SQL/DDL 迁移踩坑 | `docs/runbooks/v5518-sql-ddl-pitfalls.md` |
| 测试环境 / 快照 | `docs/runbooks/testing-envs.md` |
| 升级失败回滚 | `docs/runbooks/v5518-unified-hardware-rollback.md` |
| **当前 P1+followup 状态** | `docs/plans/2026-04-29-001-phase3-review-followups.md` |
| **#11 IT GREEN 收尾** | `docs/blockers/2026-04-29-p11-forked-vm-crash.md` |
| 历史 entry 保留 | 见下方 |

---

> **历史 entry 保留 →**

---

> **2026-04-28 收尾 — Wave 2/3/4 全 ship + adapter fan-out + P0 fix**
>
> 本 session 18 个 commit（parent 11 + premium 7）+ 1 doc。Wave 2 (U7-followup/U8/U9) + Wave 3 (U10-U16) + Wave 3 followup adapter (U16a/b/c) + Wave 4 (U17 perf) + #10 EncryptColumnAspect 修 + P0-1 PSC overprov writer fix 全 push.
>
> Review 结果：code-reviewer 跑过，1 P0 + 6 P1 + 7 P2 + 4 followup pool。**P0-1 修在 `ce8fd4e263`，P1 batch + #25 + #11 全在 2026-04-29 session 收掉**（见上方）。

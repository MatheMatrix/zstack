# OS Install Completion Monitoring Plan (B-L2)

> **For agentic workers:** Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `APIProvisionPhysicalServerMsg` LongJob terminate **only after OS install actually completes** (or fails / times out), matching BM2 Instance state-machine behavior. Replaces the fire-and-forget semantics shipped by `2026-05-02-bm2-gateway-data-plane-wiring-plan.md`.

**Architecture:**
- LongJob `ProvisionPhysicalServerLongJob` becomes **stage-based** with phase tracking embedded in `LongJobVO.jobData`.
- `Bm2GatewayDataPlane.provision()` runs through 4 phases: `NetworkPrepared → PxeTriggered → Pinging → Done`.
- Each phase is **idempotent** so MN restart resumes from the recorded phase without re-triggering side-effecting steps (PXE boot in particular).
- "OS install complete" is detected via **gateway-agent-side ping** of the target IP, mirroring BM2's `doPingBareMetal2Instance` pattern. Gateway is on the provision network and can reach the target chassis post-install.
- **No new schema field** on `PhysicalServerVO`. `LongJobVO.jobData` is the single source of truth for in-flight provision state.
- **PXE physical dependency** explicitly written into PRD: provision network MUST have a data-plane node (gateway or standalone) running DHCP/TFTP/HTTP/agent.

**Tech Stack:** Java 8, ZStack CloudBus + LongJob framework, AspectJ weaving, JSON jobData, GlobalConfig.

---

## Source Of Truth

- Predecessor: `docs/plans/2026-05-02-bm2-gateway-data-plane-wiring-plan.md` (Tasks 1–5 GREEN)
- PRD pending update: `/home/mj/zstack-workspace/cloud_prd/prd/v5.5.18-unified-hardware/provision/feat-unified_provision_network_prd.md`
- BM2 reference behavior: `premium/baremetal2/.../instance/BareMetal2InstanceBase.java#pingBareMetal2Instance` + `doPingBareMetal2Instance`
- Maven rule: `-Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository`

## File Structure

- Modify: `header/src/main/java/org/zstack/header/server/PhysicalServerProvisionTarget.java` — no field changes; pass-through.
- Create: `header/src/main/java/org/zstack/header/server/ProvisionPhase.java` — enum `NotStarted / NetworkPrepared / PxeTriggered / Pinging / Done`.
- Modify: `plugin/physicalServer/src/main/java/org/zstack/server/ProvisionPhysicalServerLongJob.java` — read/write `phase` field in jobData; resume from recorded phase.
- Modify: `premium/baremetal2/src/main/java/org/zstack/baremetal2/server/Bm2GatewayDataPlane.java` — split `provision()` into stage-based handlers; add ping helper; add timeout.
- Create: `premium/baremetal2/src/main/java/org/zstack/baremetal2/server/Bm2GatewayPingHelper.java` — encapsulates "ask gateway agent to ping target IP" via cloud bus.
- Modify: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerGlobalConfig.java` (or wherever physical-server GlobalConfig lives) — add `physicalserver.provision.timeout` (default 1800s) + `physicalserver.provision.pingInterval` (default 30s).
- Modify: `premium/test-premium/src/test/groovy/.../ProvisionPhysicalServerBm2Case.groovy` — happy-path now requires mocked gateway + ping success path; expand IT.
- Create: `premium/baremetal2/src/test/java/org/zstack/baremetal2/server/Bm2GatewayDataPlaneStageTest.java` — unit tests for phase resume, idempotency, timeout.

## Non-Goals

- **No new field on `PhysicalServerVO`.** Phase tracking lives entirely in `LongJobVO.jobData`. Schema unchanged.
- **No BM2 agent protocol extension.** Reuse existing `PrepareProvisionNetworkInGatewayMsg` + cloud-bus-based ping flow.
- **No L3 (auto-attach Host) work.** PRD line 303 keeps that v1.1+. This plan only ensures LongJob blocks until ping succeeds; downstream `AttachRole` is still operator/API-driven.
- **No STANDALONE_PXE OSS dnsmasq+tftpd implementation.** Still v1.1+. `PhysicalServerStandalonePxeProvisionProvider` stays fail-loud / unregistered.
- Do not touch unrelated dirty files: `premium/plugin-premium/ai/.../AIModelManagerImpl.java`, `.m2/`, `.omc/`, `premium/zwatch/.omc/`.

---

### Task 1: ProvisionPhase enum + jobData phase tracking

**Files:**
- Create: `header/src/main/java/org/zstack/header/server/ProvisionPhase.java`
- Modify: `plugin/physicalServer/src/main/java/org/zstack/server/ProvisionPhysicalServerLongJob.java`

- [ ] **Step 1: Add enum**

```java
public enum ProvisionPhase {
    NotStarted,
    NetworkPrepared,
    PxeTriggered,
    Pinging,
    Done
}
```

- [ ] **Step 2: Wrap msg + phase in jobData**

`ProvisionPhysicalServerLongJob.start()`:
- Parse jobData. If it has `phase` field, use it; otherwise default `NotStarted`.
- Call `provisionService.startProvisioning(msg, accountUuid, phase, ...)` — service must accept phase.
- Sub-completion records phase transitions back to jobData via `LongJobUtils.updateJobData(jobUuid, ...)`.

- [ ] **Harness Gate:** Unit test:
  - jobData with `phase=NotStarted` → goes through full chain
  - jobData with `phase=PxeTriggered` → skips PrepareNetwork + powerOnPxe, jumps straight to Pinging
  - jobData with `phase=Done` → completion.success() immediately

- [ ] **Review Gate:** No phase mutation outside known transition map (NotStarted→NetworkPrepared→PxeTriggered→Pinging→Done). No backward jumps.

---

### Task 2: Bm2GatewayDataPlane refactor — stage-based provision

**Files:**
- Modify: `premium/baremetal2/src/main/java/org/zstack/baremetal2/server/Bm2GatewayDataPlane.java`

- [ ] **Step 1: Split provision() into stage handlers**

```java
@Override
public void provision(PhysicalServerProvisionTarget target, ProvisionPhase startPhase, Completion completion) {
    String gatewayUuid = findActiveGatewayUuid(target);
    if (gatewayUuid == null) { completion.fail(...); return; }

    runStages(target, gatewayUuid, startPhase, completion);
}

private void runStages(target, gatewayUuid, currentPhase, completion) {
    switch (currentPhase) {
        case NotStarted:
            stagePrepareNetwork(target, gatewayUuid, () -> {
                recordPhase(target, NetworkPrepared);
                runStages(target, gatewayUuid, NetworkPrepared, completion);
            }, completion::fail);
            return;
        case NetworkPrepared:
            stagePowerOnPxe(target, () -> {
                recordPhase(target, PxeTriggered);
                runStages(target, gatewayUuid, PxeTriggered, completion);
            }, completion::fail);
            return;
        case PxeTriggered:
        case Pinging:
            stagePingUntilUp(target, gatewayUuid, () -> {
                recordPhase(target, Done);
                completion.success();
            }, completion::fail);
            return;
        case Done:
            completion.success();
            return;
    }
}
```

- [ ] **Step 2: Idempotency guards**

- `stagePrepareNetwork`: idempotent on agent side (re-prepare same network is OK in BM2 agent).
- `stagePowerOnPxe`: **must not run if phase ≥ PxeTriggered**. Guard outside this method (the dispatch above ensures it).
- `stagePingUntilUp`: pure read, no side effect.

- [ ] **Step 3: recordPhase helper**

```java
private void recordPhase(target, phase) {
    LongJobUtils.updateJobData(target.getJobUuid(), data -> data.put("phase", phase));
}
```

`PhysicalServerProvisionTarget` will need a `jobUuid` field added (Tasks 1's interface change cascades here).

- [ ] **Harness Gate:** Unit test for each phase entry path; `stagePowerOnPxe` not invoked when entering at PxeTriggered/Pinging.

- [ ] **Review Gate:** No state mutation in `Bm2GatewayDataPlane` outside the recorded phase transitions. No persistent BM2 Instance VO write.

---

### Task 3: stagePingUntilUp — gateway-agent ping loop

**Files:**
- Create: `premium/baremetal2/src/main/java/org/zstack/baremetal2/server/Bm2GatewayPingHelper.java`
- Modify: `premium/baremetal2/src/main/java/org/zstack/baremetal2/server/Bm2GatewayDataPlane.java`

- [ ] **Step 1: PingHelper API**

```java
public class Bm2GatewayPingHelper {
    @Autowired private CloudBus bus;

    /**
     * Ask the gateway agent to ping target IP. Reuses BM2's PingBareMetal2InstanceMsg
     * pattern but routes via gateway's HostConstant.SERVICE_ID directly without
     * BareMetal2InstanceVO involvement.
     *
     * Returns success when target IP responds; fails on timeout.
     */
    public void pingViaGateway(String gatewayUuid, String targetIp,
                               int intervalMs, int timeoutMs, Completion completion);
}
```

Implementation pattern: send `PingViaGatewayMsg` (new) to gateway service id, expect reply with reachable=true/false. If unreachable, sleep `intervalMs`, retry until total elapsed > `timeoutMs`.

If `PingViaGatewayMsg` doesn't exist in BM2 commands, build minimal: agent endpoint that runs `ping -c 1 -W 2 <ip>` and returns 0/1.

- [ ] **Step 2: stagePingUntilUp**

```java
private void stagePingUntilUp(target, gatewayUuid, onSuccess, onFail) {
    int timeoutMs = PhysicalServerGlobalConfig.PROVISION_TIMEOUT.value(Integer.class) * 1000;
    int intervalMs = PhysicalServerGlobalConfig.PROVISION_PING_INTERVAL.value(Integer.class) * 1000;
    pingHelper.pingViaGateway(gatewayUuid, target.getManagementIp(), intervalMs, timeoutMs,
        new Completion(...) {
            @Override public void success() { onSuccess.run(); }
            @Override public void fail(ErrorCode err) { onFail.accept(err); }
        });
}
```

- [ ] **Step 3: Timeout error**

On timeout: `operr("OS install timeout: PhysicalServer[uuid:%s, ip:%s] not reachable via gateway[uuid:%s] after %ds", ...)`.

- [ ] **Harness Gate:**
  - Unit test: ping success on 1st try → onSuccess called within 1 interval
  - Unit test: ping fail → retry until timeout → onFail with timeout error
  - Unit test: ping intermittent (fail, fail, success) → onSuccess after 3 intervals

- [ ] **Review Gate:** Ping helper has no BM2 Instance VO dependency. Only takes gatewayUuid + targetIp.

---

### Task 4: GlobalConfig + helper for ProvisionPhysicalServerLongJob resume

**Files:**
- Modify: GlobalConfig file (find existing one in `plugin/physicalServer/src/main/resources/`)
- Modify: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerProvisionService.java`

- [ ] **Step 1: GlobalConfig entries**

```xml
<globalConfig category="physicalserver">
    <name>provision.timeout</name>
    <description>Maximum seconds to wait for OS install completion (ping target IP)</description>
    <defaultValue>1800</defaultValue>
    <type>Integer</type>
</globalConfig>
<globalConfig category="physicalserver">
    <name>provision.pingInterval</name>
    <description>Interval seconds between gateway-agent ping attempts</description>
    <defaultValue>30</defaultValue>
    <type>Integer</type>
</globalConfig>
```

Add Java enum `PhysicalServerGlobalConfig.PROVISION_TIMEOUT` / `PROVISION_PING_INTERVAL`.

- [ ] **Step 2: Service signature update**

`PhysicalServerProvisionService.startProvisioning(msg, accountUuid, phase, completion)` — phase is the resume entry.

- [ ] **Harness Gate:** GlobalConfig defaults verified via `mvn -Dtest=TestPhysicalServerProvisionService` GREEN.

- [ ] **Review Gate:** No hardcoded timeout/interval in code.

---

### Task 5: IT — happy-path with mocked gateway ping

**Files:**
- Modify: `premium/test-premium/src/test/groovy/org/zstack/test/integration/baremetal2/ProvisionPhysicalServerBm2Case.groovy`

- [ ] **Step 1: Restore BM2 gateway fixture (with KVM_HOST role workaround)**

Re-add the harness PhysicalServerVO + PhysicalServerRoleVO(KVM_HOST) workaround that was reverted in 2026-05-02 plan §Task 5. Use SDK `createPhysicalServer` + `createServerPool` to satisfy schema NOT NULL constraints.

- [ ] **Step 2: Mock gateway agent ping reply**

`env.afterSimulator(BareMetal2GatewayConstant.PING_VIA_GATEWAY_PATH)` (new endpoint or reuse existing) — return reachable=true after first call.

- [ ] **Step 3: Assertion**

- LongJob state == Succeeded (after ping mock returns true)
- jobResult contains `serverUuid` + `networkUuid` + `phase=Done`
- BareMetal2InstanceVO count == 0 (preserve PhysicalServer-first negative)
- prepareNetworkCmds.size() == 1 (PrepareProvisionNetwork called exactly once)
- pingCmds.size() ≥ 1 (ping invoked)

- [ ] **Step 4: Add fail-loud regression**

Second case: no gateway → fail-loud "no GATEWAY_PXE data plane available" (current behavior, regression).

- [ ] **Harness Gate:** `mvn test -pl premium/test-premium -Dtest=ProvisionPhysicalServerBm2Case` GREEN (both cases).

- [ ] **Review Gate:** Test does not depend on real network; ping is mocked. No BM2 InstanceVO created.

---

### Task 6: PRD校正

**Files:**
- Modify: `/home/mj/zstack-workspace/cloud_prd/prd/v5.5.18-unified-hardware/provision/feat-unified_provision_network_prd.md`

- [ ] **Step 1: Add 2026-05-03 修正章节 — withdraw deferred wording + add stage semantics**

Append:

```markdown
### 2026-05-03 修正 — OS install 完成监听纳入 v5.5.18 + PXE 物理依赖明文

**撤回 2026-05-02 修正章节**：

之前写「不在本 phase scope: OS install 完成监听、装完后 PhysicalServer state 自动迁移、agent 回连超时处理、kickstart 失败重试」**过于激进**，实际：

- ❌ 原措辞 deferred 的 4 项被一刀切，但「OS install 完成监听」是装机基本可用性必需。
- ✅ 仅以下 1 项确实推 v1.1+：「装完后自动注册成 KVM/Container Host」（行 303 一键编排）。

**v5.5.18 装机完成语义（B-L2 stage-based）**：

`APIProvisionPhysicalServerMsg` LongJob 的成功语义升级为：

- ✅ Succeeded = 装机网络已 prepared **且** BMC PXE boot 已触发 **且** 装完 OS 后真机可被 gateway agent 通过 provision 网络 ping 通。
- ❌ 不在 v5.5.18 scope（仍 v1.1+）：装完后自动 attach 成 KVM/Container Host（一键编排），kickstart 失败重试，agent install on target.

LongJob 内部跑 4 阶段：

```
NotStarted → NetworkPrepared → PxeTriggered → Pinging → Done
```

每阶段幂等 + jobData 持久化 phase，MN 重启后 LongJob 框架 resume 时不重复触发已完成阶段（避免 PXE 重启真机）。

**Ping 机制**：复用 BM2 `doPingBareMetal2Instance` 思路——ZStack MN 通过 cloud bus 让 gateway agent 在 provision 网络上 ping 目标 IP，因为 gateway 跟 target 同 L2 / L3 可达。

**Timeout**：默认 1800s（GlobalConfig `physicalserver.provision.timeout`），超时 LongJob Failed。

### 2026-05-03 PXE 物理依赖明文

PXE 装机协议物理上要求 provision 网络上有一个数据面服务节点跑：

- DHCP server（dnsmasq 或等价）—— L2 广播限制，必须在同网段
- TFTP server（PXE bootloader 投递）
- HTTP server（iPXE / OS image / kickstart 投递）
- agent endpoint（ZStack MN REST 控制装机配置）

**这是协议硬约束**，不是设计选择。v5.5.18 的承载形态：

- **GATEWAY_PXE**（v5.5.18 主路径）：BareMetal2GatewayVO 兼任装机服务节点（KVMHostVO 子类）。一台机器既是 KVM Host 又跑 BM2 gateway agent。
- **STANDALONE_PXE**（v1.1+ 预留）：独立 PXE 服务节点（不绑 KVM Host），ZStack 部署一个 dnsmasq+tftpd+http VM 或物理盒子。

**MN 自身不能承担数据面**：MN 通常在管理网络上，没接 provision 网络，且客户环境多 provision 网段。

**Bm2GatewayDataPlane 找不到 active gateway 时 fail-loud**，不自动 fallback STANDALONE_PXE（v1.1+ 才实装 STANDALONE_PXE provider）。
```

- [ ] **Step 2: AC 加入新条目**

```markdown
- [ ] **AC-PN-14**: APIProvisionPhysicalServerMsg LongJob 仅在装机网络 prepared + BMC PXE 触发 + gateway-agent ping target IP 通后才返 Succeeded
- [ ] **AC-PN-15**: LongJob 在 MN 重启后从已记录的 phase resume，不重复触发 PXE boot
- [ ] **AC-PN-16**: 装机超时（default 1800s 无 ping 通）LongJob 返 Failed with operr 含 "OS install timeout"
- [ ] **AC-PN-17**: provision 网络无 active GATEWAY_PXE data plane 节点时 LongJob fail-loud "no GATEWAY_PXE data plane available"，**不**自动 fallback STANDALONE_PXE
```

- [ ] **Step 3: cloud_prd commit + push myfork**

```bash
cd /home/mj/zstack-workspace/cloud_prd
git add prd/v5.5.18-unified-hardware/provision/feat-unified_provision_network_prd.md
zcommit doc prd "" "OS install monitoring + PXE physical dependency" \
  $'B-L2 stage-based provisioning: LongJob waits for gateway-agent\nping success before Succeeded. PXE physical dependency clause\nadded — provision network must host DHCP/TFTP/HTTP/agent.'
git push myfork prd-sync-latest
```

- [ ] **Harness Gate:** Grep 验证 PRD 不再包含「不在本 phase scope: OS install 完成监听」之类字样（应只剩 v1.1+ 自动 attach 的措辞）。

- [ ] **Review Gate:** PRD 措辞分清「装机网络 prepared」「BMC PXE 触发」「OS 装完起来 ping 通」「自动 attach 成 Host」四个层级，明示 v5.5.18 cover 前 3，第 4 推 v1.1+。

---

### Task 7: docs/STATUS.md + docs/brainstorms/next-session.md 更新

**Files:**
- Modify: `docs/STATUS.md`
- Modify: `docs/brainstorms/next-session.md`

- [ ] **STATUS.md**:
  - bump §Last updated 到 2026-05-03 (B-L2 stage-based provisioning GREEN)
  - §4.1 R8 状态 ⚠️ MOSTLY DONE → ✅ DONE
  - §4.2 U18-U20 ⚠️ MOSTLY DONE → ✅ DONE
  - §5 ❌3 行 → 移到 ✅ 列表，备注「LongJob waits for ping; v1.1+ scope = auto-attach Host」

- [ ] **next-session.md**: 顶部加 2026-05-03 follow-on 描述 stage-based 实装 + ping helper + GlobalConfig + IT happy-path

---

### Task 8: Final regression matrix + commit/push

**Files:** No new code; verify + commit.

- [ ] **Step 1: Run focused regression**

```bash
MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=TestPhysicalServerProvisionService -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=TestPhysicalServerIpmiPowerExecutor -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=PhysicalServerOpsCase -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
MAVEN_OPTS=-Xmx4G mvn test -pl premium/baremetal2 -Dtest=Bm2GatewayDataPlaneTest,Bm2GatewayDataPlaneStageTest -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
MAVEN_OPTS=-Xmx4G mvn test -pl premium/test-premium -Dtest=ProvisionPhysicalServerBm2Case -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
```

- [ ] **Step 2: Diff hygiene + commit + push**

Parent commit: `<feature>[server]: stage-based ProvisionPhysicalServer LongJob`
Premium commit: `<feature>[baremetal2]: Bm2GatewayDataPlane stage-based + ping helper`
Push origin (jin.ma fork) feature/unifi-host-dev.

- [ ] **Harness Gate:** All focused tests GREEN; no schema migration; no PhysicalServerVO field change.

- [ ] **Review Gate:** Phase tracking lives in jobData only. Idempotency holds for MN restart mid-phase. Ping helper does not depend on BareMetal2InstanceVO.

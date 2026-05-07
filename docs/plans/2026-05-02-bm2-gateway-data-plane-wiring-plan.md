# BM2 Gateway Data-Plane Wiring Plan

> **For agentic workers:** Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire a real `PhysicalServerProvisionDataPlane` implementation for `GATEWAY_PXE` so that `APIProvisionPhysicalServerMsg` actually triggers PXE provisioning through the existing BareMetal2 gateway agent, without requiring `BareMetal2GatewayVO` as a precondition.

**Architecture:**
- `PhysicalServerVO` is the source of truth.
- `BareMetal2GatewayVO` is a **data-plane endpoint provider**, not a装机 precondition. If the cluster has an active gateway, use it; if not, fail-loud `no GATEWAY_PXE data plane available`. Auto-fallback to STANDALONE_PXE is **out of scope**.
- This phase ships **fire-and-forget装机**: success = network prepared on gateway + BMC PXE boot triggered. OS install completion validation belongs to `docs/runbooks/physical-server-pxe-real-env-validation.md`.
- Reuses existing BM2 agent protocol (`PrepareProvisionNetworkInGatewayMsg` + `BareMetal2Gateway.prepareProvisionNetwork()` flow chain + `BareMetal2GatewayCommands` agent commands). No new agent endpoint.

**Tech Stack:** Java 8, Spring XML extension registration, ZStack CloudBus, AspectJ weaving, `ipmitool`.

---

## Source Of Truth

- PRD: `/home/mj/zstack-workspace/cloud_prd/prd/v5.5.18-unified-hardware/provision/feat-unified_provision_network_prd.md` — pending Task 4 校正 to add data-plane endpoint clause.
- Predecessor plan: `docs/plans/2026-05-01-physical-server-first-provision-plan.md` (Tasks 1–8 GREEN).
- Maven rule for every command: `-Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository`.

## File Structure

- Modify: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerIpmiPowerExecutor.java` — add `powerOnPxe(server, completion)`.
- Create: `premium/baremetal2/src/main/java/org/zstack/baremetal2/server/Bm2GatewayDataPlane.java` — implements `PhysicalServerProvisionDataPlane` with `getType() == GATEWAY_PXE`.
- Modify: `premium/conf/springConfigXml/baremetal2.xml` — register `Bm2GatewayDataPlane` as `<bean>` + `<zstack:plugin><zstack:extension/>`.
- Modify: `premium/test-premium/src/test/groovy/org/zstack/test/integration/baremetal2/ProvisionPhysicalServerBm2Case.groovy` — add real BM2 gateway fixture and assert agent message dispatch (no BM2 Instance creation).

## Non-Goals

- Do not create `BareMetal2InstanceVO` from装机 path.
- Do not require BM2 Gateway as装机 precondition (still allowed as data-plane endpoint).
- Do not implement OS install completion polling. Real PXE install is validated through `physical-server-pxe-real-env-validation.md`.
- Do not implement STANDALONE_PXE auto-fallback. STANDALONE_PXE provider remains fail-loud / unregistered.
- Do not extend BareMetal2 agent protocol. Reuse existing `PrepareProvisionNetworkInGatewayMsg` only.

---

### Task 1: Add IPMI PXE boot trigger to PhysicalServerIpmiPowerExecutor

**Files:**
- Modify: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerIpmiPowerExecutor.java`
- Test: `test/src/test/java/org/zstack/test/server/TestPhysicalServerProvisionService.java` (or new unit test)

- [ ] **Step 1: Add `powerOnPxe` method**

Implementation must:
- Call `ipmitool ... chassis bootdev pxe options=efiboot` (or `pxe`) to set one-shot PXE boot.
- Then call `chassis power reset` (if currently on) or `chassis power on` (if currently off). Use `power reset` as default since BMC may already have OS booted.
- In `CoreGlobalProperty.UNIT_TEST_ON`, return success without invoking shell.
- Validate OOB credentials before any IPMI call (reuse `validate()`).

Suggested signature:
```java
public void powerOnPxe(PhysicalServerVO server, Completion completion) { ... }
```

- [ ] **Step 2: Unit harness**

Add a JUnit case asserting:
- `powerOnPxe` calls `validate()` and fails with same error code when OOB missing.
- In `UNIT_TEST_ON`, returns success without shelling.

Run:
```bash
MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=TestPhysicalServerProvisionService -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
```

- [ ] **Harness Gate:** new method covered by unit test, no IPMI shell call when `UNIT_TEST_ON`.

- [ ] **Review Gate:** does not change `powerOn/powerOff/powerReset` semantics; no boot-order persistence beyond one-shot.

---

### Task 2: Implement Bm2GatewayDataPlane

**Files:**
- Create: `premium/baremetal2/src/main/java/org/zstack/baremetal2/server/Bm2GatewayDataPlane.java`

- [ ] **Step 1: Class skeleton**

```java
public class Bm2GatewayDataPlane implements PhysicalServerProvisionDataPlane {
    @Autowired private DatabaseFacade dbf;
    @Autowired private CloudBus bus;
    @Autowired private PhysicalServerIpmiPowerExecutor ipmiPowerExecutor;

    @Override public ProvisionNetworkType getType() { return ProvisionNetworkType.GATEWAY_PXE; }

    @Override public void provision(PhysicalServerProvisionTarget target, Completion completion) { ... }
}
```

- [ ] **Step 2: Lookup data-plane endpoint (NOT precondition)**

Inside `provision`:
- Find any active `BareMetal2GatewayVO` whose `clusterUuid` is attached to the same `ServerPool` as the target's server. Pick one (deterministic — order by `createDate`).
- If 0 active gateway → `completion.fail(operr("no GATEWAY_PXE data plane available for server[uuid:%s]: no active BareMetal2 gateway in pool's clusters", target.getServerUuid()))`.
- This lookup must NOT require server's prior association with gateway. The lookup is to find an agent endpoint, not enforce a relationship.

- [ ] **Step 3: Build BareMetal2ProvisionNetworkInventory transiently from target**

Construct a transient `BareMetal2ProvisionNetworkInventory` (do not persist) populated from:
- `uuid` = `target.getNetworkUuid()`
- DHCP fields from `target.getDhcp*()`
- `name` = "physical-server-provision-" + serverUuid (synthesized)

If fields missing (nic mac, dhcp interface, etc.), use `Bm2GatewayDataPlane`'s validation — the gateway agent will reject malformed input.

- [ ] **Step 4: Send `PrepareProvisionNetworkInGatewayMsg` and await reply**

```java
PrepareProvisionNetworkInGatewayMsg pmsg = new PrepareProvisionNetworkInGatewayMsg();
pmsg.setGatewayUuid(gatewayUuid);
pmsg.setNetworkInventory(transientNetworkInventory);
bus.makeTargetServiceIdByResourceUuid(pmsg, HostConstant.SERVICE_ID, gatewayUuid);
bus.send(pmsg, new CloudBusCallBack(completion) {
    public void run(MessageReply reply) {
        if (!reply.isSuccess()) { completion.fail(reply.getError()); return; }
        // Step 5
    }
});
```

- [ ] **Step 5: Trigger BMC PXE boot via PhysicalServerIpmiPowerExecutor.powerOnPxe**

After prepare succeeds:
- Load `PhysicalServerVO` by `target.getServerUuid()`.
- Call `ipmiPowerExecutor.powerOnPxe(server, completion)`.

- [ ] **Step 6: Fire-and-forget completion**

`powerOnPxe` success → `completion.success()`. No polling for OS install. Document in javadoc that real install validation is the runbook's job.

- [ ] **Harness Gate:** Bm2GatewayDataPlane has no `BareMetal2ChassisVO`/`BareMetal2InstanceVO` access, no `CreateBareMetal2InstanceMsg`, no `BareMetal2InstanceVO` write. Validate via `rg "BareMetal2Chassis|BareMetal2Instance|CreateBareMetal2Instance" premium/baremetal2/src/main/java/org/zstack/baremetal2/server/Bm2GatewayDataPlane.java` — must be 0 matches.

- [ ] **Review Gate:** lookup logic is endpoint-only. The class never asserts the server is "owned" by a gateway, never persists gateway-server relationship.

---

### Task 3: Spring XML registration + IT enhancement

**Files:**
- Modify: `premium/conf/springConfigXml/baremetal2.xml` — register `Bm2GatewayDataPlane`.
- Modify: `premium/test-premium/src/test/groovy/org/zstack/test/integration/baremetal2/ProvisionPhysicalServerBm2Case.groovy`

- [ ] **Step 1: XML registration**

Append to `premium/conf/springConfigXml/baremetal2.xml`:
```xml
<bean id="Bm2GatewayDataPlane" class="org.zstack.baremetal2.server.Bm2GatewayDataPlane">
    <zstack:plugin>
        <zstack:extension interface="org.zstack.header.server.PhysicalServerProvisionDataPlane"/>
    </zstack:plugin>
</bean>
```

- [ ] **Step 2: IT — add real BM2 gateway fixture and assert agent message dispatch**

In `ProvisionPhysicalServerBm2Case`:
- Add a `BareMetal2GatewayVO` fixture (real one, attached to same cluster as server's pool). Note: the negative assertion "no `BareMetal2GatewayVO`" is now dropped; replace with positive assertion that the agent received `PrepareProvisionNetworkInGatewayMsg`.
- Mock the BM2 gateway agent simulator's `PrepareProvisionNetworkInGatewayCmd` handler (search testlib-premium for existing simulator patterns).
- Assert:
  - LongJob `Succeeded`
  - jobResult contains `serverUuid` and `networkUuid`
  - **No** `BareMetal2InstanceVO` created (preserve PhysicalServer-first negative assertion)
  - **No** `CreateBareMetal2InstanceMsg` sent

- [ ] **Step 3: Run premium harness**

```bash
MAVEN_OPTS=-Xmx4G mvn test -pl premium/test-premium -Dtest=ProvisionPhysicalServerBm2Case -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
```

- [ ] **Harness Gate:** `BareMetal2InstanceVO` count before == after == 0; gateway agent received exactly one `PrepareProvisionNetworkInGatewayCmd`.

- [ ] **Review Gate:** OSS test (`PhysicalServerOpsCase`) still uses `TestGatewayPxeProvisionProvider` — premium-only IT now validates the real wiring without OSS contamination.

---

### Task 4: PRD校正 + STATUS.md update + cloud_prd commit

**Files:**
- Modify: `/home/mj/zstack-workspace/cloud_prd/prd/v5.5.18-unified-hardware/provision/feat-unified_provision_network_prd.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/brainstorms/next-session.md`

- [ ] **Step 1: PRD校正**

In `feat-unified_provision_network_prd.md` append a `### 2026-05-02 修正 — BM2 Gateway 角色澄清`:

> **BareMetal2 Gateway 在 PhysicalServer-first 装机模型下的角色**:
> - ❌ 不是装机前置资源：装 PhysicalServer 不要求 cluster 有 BM2 Gateway。
> - ✅ 是 GATEWAY_PXE provider 的 data-plane endpoint 实现：当 ProvisionNetwork.type=GATEWAY_PXE 时，`Bm2GatewayDataPlane` 在 server 所在 pool 的 cluster 中查找 active gateway，作为 agent 通信端点。
> - 📌 fallback 策略：`Bm2GatewayDataPlane` 找不到 active gateway 时 fail-loud `no GATEWAY_PXE data plane available`。**不**自动 fallback 到 STANDALONE_PXE。STANDALONE_PXE 路径独立 provider（OSS-only，本 phase 仍为 unregistered placeholder）。
>
> **本 phase 装机语义（fire-and-forget）**:
> - ✅ 成功 = ProvisionNetwork 在 gateway 上 prepared (DHCP/iPXE) + BMC PXE boot 已触发。
> - ❌ 不在本 phase scope：OS install 完成监听、装完后 PhysicalServer state 自动迁移、agent 回连超时处理。
> - 📌 真机端到端验证由 `docs/runbooks/physical-server-pxe-real-env-validation.md` 独立执行。

- [ ] **Step 2: STATUS.md 校正**

`§Last updated`: bump 到 `2026-05-02 (BM2 Gateway data-plane wiring GREEN)`.

`§4.1 R8`: 备注追加「2026-05-02：`Bm2GatewayDataPlane` 实装，GATEWAY_PXE 装机 fire-and-forget GREEN；OS install 完成监听仍 deferred 真机 runbook」。

`§4.2 U18-U20`: 状态 `⚠️ PARTIAL` → `⚠️ MOSTLY DONE`，备注「contract + GATEWAY_PXE data-plane wiring GREEN；OS install 监听 + STANDALONE_PXE dnsmasq stack 仍 deferred」。

`§5 ❌3`: 改为「ProvisionProvider OS install 完成监听 + STANDALONE_PXE dnsmasq stack 仍 deferred 真机 runbook + 后续 U-unit」。

- [ ] **Step 3: next-session.md 顶部 follow-on**

Add a new top-level entry `2026-05-02 follow-on — Bm2GatewayDataPlane GREEN, GATEWAY_PXE 装机 fire-and-forget 闭环`. Include:
- GREEN evidence (premium harness + unit test)
- What changed (3 files: PhysicalServerIpmiPowerExecutor, Bm2GatewayDataPlane, baremetal2.xml + IT)
- Followup F1–F9 状态更新（F3 收口；OS install 监听重排成 P3 deferred）

- [ ] **Step 4: cloud_prd commit + push**

```bash
cd /home/mj/zstack-workspace/cloud_prd
git add prd/v5.5.18-unified-hardware/provision/feat-unified_provision_network_prd.md
# zcommit doc with PhysicalServer-first + Bm2 gateway endpoint clarification
git push myfork prd-sync-latest
```

- [ ] **Harness Gate:** grep cloud_prd PRD 不再有「装 BM2 Gateway 才能装 PhysicalServer」措辞。

- [ ] **Review Gate:** STATUS.md 区分「契约 GREEN」「data-plane fire-and-forget GREEN」「真装机端到端 GREEN（runbook only）」三个层级。

---

### Task 5: Final regression + parent/premium commit + push

**Files:**
- No new files. Stage modified + new from Tasks 1–3.

- [ ] **Step 1: Focused regression matrix（每条 mvn 单 test，IT framework 同 fork 跑 2 个会崩 forked-VM）**

```bash
MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=TestPhysicalServerProvisionService -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=PhysicalServerOpsCase -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
MAVEN_OPTS=-Xmx4G mvn test -pl premium/test-premium -Dtest=ProvisionPhysicalServerBm2Case -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
```

- [ ] **Step 2: Diff hygiene**

```bash
git diff --check
git status --short
git -C premium diff --check
git -C premium status --short
```

- [ ] **Step 3: Commit + push**

Parent: zcommit feature[server] "Bm2 gateway data-plane wiring (powerOnPxe)" — STATUS / next-session / IpmiPowerExecutor / new plan / unit test.

Premium: zcommit feature[baremetal2] "Bm2GatewayDataPlane impl" — Bm2GatewayDataPlane.java + baremetal2.xml + ProvisionPhysicalServerBm2Case 改写。

Push 到 origin/feature/unifi-host-dev (parent + premium 都是 jin.ma fork).

- [ ] **Harness Gate:** all focused tests GREEN; no `BareMetal2InstanceVO` write from装机 path; gateway lookup is endpoint-only.

- [ ] **Review Gate:** premium harness asserts the actual agent message dispatch, not stub success. 装机 fire-and-forget 边界明确写在 STATUS.md / next-session.md / cloud_prd PRD。

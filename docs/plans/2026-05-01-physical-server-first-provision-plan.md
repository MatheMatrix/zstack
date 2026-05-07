# PhysicalServer-first Provision Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `APIProvisionPhysicalServerMsg` install from `PhysicalServerVO` as the source of truth, without requiring BM2 Chassis, BM2 Gateway, or BM2 Instance as pre-existing resources.

**Architecture:** The unified physical server layer owns orchestration, validation, LongJob state, OOB/IPMI power control, and PhysicalServer provision result. The premium BM2 module may contribute reusable PXE data-plane command builders or agent protocol adapters, but it must not force the PhysicalServer flow through `BareMetal2GatewayVO` or `CreateBareMetal2InstanceMsg`.

**Tech Stack:** Java 8, Spring XML extension registration, ZStack CloudBus/LongJob, Groovy integration tests, JUnit unit tests, premium BM2 testlib simulators.

---

## Source Of Truth

- PRD updated: `/home/mj/zstack-workspace/cloud_prd/prd/v5.5.18-unified-hardware/provision/feat-unified_provision_network_prd.md`
- Current incorrect implementation:
  - `premium/baremetal2/src/main/java/org/zstack/baremetal2/server/Bm2GatewayPxeProvisionProvider.java`
  - `premium/test-premium/src/test/groovy/org/zstack/test/integration/baremetal2/ProvisionPhysicalServerBm2Case.groovy`
- Maven rule for every command:
  - `-Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository`

## File Structure

- Modify: `header/src/main/java/org/zstack/header/server/ProvisionRequest.java`
  - Carry only PhysicalServer-first install inputs. Add fields only if implementation needs explicit provision target metadata.
- Modify: `header/src/main/java/org/zstack/header/server/ProvisionResult.java`
  - Return PhysicalServer provision result, not BM2 instance identity.
- Modify: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerProvisionService.java`
  - Own validation for server/network/pool/OOB/provision NIC and provider selection.
- Modify/Create: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerProvisionTarget.java`
  - If needed, immutable DTO built from PhysicalServer, hardware discovery, network, and request.
- Modify/Create: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerProvisionDataPlane.java`
  - Generic interface for PXE data-plane operations used by provider code.
- Replace: `premium/baremetal2/src/main/java/org/zstack/baremetal2/server/Bm2GatewayPxeProvisionProvider.java`
  - Rename or replace with a PhysicalServer-first provider. It must not query `BareMetal2GatewayVO` or send `CreateBareMetal2InstanceMsg`.
- Modify: `premium/conf/springConfigXml/baremetal2.xml`
  - Register the corrected provider and any premium data-plane adapter.
- Modify: `premium/test-premium/src/test/groovy/org/zstack/test/integration/baremetal2/ProvisionPhysicalServerBm2Case.groovy`
  - Replace BM2 Gateway harness with PhysicalServer-first harness.
- Modify: `test/src/test/groovy/org/zstack/test/integration/server/PhysicalServerOpsCase.groovy`
  - Keep OSS provider/no-provider LongJob coverage honest.
- Create: `test/src/test/java/org/zstack/test/server/TestPhysicalServerProvisionService.java`
  - Fast unit coverage for validation and provider dispatch.
- Update: `docs/brainstorms/next-session.md`, `docs/STATUS.md`
  - Remove “BM2 Gateway PXE focused GREEN” as feature acceptance.

## Non-Goals

- Do not create `BareMetal2InstanceVO` during `APIProvisionPhysicalServerMsg`.
- Do not require `BareMetal2GatewayVO`, `BareMetal2ChassisVO`, or BM2 cluster attachment for PhysicalServer provision.
- Do not implement full real PXE boot validation in unit harness. Real hardware validation remains a separate environment test.
- Do not touch unrelated dirty files: `premium/plugin-premium/ai/src/main/java/org/zstack/ai/AIModelManagerImpl.java`, `.m2/`, `.omc/`, `premium/zwatch/.omc/`.

---

### Task 1: Lock The Broken Assumption With A Failing Harness

**Files:**
- Modify: `premium/test-premium/src/test/groovy/org/zstack/test/integration/baremetal2/ProvisionPhysicalServerBm2Case.groovy`
- Test: `premium/test-premium/src/test/groovy/org/zstack/test/integration/baremetal2/ProvisionPhysicalServerBm2Case.groovy`

- [ ] **Step 1: Remove BM2 Gateway setup from the harness**

Delete the `createGatewayForTest(...)` call and the helper-created `BareMetal2GatewayVO` dependency from `ProvisionPhysicalServerBm2Case`. Keep PhysicalServer, ServerPool, unified ProvisionNetwork, BM2 role, chassis compatibility record, and hardware info only if the current role adapter still needs them.

- [ ] **Step 2: Add negative assertions**

Assert the test setup contains no `BareMetal2GatewayVO` and no `BareMetal2InstanceVO` before provision starts. After provision succeeds, assert no `BareMetal2InstanceVO` was created by the PhysicalServer provision API.

- [ ] **Step 3: Run harness and capture expected failure**

Run:

```bash
MAVEN_OPTS=-Xmx4G mvn test -pl premium/test-premium -Dtest=ProvisionPhysicalServerBm2Case -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
```

Expected before implementation: FAIL with a message equivalent to `no connected BareMetal2 gateway found` or an assertion showing BM2 Gateway was required.

- [ ] **Harness Gate**

The failing harness must prove the current implementation requires BM2 Gateway. Do not proceed if the failure is a compile error or unrelated environment issue.

- [ ] **Review Gate**

Review the harness diff before implementation. Confirm it tests PhysicalServer-first semantics:
- no `addBareMetal2Gateway`
- no `BareMetal2GatewayVO` fixture
- no expectation that `CreateBareMetal2InstanceMsg` runs
- explicit assertion that `APIProvisionPhysicalServerMsg` is the user-facing entry

---

### Task 2: Move PhysicalServer Validation Into The Unified Service

**Files:**
- Modify: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerProvisionService.java`
- Create: `test/src/test/java/org/zstack/test/server/TestPhysicalServerProvisionService.java`

- [ ] **Step 1: Write unit tests for validation**

Cover these cases:
- network missing: fail with `ProvisionNetwork[...] not found`
- network disabled: fail with `not Enabled`
- network zone differs from server zone: fail with zone mismatch
- server has no pool: fail with `not assigned to any ServerPool`
- network not attached to server pool: fail with pool-ref message
- missing OOB for PXE provision: fail with `PhysicalServer[...] has no OOB/IPMI credentials for PXE provision`
- provided provision NIC MAC is not present in discovered hardware: fail with explicit `provision NIC` error

- [ ] **Step 2: Implement minimal validation**

Keep provider-specific checks out of this service. This service should build a valid `ProvisionRequest` only after PhysicalServer-level invariants are true.

- [ ] **Step 3: Run unit harness**

Run:

```bash
MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=TestPhysicalServerProvisionService -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
```

Expected: all validation tests pass.

- [ ] **Harness Gate**

Unit harness must fail before implementation and pass after implementation. It must not depend on premium BM2 classes.

- [ ] **Review Gate**

Review `PhysicalServerProvisionService` for dependency direction. It must not import BM2 classes and must not query `BareMetal2*VO`.

---

### Task 3: Define A Generic PhysicalServer PXE Target

**Files:**
- Create: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerProvisionTarget.java`
- Modify: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerProvisionService.java`
- Test: `test/src/test/java/org/zstack/test/server/TestPhysicalServerProvisionService.java`

- [ ] **Step 1: Add target-building tests**

Cover:
- target uses `PhysicalServerVO.uuid`
- target uses `PhysicalServerVO.oobAddress/oobPort/oobUsername/oobPassword`
- target uses `PhysicalServerProvisionNetworkVO` DHCP fields
- target resolves `provisionNicMac` from request when present
- target falls back to hardware-discovered primary provision NIC when request MAC is absent
- target carries `osImageUuid`, `osDistribution`, and `kickstartTemplate`

- [ ] **Step 2: Implement target DTO**

The DTO should contain only PhysicalServer-first fields:
- `serverUuid`
- `networkUuid`
- `managementIp`
- `oobAddress/oobPort/oobUsername/oobPassword`
- `provisionNicMac`
- `dhcpInterface`
- `dhcpRangeStartIp`
- `dhcpRangeEndIp`
- `dhcpRangeNetmask`
- `dhcpRangeGateway`
- `osImageUuid`
- `osDistribution`
- `kickstartTemplate`
- `customParams`

- [ ] **Step 3: Run unit harness**

Run:

```bash
MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=TestPhysicalServerProvisionService -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
```

Expected: target-building tests pass.

- [ ] **Harness Gate**

The target tests must assert no BM2 identity fields are present: no chassisUuid, gatewayUuid, bmInstanceUuid, chassisOfferingUuid.

- [ ] **Review Gate**

Review DTO naming and ownership. If a field is only meaningful to BM2, it does not belong in this DTO.

---

### Task 4: Replace BM2 Gateway Provider With PhysicalServer-first Provider

**Files:**
- Replace: `premium/baremetal2/src/main/java/org/zstack/baremetal2/server/Bm2GatewayPxeProvisionProvider.java`
- Modify: `premium/conf/springConfigXml/baremetal2.xml`
- Test: `premium/test-premium/src/test/groovy/org/zstack/test/integration/baremetal2/ProvisionPhysicalServerBm2Case.groovy`

- [ ] **Step 1: Rename implementation intent**

Rename the provider class to reflect the new boundary, for example `PhysicalServerGatewayPxeProvisionProvider`. Keep package placement in premium if it reuses premium BM2 agent protocol classes.

- [ ] **Step 2: Remove forbidden dependencies**

Provider implementation must not:
- query `BareMetal2GatewayVO`
- query `BareMetal2ChassisVO` as a precondition
- send `CreateBareMetal2InstanceMsg`
- create `BareMetal2ProvisionNetworkVO` as the owner of the unified operation
- return `BareMetal2InstanceVO.uuid` as `providerResourceUuid`

- [ ] **Step 3: Add a generic data-plane adapter seam**

Introduce a small adapter boundary so the harness can capture generated PXE data without real hardware:
- input: `PhysicalServerProvisionTarget`
- operation: prepare network/config
- operation: trigger boot or request IPMI executor
- output: provider result for the PhysicalServer LongJob

- [ ] **Step 4: Run premium harness**

Run:

```bash
MAVEN_OPTS=-Xmx4G mvn test -pl premium/test-premium -Dtest=ProvisionPhysicalServerBm2Case -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
```

Expected: PASS with no BM2 Gateway fixture and no BM2 Instance creation.

- [ ] **Harness Gate**

The harness must prove:
- `APIProvisionPhysicalServerMsg` succeeds with only PhysicalServer + ProvisionNetwork + OOB + hardware info
- no `BareMetal2GatewayVO` exists
- no `BareMetal2InstanceVO` is created
- captured PXE payload uses PhysicalServer OOB/provision NIC/network fields

- [ ] **Review Gate**

Run a focused review on the provider diff. Required review questions:
- Is PhysicalServer the source of truth?
- Are any BM2 resource-model assumptions left?
- Are command payloads generic enough to be reused without BM2 Gateway?
- Does LongJob failure expose actionable errors?

---

### Task 5: Keep OSS Provider And No-provider Paths Honest

**Files:**
- Modify: `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerStandalonePxeProvisionProvider.java`
- Modify: `test/src/test/groovy/org/zstack/test/integration/server/PhysicalServerOpsCase.groovy`

- [ ] **Step 1: Add OSS no-provider regression**

Ensure `STANDALONE_PXE` remains fail-loud with `reserved and not implemented yet`, and no LongJob result pretends provisioning happened.

- [ ] **Step 2: Add provider-selection regression**

Use a deterministic OSS test provider in test scope only. Assert provider selection is by `ProvisionNetworkType` and receives a `ProvisionRequest` based on PhysicalServer fields.

- [ ] **Step 3: Run OSS harness**

Run:

```bash
MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=PhysicalServerOpsCase -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
```

Expected: PASS.

- [ ] **Harness Gate**

OSS harness must not import premium BM2 classes and must not require premium test fixtures.

- [ ] **Review Gate**

Review that OSS code remains generic. The physicalServer plugin must not depend on premium BM2 packages.

---

### Task 6: Add Real-environment Harness Definition

**Files:**
- Create: `docs/runbooks/physical-server-pxe-real-env-validation.md`
- Modify: `docs/brainstorms/next-session.md`

- [ ] **Step 1: Document required real environment**

The runbook must list:
- one PhysicalServer with reachable BMC/IPMI
- provision network L2 reachability
- PXE data-plane node or agent endpoint used by the generic provider
- OS image/kickstart inputs
- expected DHCP/iPXE traffic
- expected LongJob state transitions

- [ ] **Step 2: Document pass/fail evidence**

Evidence must include:
- API transcript for creating/scanning PhysicalServer
- hardware discovery output showing provision NIC
- LongJob uuid and final state
- PXE data-plane logs
- BMC power-cycle logs
- installed OS reachability check

- [ ] **Harness Gate**

The runbook must be precise enough that QA can execute it without reading implementation code.

- [ ] **Review Gate**

Review the runbook with a harness mindset: it must distinguish simulator proof from real PXE installation proof.

---

### Task 7: Documentation And Status Correction

**Files:**
- Modify: `docs/brainstorms/next-session.md`
- Modify: `docs/STATUS.md`
- Modify: `/home/mj/zstack-workspace/cloud_prd/prd/v5.5.18-unified-hardware/provision/feat-unified_provision_network_prd.md`

- [ ] **Step 1: Revoke stale GREEN wording**

Replace “BM2 Gateway PXE focused IT GREEN” with “previous BM2 Gateway harness invalid for PhysicalServer-first acceptance” until the corrected harness passes.

- [ ] **Step 2: Add new acceptance status**

Only mark ProvisionProvider focused GREEN after Task 4 and Task 5 pass.

- [ ] **Step 3: Run doc grep**

Run:

```bash
rg -n "BM2 Gateway PXE focused IT GREEN|Bm2GatewayPxeProvisionProvider|BareMetal2GatewayVO.*PhysicalServer|CreateBareMetal2InstanceMsg.*PhysicalServer" docs /home/mj/zstack-workspace/cloud_prd/prd/v5.5.18-unified-hardware/provision
```

Expected: no stale acceptance claim remains. References to old code are allowed only in “incorrect implementation” or “must remove” contexts.

- [ ] **Harness Gate**

Docs must match actual tested behavior. No doc may claim real PXE installation unless a real environment runbook has been executed.

- [ ] **Review Gate**

Review docs for overclaiming:
- focused simulator harness
- no-provider OSS harness
- real PXE environment validation
- broader CI/nightly

---

### Task 8: Final Regression Matrix And Review

**Files:**
- No new implementation files.
- Update docs only if a result changes.

- [ ] **Step 1: Run focused regression matrix**

Run:

```bash
MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=TestPhysicalServerProvisionService,TestHardwareDiscoveryScheduler -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
```

Run:

```bash
MAVEN_OPTS=-Xmx4G mvn test -pl test -Dtest=PhysicalServerOpsCase,PhysicalServerPowerCase -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
```

Run:

```bash
MAVEN_OPTS=-Xmx4G mvn test -pl premium/test-premium -Dtest=ProvisionPhysicalServerBm2Case,PowerAndDiscoverPhysicalServerCase -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true
```

- [ ] **Step 2: Run diff hygiene**

Run:

```bash
git diff --check
git status --short
git -C premium status --short
```

- [ ] **Harness Gate**

All focused tests must pass. If a test is skipped or blocked, record the reason in `docs/brainstorms/next-session.md`.

- [ ] **Review Gate**

Run a final review over the complete diff before push/MR. Required review scope:
- no forbidden BM2 resource preconditions
- no BM2 instance creation from PhysicalServer provision
- no accidental dependency from OSS plugin to premium BM2
- LongJob error messages are actionable
- harnesses assert the PhysicalServer-first contract, not just happy-path success


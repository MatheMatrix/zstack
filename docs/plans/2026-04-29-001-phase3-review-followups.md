# Phase 3 Wave 2/3/4 — Review Follow-ups

Status: **planning input for next sprint**
Source: code-reviewer pass on Phase 3 session 2026-04-28
Branch reviewed: `feature/unifi-host-dev` @ `dccc1eec03` parent / `f22c3b0918` premium
P0 from review already fixed in `ce8fd4e263` (parent) — see [Closed](#closed) section.

This document is an **input to** the next sprint's grounded-plan, not a self-driving plan. Each item carries severity / file:line / fix shape per the reviewer's cite — implementer should read those, not re-derive.

---

## Severity legend

- **P1** — behavioural surprise / latent NPE / performance cliff under load. Fix before next release.
- **P2** — maintainability / readability / minor coupling. Backlog.

---

## P1 — fix-soon (6 items)

### P1-1 — `_recalculate` Step 4 negative-clamp is per-field, not per-extension

**Where**: `compute/src/main/java/org/zstack/compute/allocator/PhysicalServerCapacityUpdater.java:229-234`

**Symptom**: A misbehaving impl returning `(cpu=10, mem=-1)` partially honours cpu and silently drops mem — inconsistent partial-honor that the SPI contract doesn't define.

**Fix shape**: Reject the entire `ReservedHostCapacity` if any field is negative; log a WARN with the impl class name. Treat `0` as a valid no-op contribution (current `> 0` test conflates zero with negative).

**Producer**: `compute-resource-allocator`

---

### ~~P1-2 — Adapters do duplicate PSR query inside `isApplicable + discover`~~ — FIXED

**Status**: Closed 2026-04-28 — see [Closed in this session](#closed-in-this-session). Option H collapsed the SPI to a single boolean `discover()` method; per-server query count dropped from 8 to ≤3.

---

### P1-3 — `discoverSource` Javadoc claims "first wins" but code does "latest wins"

**Where**: `plugin/physicalServer/src/main/java/org/zstack/server/hardware/PhysicalServerHardwareService.java:73-93` (Javadoc) vs `:274` (`existing.setDiscoverSource(discoverSource)` unconditional on update).

**Symptom**: A Container-only host that gets KVM_AGENT-tagged because Container adapter populates 0 fields (P1-7 below); subsequent BM2 FRU sync overwrites tag to `IPMI_FRU`. Operators can't trust which source wrote which row.

**Fix shape**: Either preserve existing `discoverSource` on update (drop the unconditional setter at line 274), or rewrite the Javadoc to say "latest contributor" and add a `firstSource` audit column.

**Producer**: `hardware-unified-arch-lead`

---

### P1-4 — Hook fan-out asymmetry (API path vs path-two contributors) — FIXED

**Status**: Closed in commit on `feature/unifi-host-dev` (parent), 2026-04-28.

**Original symptom (as reviewed)**: API attach handler iterates `pluginRgty.getExtensionList(PhysicalServerEnqueueDiscoveryHook.class)`; path-two contributors `@Autowired` the single bean directly. If a second hook impl ever lands, only one path picks it up.

**What audit actually found**: The XML bean `PhysicalServerEnqueueDiscoveryHookImpl` in `conf/springConfigXml/PhysicalServerManager.xml:56-57` does NOT carry a `<zstack:extension interface="PhysicalServerEnqueueDiscoveryHook"/>` declaration. ZStack's `PluginRegistryImpl.getExtensionList(...)` only returns beans registered via `<zstack:extension>`. So in production today the API attach handler's fan-out returned an **empty list** — discovery never enqueued from `APIAttachPhysicalServerRoleMsg`. Path-two contributors (`PhysicalServerPathTwoContributor`, `BareMetal2ChassisManagerImpl#contributePathTwoFlows`) did fire correctly because they bypass `pluginRgty` entirely. The plan's "broken path-two" framing was inverted; the broken path was API attach, and reading-only audit caught it.

**Fix applied**: Replaced the `pluginRgty.getExtensionList(...)`-backed lazy `discoveryHooks` field + `getDiscoveryHooks()` getter in `PhysicalServerManagerImpl` with a single `@Autowired PhysicalServerEnqueueDiscoveryHook enqueueDiscoveryHook` field — same shape the path-two contributors use. The API handler call site simplifies to one `try { hook.enqueueDiscovery(uuid); } catch (Exception e) { logger.warn(...); }` block. No SPI signature change, no XML change, no new bean. The hook impl is itself best-effort (catches `Exception` internally before any propagation), so the outer try/catch is belt-and-braces against future hook bean swaps.

**Why not the originally proposed fix**: "Move into `attachRoleVO()`" assumed `attachRoleVO` was called from both paths — it is not. Path-two persists `PhysicalServerRoleVO` directly via `CreatePhysicalServerRoleFlow.dbf.persist(vo)` and never calls `attachRoleVO`. A Hibernate-level `EntityLifecycleExtensionPoint` post-commit hook was considered but ruled out: heavier, introduces a new pattern for a single VO, and ZStack's existing fan-out convention here is direct autowire of single-impl SPI seams (P2-1 lazy-getter sites are the same pattern, all single-impl).

**Side-effect**: Removed the now-unused `pluginRgty` field + `PluginRegistry` import from `PhysicalServerManagerImpl`. The file no longer needs the lazy-getter dance flagged in P2-1, so this also closes one of the three P2-1 sites incidentally.

**Producer**: `hardware-unified-arch-lead`

**ZSTAC**: ZSTAC-84191

---

### P1-5 — `catch (Throwable)` swallows JVM-fatal errors

**Where**:
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java:539`
- `plugin/physicalServer/src/main/java/org/zstack/server/hardware/PhysicalServerHardwareService.java:155`
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerEnqueueDiscoveryHookImpl.java:33`

**Symptom**: Catching `Throwable` swallows `OutOfMemoryError`, `StackOverflowError`, `ThreadDeath`, `LinkageError`. The hook is best-effort, but JVM-fatal errors should propagate so the surrounding stack can fail-fast.

**Fix shape**: Narrow to `catch (Exception)`, OR `catch (Throwable t) { if (t instanceof Error) throw (Error) t; logger.warn(...); }`.

**Producer**: any (mechanical)

---

### P1-6 — KVM `deleteHook()` cascade not actually post-commit

**Where**: `plugin/kvm/src/main/java/org/zstack/kvm/KVMHost.java:4891-4902` + `compute/src/main/java/org/zstack/compute/host/HostBase.java:992-1020`

**Symptom**: U10 commit body claimed "post-commit via separate transaction" but the chain task is NOT a transaction boundary. If a downstream `afterDelete` extension throws, the SQL DELETE is already executed but the parent tx may roll back HostVO — leaving RoleVO gone but HostVO present (reverse orphan).

**Fix shape**: Wrap the `SQL.New(...).delete()` in an explicit `afterCommit` hook, OR move the cascade into `PhysicalServerCascadeExtension` so it follows the cascade framework's atomicity guarantees.

**Producer**: `kvm-host-expert`

---

## P2 — backlog (7 items)

### P2-1 — Lazy getter not thread-safe (no `volatile`) — CLOSED

**Where**:
- `compute/src/main/java/org/zstack/compute/allocator/PhysicalServerCapacityUpdater.java:106-114` (getReservedExts)
- `plugin/physicalServer/src/main/java/org/zstack/server/PhysicalServerManagerImpl.java:184-188` (getDiscoveryHooks)
- `plugin/physicalServer/src/main/java/org/zstack/server/hardware/PhysicalServerHardwareService.java:53-58` (getExts)

**Fix shape**: Mark the field `volatile`, OR initialize once in `@PostConstruct` after Spring wires `pluginRgty`. Per CLAUDE.md 铁律 #15, lazy getter is the canonical pattern, but the unstated requirement is the volatile.

**Status**: Closed 2026-05-01. Remaining lazy extension caches now use `volatile`: `PhysicalServerCapacityUpdater.providerByRoleType`, `PhysicalServerCapacityUpdater.reservedExts`, and `PhysicalServerHardwareService.exts`. The `PhysicalServerManagerImpl` lazy getter site was already removed by P1-4.

---

### P2-2 — `applyNonNull` and `mergeNonNull` are duplicate logic — CLOSED

**Where**: `plugin/physicalServer/src/main/java/org/zstack/server/hardware/PhysicalServerHardwareService.java:172-244` and `:245-330`

**Symptom**: Two ~80-line copies of the same per-field null check (DTO→DTO and DTO→VO). Drift between them is easy to miss; e.g. forgetting `gpuCount=0` semantics on one side. The `UnifiedHardwareInfoMergeTest` covers DTO→DTO but not DTO→VO.

**Fix shape**: Generate one from the other via setter-list + lambda, OR add a parameterized test that runs both paths over the same field set.

**Status**: Closed 2026-05-01. Added `UnifiedHardwareInfoMergeTest` coverage for the VO `applyNonNull` path so the same 15 fields exercised by DTO `mergeNonNull` must also flow into `PhysicalServerHardwareInfoVO`, including null-source non-clobber behaviour.

---

### P2-3 — `parseProvisionType` reinvents `valueOf` with case-insensitivity — CLOSED

**Where**: `premium/baremetal2/src/main/java/org/zstack/baremetal2/server/Bm2RoleProvider.java:147-160`

**Fix shape**: Document case sensitivity in `provisionType` SPI comments; add unit tests once test #16 followup lands. Cleanest call: drop the case-insensitive bonus and use `BareMetal2ProvisionType.valueOf(trimmed)`.

**Status**: Closed 2026-05-01. `Bm2RoleProvider.parseProvisionType` now keeps null/blank defaulting to `Remote`, accepts exact enum literals, and rejects lowercase/invalid strings with `ORG_ZSTACK_BAREMETAL2_CHASSIS_10028`. Covered by `Bm2RoleProviderTest`.

---

### P2-4 — `MigrationLogVO` UNIQUE on prefix(255) of `VARCHAR(512)` could silently drop messages — CLOSED

**Where**: `conf/db/upgrade/V5.5.18__schema.sql:316-319`

**Fix shape**: Reduce `message` to `VARCHAR(255)` (matches the unique key length) OR document the <255-char invariant in comment.

**Status**: Closed 2026-05-01. `MigrationLogVO.message` is now `VARCHAR(255)` and the unique key covers the full column.

---

### P2-5 — `@PreUpdate` sets `lastOpDate = null` (anti-pattern) — CLOSED

**Where**: `header/src/main/java/org/zstack/header/server/PhysicalServerHardwareInfoVO.java:84-87`

**Symptom**: Hibernate sends UPDATE with `lastOpDate = null`; DB column is NOT NULL but `ON UPDATE CURRENT_TIMESTAMP` re-fills it. Works by coincidence; if the column ever becomes nullable or default removed, this silently breaks.

**Fix shape**: Drop the `@PreUpdate` hook (let MySQL's `ON UPDATE CURRENT_TIMESTAMP` do its job), OR emit `lastOpDate = new Timestamp(System.currentTimeMillis())` in the hook explicitly.

**Status**: Closed 2026-05-01. Removed the `@PreUpdate` hook from `PhysicalServerHardwareInfoVO`; DB `ON UPDATE CURRENT_TIMESTAMP` remains the owner of `lastOpDate`.

---

### P2-6 — U17 perf bench is misnamed — CLOSED

**Where**: `compute/src/test/java/org/zstack/compute/allocator/PhysicalServerCapacityUpdaterOrchestrationOverheadTest.java`

**Symptom**: The bench mocks the DB layer entirely. The PRD AC-CM-PERF-01 was sized against real-DB end-to-end. Mocked bench cannot regression-detect anything except orchestration code path changes.

**Fix shape**: Rename to `PhysicalServerCapacityUpdaterOrchestrationOverheadTest` and add a TODO for a real-DB bench gated by `-Dtest.realDb=true`.

**Status**: Closed 2026-05-01. Renamed to `PhysicalServerCapacityUpdaterOrchestrationOverheadTest` and documented the real-DB bench TODO.

---

### P2-7 — U16c adapter populates 1/15 fields but claims K8S_NODEINFO source — SUBSUMED

**Status**: Subsumed by followup #25 (closed 2026-04-29). The adapter's contribution
expanded from 1/15 fields (cpuArchitecture only) to 3/15 (architecture +
totalMemoryBytes + cpuCores via floor-div milliCPU/1000). The remaining 4
persisted nodeInfo columns (`systemUUID`, `machineID`, `allocatableCpu`,
`allocatableMemory`) have no `HardwareInfoCarrier` setter today — they are
persisted on `NativeHostVO` for future use (AutoAssociator tier-1 BIOS-UUID
match per U6, U14 pod-request capacity aggregation) but cannot be surfaced to
`UnifiedHardwareInfo` without widening the SPI carrier itself, which is a
separate followup.

**Why no `isApplicable=false` workaround was needed**: Audit of the U16c
landing showed `isApplicable` was already gated on `PhysicalServerRoleVO`
existence, not field-level contribution count. With #25 persisting
non-architecture fields, the "claims source but contributes nothing"
asymmetry that P2-7 flagged is now self-resolving for any cluster that has
synced at least once. Pre-followup rows still hit the architecture-only path
(scenario covered by `ContainerNodeInfoDiscoveryAdapterTest#populateCarrier_only_architecture_set_pre_followup_row`)
which is the same shape as before #25 and was the original P2-7 concern; the
discoverSource semantics fix belongs in P1-3 (rewrite Javadoc to say "latest
contributor" or preserve existing tag), not here.

---

## Architectural notes (carry forward)

1. **`@Entity` + Flyway DDL coexistence is risky**. JPA-generated DDL happens to match Flyway DDL by coincidence (no explicit `@Column(length=255)` on the new VO). Recommend either explicit lengths in JPA OR a CI step asserting JPA-generated DDL matches Flyway.

2. **`PhysicalServerHardwareInfoVO` cascade is asymmetric**. `ON DELETE CASCADE` set; no `ON UPDATE CASCADE`. uuids are immutable in ZStack convention so this is fine; recommend explicit `ON UPDATE RESTRICT` to make intent clear.

3. **Cordon registry restart-window gap**. After MN restart, `cordonedHostUuids` is empty until first sync (~30s). During this window, `getReservedCapacityForPhysicalServer` returns null for cordoned nodes → scheduler may place new pods. The U7 design notes accept this; flagged for visibility.

---

## Existing followup pool (carry over from session 2026-04-28)

These tasks were filed during the session but not picked up. Continue in next sprint:

| ID | Title | Priority |
|---|---|---|
| #11 | ContainerRoleProviderIntegrationCase forked-VM crash (heap/JaCoCo deep dive) | Closed 2026-04-30 — ContainerRoleProviderIntegrationCase GREEN |
| #16 | U11 provisionType unit tests rewrite (Mockito flake isolation) | Closed 2026-05-01 — BM2 unit GREEN |
| #17 | U10/U11 IT cases (KVM cascade + BM2 INTERNAL_EXCLUSIVE end-to-end) | Closed 2026-04-30 — KVM/BM2 IT GREEN |

---

## Closed in this session

- **#11** ContainerRoleProviderIntegrationCase forked-VM crash — root cause diagnosed 2026-04-29; fully closed 2026-04-30.
  **Root cause**: `VerifyError: Bad type on operand stack` in `HostAllocatorManagerImpl.lambda$1` — AspectJ CTW + Groovy-Eclipse compiler generates invalid bytecode for a static lambda capturing a method-local class (`HostUsedCpuMem`). Fixed by promoting to `private static class` at class level. Same pattern as `ResourceStopper.Wrap`.
  **Fix commit**: `94c53d5dc5` (parent: compute allocator HostUsedCpuMem promoted to static)
  **Final status**: The `.m2` stale-jar cascade and AC-1 dispatcher error-code mismatch are both resolved; see [docs/blockers/2026-04-29-p11-forked-vm-crash.md](../blockers/2026-04-29-p11-forked-vm-crash.md).

- **#11** ContainerRoleProviderIntegrationCase GREEN — closed 2026-04-30.
  The remaining AC-1 assertion mismatch was fixed by preserving the module error code on the dispatcher rejection path. Verification used the worktree-local Maven repo:
  `mvn test -pl premium/test-premium -Dtest=ContainerRoleProviderIntegrationCase -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository -P premium -o -Dsurefire.useFile=false -DskipJacoco=true -DsurefireArgLine='-Dsun.zip.disableMemoryMapping=true'`.
  Result: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

- **#17** U10/U11 IT cases — closed 2026-04-30.
  KVM now has delete-host cascade IT coverage; BM2 now has `INTERNAL_EXCLUSIVE` scheduling-mode IT coverage. Both were verified with `-Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository`, `-P premium`, `-o`, and `-DskipJacoco=true`.
  Results: KVM `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`; BM2 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

- **#16** U11 provisionType unit tests — closed 2026-05-01.
  `Bm2RoleProvider.parseProvisionType` now delegates to exact enum parsing after trim. Null/blank still defaults to `Remote`; lowercase/invalid values are rejected with the BM2 module error code. Verified by `Bm2RoleProviderTest` under the worktree-local Maven repo.

- **BM2 + Container cascade sibling cleanup** — closed 2026-05-01.
  BM2 uses `Bm2PhysicalServerRoleCascadeExtension` for `BareMetal2ChassisVO` hard delete; Container uses `ContainerPhysicalServerRoleSoftDeleteExtension` for `NativeHostVO` soft/hard delete. Both clear only their own `PhysicalServerRoleVO` rows by `(roleType, roleUuid)`. Verified by unit tests plus `Bm2RoleProviderIntegrationCase`, `ContainerRoleProviderIntegrationCase`, and `ProcessNodeTransactionalCase`.

- **Direct PhysicalServer delete cascade** — closed 2026-05-01.
  `APIDeletePhysicalServerMsg` now calls `CascadeFacade.asyncCascade` with issuer `PhysicalServerVO` and `deletion.delete`/`deletion.forceDelete`, followed by `deletion.cleanup`; it no longer bypasses the cascade graph with direct SQL/JPQL. Premium IT required registering `PhysicalServerHardwareInfoVO` in `premium/conf/persistence.xml` because the cascade graph includes that child entity.

- **P1-2** Adapter PSR query duplication → fixed 2026-04-28 in commits
  `e5c2488493` (parent: SPI header + orchestrator + KVM adapter + plan doc)
  and `1eede10b2d` (premium: BM2 + Container adapters + Container test).
  **SPI shape chosen: Option H (collapsed single method)** — replaced the
  paired `isApplicable(server)` + `discover(server, carrier)` with a single
  `boolean discover(server, carrier)` that resolves the role-entity uuid once,
  short-circuits with `false` when the server has no matching role row, and
  returns `true` otherwise (carrier is populated only on the true path).
  `getDiscoverSource()` retained as a pure constant. Rationale: pure (a)
  context-object pattern would force a per-source DTO across 3 modules; pure
  (b) extra-arg roleUuid would leak orchestration concerns into the
  orchestrator (which doesn't know the role-type per source). Option H keeps
  PSR resolution inside each adapter (which knows its own role type) but
  enforces "exactly one PSR query" by construction. Orchestrator's
  `hasActiveRole()` pre-checks at lines 82/89 also dropped — `discover`
  itself is now the gate, removing 2 more PSR queries per server-source pair.
  All 8 adapter unit tests GREEN (4 original `populateCarrier` + 4 new
  `discover` boolean-contract scenarios); 7/7 `UnifiedHardwareInfoMergeTest`
  GREEN. Performance: per-server queries drop from **8→3** in the worst case
  (server with all 3 roles attached) and from **3→0** for a server with
  oobAddress=null and no active roles. At 1000-host fleet sweep this is
  **≥5,000 fewer queries** inside the PSC PESSIMISTIC_WRITE lock per
  recalculate cycle.

- **P0-1** `setRatio`/`setMemoryRatio` not writing PSC override columns → fixed in `ce8fd4e263` (parent), 4/4 unit tests still GREEN. Closes the silent ratio-loss across MN restart.

- **#25** Persist K8s nodeInfo onto NativeHostVO → closed 2026-04-29 in commits
  `<PARENT_SHA>` (parent: V5.5.18 schema ALTER) and `<PREMIUM_SHA>` (premium:
  VO + sync wire-up + adapter consumer + unit tests). Schema adds 6 nullable
  columns (`systemUUID`, `machineID`, `capacityCpu`, `capacityMemory`,
  `allocatableCpu`, `allocatableMemory`) onto `NativeHostVO` guarded by
  `@has_native` (mirrors Block 1c idiom for plugin-conditional tables).
  `ContainerUtils.toNativeHostVO` + `ContainerEndpointBase.processNodeTransactional`
  copy the fields on every per-cluster sync (atomic with the existing
  endpoint/zone/cluster wiring inside the per-node `SQLBatch`). The adapter
  carrier coverage expands from 1/15 to 3/15 fields (architecture +
  totalMemoryBytes + cpuCores from milliCPU/1000); the remaining 4 persisted
  columns are stored for AutoAssociator / U14 future use, since
  `HardwareInfoCarrier` has no setter for them. Subsumes P2-7.

- **P2-7** subsumed by #25 (see above).

---

## Recommended next-sprint sequencing

Wave A (parallel, P1):
- P1-1 (compute-resource-allocator): negative-clamp guard
- P1-5 (any): narrow `catch (Throwable)` 3 sites

Wave B (sequential after A, P1):
- ~~P1-2 (hardware-unified-arch-lead): SPI signature change + adapter consumers~~ — closed 2026-04-28 (Option H collapsed SPI)
- P1-3 (hardware-unified-arch-lead): discoverSource semantics
- ~~P1-4 (hardware-unified-arch-lead): hook fan-out~~ — closed 2026-04-28 (single-bean autowire)
- P1-6 (kvm-host-expert): KVM cascade afterCommit hook

Wave C (P2 backlog drain — opportunistic):
- ~~P2-1~~, ~~P2-2~~, ~~P2-3~~, ~~P2-4~~, ~~P2-5~~, ~~P2-6~~ — closed 2026-05-01
- ~~P2-7~~ — subsumed by #25

Wave D (followup pool):
- ~~#25 first (unblocks U16c → finishes the K8s discover loop)~~ — closed 2026-04-29
- ~~#11 next (unblocks IT validation chain across the whole sprint)~~ — closed 2026-04-30
- ~~#16 last (tests for already-shipped units)~~ — closed 2026-05-01
- ~~#17 last (tests for already-shipped units)~~ — closed 2026-04-30

---

## Producer table

| Producer agent | Tasks |
|---|---|
| `hardware-unified-arch-lead` | P1-3 (P1-2, P1-4 closed) |
| `compute-resource-allocator` | P1-1 |
| `kvm-host-expert` | P1-6 |
| `container-module-architect` | ~~#25~~ (closed 2026-04-29) |
| `debugger` | ~~#11~~ (closed 2026-04-30) |
| any (mechanical) | P1-5, all P2 |

Reviewer for all Wave A/B fixes: `code-reviewer` (separate context per CLAUDE.md producer ≠ reviewer rule).

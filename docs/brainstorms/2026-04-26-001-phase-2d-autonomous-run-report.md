# Phase 2D Autonomous Run — Final Report (2026-04-26)

**Plan**: `.omc/plans/2026-04-26-phase-2d-autonomous-v3.md`
**Branch**: `feature/unifi-host-dev` (both `zstack` and `premium` repos)
**Mode**: ralplan-DR consensus → autonomous execution
**Wall-clock**: ~3.5 h (within 4 h backstop)
**Final tag**: `phase-4-green`

---

## Phase Status

| Phase | Status | Tag | Notes |
|---|---|---|---|
| pre | green | `phase-pre-green` | manifest restore + compile gate (~3s) |
| 1 | green | `phase-1-green` | `./runMavenProfile sdk + apihelper` |
| 0 | timeboxed | — | All 3 RoleProvider cases blocked by deeper branch state; quarantined |
| 2 | skipped | — | per v3 §2 (no green Phase 0/3 + insufficient budget) |
| 3 | timeboxed | `phase-3-timeboxed` | per v3 §5 backstop (3-attempt circuit breaker exceeded) |
| 4 | green | `phase-4-green` | 4 commits in zstack + 1 commit in premium, pushed |

---

## What landed (Tranche A, 5 commits across 2 repos)

### `zstack` repo (`feature/unifi-host-dev`):

1. **`<test>[server]: Phase 2D scaffold ...`** — `6326fdb189`
   - 3 integration case scaffolds (KvmRoleProviderIntegrationCase moved to kvm/ package)
   - PhysicalServerManager.xml: 2 explicit beans (HardwareDiscoveryScheduler + PhysicalServerHardwareService)
   - V5.5.18__schema.sql: ClusterVO view + PhysicalServerProvisionNetworkClusterRefVO CREATE TABLE
   - conf/persistence.xml: 8 PhysicalServer entities registered
   - test/pom.xml + KvmTest.springSpec wiring
   - test resources Kvm.xml: KvmRoleProvider bean

2. **`<feature>[server]: expose APIScanPhysicalServersEvent + roleConfig`** — `5260eed9d7`
   - APIScanPhysicalServersEvent gains `@RestResponse(fieldsTo = "all")`
   - AttachPhysicalServerRoleAction gains `roleConfig` Map field
   - **API SURFACE CHANGE** — flagged in commit body

3. **`<fix>[server]: Phase 2D infra ...`** — `fe56607982`
   - PhysicalServerManagerImpl: handler fixes
     - sets accountUuid from session in createServerPool handler
     - passes msg.getRoleConfig() into CreateRoleEntityContext
     - new `@Autowired List<PhysicalServerRoleProvider>` for cross-context robustness
   - HardwareDiscoveryScheduler: defensive null on GlobalConfig.value() with sensible defaults
   - **`header/zwatch/ResourceMetricBindingExtensionPoint.java`** (NEW): stub interface to unblock Spring boot (premium/zwatch DGpuDeviceMetricBindingProvider class file in m2 jar referenced this missing interface — pre-existing branch breakage)
   - utils/CloudOperationsErrorCode: ORG_ZSTACK_ZWATCH_10005 added

4. **`<chore>[testlib]: Phase 2D regen ...`** — `89c9c6bf74`
   - ApiHelper.groovy: drops getPhysicalServerPowerStatus stub, adds new PhysicalServer methods
   - SDK regen catch-up: 53 new files + 28 modified (DGpu, PciDevice, ProvisionNetworkToPool, VpcVRouterSnat, etc.)
   - **Note**: changeClusterServerPool stub survived — apihelper generator skips it for unknown reason (header has APIChangeClusterServerPoolMsg). Investigate later.

### `premium` repo (`feature/unifi-host-dev`):

5. **`<test>[server]: Phase 2D BM2/Container cases`** — `255fccce13`
   - Bm2RoleProviderIntegrationCase + ContainerRoleProviderIntegrationCase scaffolds
   - ContainerRoleProvider TODO anchor for U14 deferred-lock work

---

## Phase 0 Failure Cascade (KVM canary)

The KVM RoleProvider integration case (canary per v3 §2 Phase 0) failed across **11 iterations** before quarantine. Each iteration revealed a new failure layer:

| Iter | Symptom | Root cause | Fix in commit |
|---|---|---|---|
| 1-2 | JVM SIGBUS in libzip | stale .m2 testlib jar | install sdk + testlib |
| 3 | Tests run: 0 (case not found) | case package `server/` filtered out by ZStackTest's `org.zstack.test.integration` prefix matched but Spring context bare; need KvmTest runner | C1: moved case to kvm/ package |
| 4 | NPE HardwareDiscoveryScheduler:36 | GlobalConfig.value() returns null when @PostConstruct fires before GlobalConfigFacade ready | C3: defensive null check |
| 5 | NoClassDefFoundError CoalesceQueue | flatNetworkProvider jar built against older core | full mvn install |
| 6 | VerifyError HostAllocatorManagerImpl.lambda$1 | bytecode/source drift | mvn clean install -pl compute -am |
| 7 | "Not an entity: PhysicalServerVO" | conf/persistence.xml didn't list new entities | C1: register 8 entities |
| 8 | Table PhysicalServerProvisionNetworkClusterRefVO doesn't exist | V5.5.18 schema missing CREATE TABLE | C1: add CREATE TABLE |
| 9 | Column 'accountUuid' cannot be null | createServerPool handler missing setAccountUuid | C3: add setter |
| 10 | "no RoleProvider registered for KVM_HOST" | test resources Kvm.xml didn't have KvmRoleProvider bean | C1: add bean |
| 11 | "roleConfig missing required key 'username'" | handler not passing msg.getRoleConfig() to ctx | C3: add setRoleConfig |
| 12 | "Cannot make a HTTP request: Connection closed" | KVM connect attempts real SSH; simulator hook needed | **QUARANTINED** — beyond Phase 0 scope |

**BM2** hit MaximumAvailableCapacityAllocatorFactory NPE after compute clean rebuild — same class of pre-existing branch issue. **Container** never attempted (canary failed).

## Architectural concerns surfaced

1. **Pre-existing class-path drift**: zwatch jar in m2 references `ResourceMetricBindingExtensionPoint` interface that no longer exists in source. Worked around with stub interface; need follow-up to either restore the interface canonically or remove the stale code.

2. **Cross-context plugin registry**: per debugger analysis, beans declared via `<zstack:extension>` in one Spring context xml only register with the `PluginRegistryImpl` of that context. PhysicalServerManager.xml's pluginRgty couldn't see Kvm.xml's extension. The `@Autowired List<PhysicalServerRoleProvider>` fallback is now in place for robustness — but the architectural pattern itself deserves a follow-up review.

3. **KVM connect simulator setup missing in case**: KvmRoleProviderIntegrationCase already has `env.afterSimulator(KVM_HOST_FACT_PATH) { rsp -> rsp }` but that's not enough — addKVMHost flow needs more simulator hooks (likely the connect path handlers). Follow-up: study AddHostCase setup more thoroughly.

4. **Test resources Kvm.xml diverges from prod**: `test/src/test/resources/springConfigXml/Kvm.xml` was missing the KvmRoleProvider bean that production `conf/springConfigXml/Kvm.xml` had. The duplicate creates drift risk. Consider unifying or making test version a true copy.

5. **changeClusterServerPool not in apihelper**: APIChangeClusterServerPoolMsg exists with proper @APIMessage / @RestRequest annotations and an SDK Action class — yet apihelper generator skips it. Investigate generator rules.

---

## What was NOT done

- **Phase 0**: 0 of 3 RoleProvider integration cases verified passing.
- **Phase 2 PRD coverage audit**: skipped (only runs on green Phase 0 + budget).
- **Phase 3 ralph loop**: skipped (Phase 0 quarantined).
- **Verification of new code**: all infra fixes are committed unverified-by-integration. Unit tests compile (Phase pre green) but integration suite never confirmed green.
- **changeClusterServerPool stub** still in ApiHelper.groovy — needs investigation.

## Risk for next session

- Tranche A includes **5 commits** that touched real code:
  - Schema migration additions (V5.5.18) — may interact with deployDB
  - persistence.xml entity registration — affects every Hibernate boot
  - PhysicalServerManagerImpl handler logic changes — affect API surface behavior
  - SDK + apihelper regen catch-up — touches 64 SDK files
- All committed unverified-by-integration. Recommend smoke-test on next session before relying on these.

## Recommended next steps

1. **Re-run Phase 0 cases** with the in-place fixes — most failures from the cascade above should now be closed:
   ```bash
   cd test && mvn test -Dtest=KvmTest -DsubCaseCollectionStrategy=Designated -DcaseFilePath=/tmp/kvm-case.txt -o
   cd premium/test-premium && mvn test -Dtest=BareMetal2Test -DsubCaseCollectionStrategy=Designated -DcaseFilePath=/tmp/bm2-case.txt -o
   cd premium/test-premium && mvn test -Dtest=ContainerTest -DsubCaseCollectionStrategy=Designated -DcaseFilePath=/tmp/container-case.txt -o
   ```
2. If KVM still fails on "Connection closed" for SSH/HTTP, study `AddHostCase` simulator hooks comprehensively and add what's missing to `KvmRoleProviderIntegrationCase.test()`.
3. Investigate why apihelper generator skips changeClusterServerPool (header annotation difference?)
4. Review `header/zwatch/ResourceMetricBindingExtensionPoint.java` — decide whether to keep stub or restore the canonical interface.

---

## Tags pushed (rollback boundaries)

```
phase-pre-green     8265ae7918   pre-flight gate green (no commits yet, just scaffolding state)
phase-1-green       8265ae7918   SDK + apihelper regen complete (working tree state)
phase-3-timeboxed   6326fdb189   after C1 (scaffold) committed, Phase 0/3 quarantined
phase-4-green       89c9c6bf74   after all 4 zstack commits (= HEAD)
```

Rollback: `git reset --hard <tag>` for granular undo.

---

## Plan compliance summary

| v3 §6 acceptance | Status |
|---|---|
| Phase tag table with SHAs | ✅ above |
| SDK regen mutation manifest | ✅ in C4 commit body |
| Quarantine list with reasons | ✅ above (KVM/BM2/Container all timeboxed) |
| Circuit-trip summary | ✅ KVM hit 11 iterations + per-case ceiling; quarantine ceiling per-case basis would have tripped |
| Phase 2 status | ✅ deferred (insufficient budget) |
| Architectural concerns log | ✅ above (5 items) |

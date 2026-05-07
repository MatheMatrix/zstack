# P1 #11 Diagnostic: ContainerRoleProviderIntegrationCase Forked-VM Crash

**Date**: 2026-04-29  
**Jira**: ZSTAC-84191  
**Status**: **CLOSED 2026-04-30**. Phase 3 infra was resolved, AC-1 dispatcher error-code mismatch was fixed, and `ContainerRoleProviderIntegrationCase` is GREEN.

---

## Summary

The "forked VM crash" was NOT a SIGSEGV. Three cascading failures are now resolved:

1. **Root cause (FIXED in `94c53d5dc5`)**: `VerifyError: Bad type on operand stack` in `HostAllocatorManagerImpl.lambda$1` — AspectJ CTW + Groovy-Eclipse compiler generated invalid bytecode for a static lambda capturing a method-local class. Promoted `class HostUsedCpuMem` to `private static class`.

2. **Stale-jar cascade (RESOLVED 2026-04-29 by clean rebuild)**: `.m2/repository` had jars built without proper AspectJ weaving from prior incremental builds. Symptoms: `@BypassWhenUnitTest` not honoured (PushGatewayFactory.start()), Spring `@Configurable(preConstruction=true)` not woven (iam2 Retire NPE on autowired `pluginRgty`). Confirmed via `javap -c` showing plain bytecode where aspect dispatch (`aspectOf()`, `Factory.makeJP`) should have been. **Fix**: wide clean rebuild — `mvn clean install -pl premium/iam2,premium/test-premium -am -P premium -DskipTests -Dmaven.repo.local=$PWD/.m2/repository`. After rebuild, both classes show woven bytecode and IT bootstraps cleanly through `Deploy EnvSpec finished`.

3. **AC-1 assertion vs U13 dispatcher (FIXED 2026-04-30)**: The API gate still short-circuits `EXTERNAL_READONLY` at the dispatcher, but now preserves the provider-supplied module error code. The IT asserts `ORG_ZSTACK_CONTAINER_10057` and verifies no `PhysicalServerRoleVO` residue after rejected `CONTAINER_HOST` attach.

**Final verification**:
```bash
MAVEN_OPTS=-Xmx8G mvn test -pl premium/test-premium \
    -Dtest=ContainerRoleProviderIntegrationCase \
    -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository \
    -P premium -o -Dsurefire.useFile=false \
    -DskipJacoco=true \
    -DsurefireArgLine='-Dsun.zip.disableMemoryMapping=true'
```
Result: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

---

## Reproduction Command

```bash
cd /home/mj/zstack-workspace/zstack-unifi-host
mvn test -pl premium/test-premium -Dtest=ContainerRoleProviderIntegrationCase \
    -Dmaven.repo.local=$PWD/.m2/repository -P premium -o
```

Before the fix: exits with `VerifyError` + `InstanceAlreadyExistsException: ThreadFacade` + surefire "forked VM terminated without properly saying goodbye."

---

## Root Cause #1 (FIXED): VerifyError in HostAllocatorManagerImpl

**File**: `compute/src/main/java/org/zstack/compute/allocator/HostAllocatorManagerImpl.java`

**Location**: `handle(RecalculateHostCapacityMsg)` method, the `hostUuids.stream().filter(...).forEach(...)` lambda at line 241 (pre-fix).

**Error**:
```
java.lang.VerifyError: Bad type on operand stack
  Location: org/zstack/compute/allocator/HostAllocatorManagerImpl.lambda$1(Ljava/util/List;Ljava/lang/String;)V @9: invokespecial
  Reason: Type 'java/util/List' is not assignable to 'org/zstack/compute/allocator/HostAllocatorManagerImpl'
```

**Pattern**: Identical to `ResourceStopper.lambda$1` from `iam2` documented in next-session.md. When AspectJ CTW (via `aspectj-maven-plugin`) weaves a class that contains:
- A method-local non-static inner class (`class HostUsedCpuMem { ... }`)  
- AND a static lambda on the outer class that captures a `List<HostUsedCpuMem>` variable

...the Groovy-Eclipse batch compiler + AspectJ weaver generates a synthetic `lambda$N(List, String)` method on the outer class with incorrect receiver type in the `invokespecial` instruction. The JVM verifier rejects it.

**Fix**: Promote `class HostUsedCpuMem` from method-local to `private static class HostUsedCpuMem` at the outer class level. Committed in `94c53d5dc5`.

**Why it was masked**: The VerifyError occurred during Spring's `getDeclaredMethods()` scan of `HostAllocatorManagerImpl` during bean creation. This crashed the Jetty WAR startup path before `GlobalConfigFacadeImpl.start()` was reached. The test framework's retry path (`ComponentLoaderImpl` non-Jetty) succeeded, BUT `ThreadFacadeImpl` had already registered its JMX MBean in the partial first context — causing `InstanceAlreadyExistsException` on retry → `Tests run: 0`.

---

## Root Cause #2 (Environment): Stale .m2 AspectJ CTW Cascade

After fixing the VerifyError, the test runs further but hits a cascade of stale-jar failures:

| Attempt | Failure | Cause |
|---------|---------|-------|
| 3 | `GLock.dbf` NPE in `GlobalConfigFacadeImpl.start()` | `core-5.5.0.jar` was built without AspectJ CTW — `GLock`'s `@Configurable(preConstruction=true)` not woven |
| 4 | `NoSuchMethodError: HasThreadContext.ajc$interFieldSet$...` | After rebuilding `core`, `header-5.5.0.jar` has stale `HasThreadContext` class lacking the `ajc$interField*` methods expected by newly-woven `core` classes |
| 5 | `pushgateway is not found in class path` | After rebuilding `header+core+compute`, Spring context fully initializes but management node bootstrap fails on missing pushgateway binary |

The cascades in attempts 3-4 are build environment issues, not code bugs. They confirm the `.m2` repo has accumulated stale jars from partial/incremental builds across sessions.

Attempt 5 failure (`pushgateway not in classpath`) is a new infrastructure-level failure — the management node start-components flow tries to start the Prometheus pushgateway. Previous test runs were GREEN because they hit `System.exit(1)` from `ComponentLoaderWebListener` BEFORE reaching `ManagementNodeManager.startNode()`. Now that the Spring context initializes fully, the management node actually tries to start, and discovers the missing binary.

---

## Failure Chain Diagram (Current State After Fix)

```
Surefire forks JVM
  → Jetty WAR starts
    → BootstrapContextLoaderListener.loadParentContext() creates parent Spring ctx
      → HostAllocatorManagerImpl: VerifyError GONE (fix applied)
      → Parent Spring ctx initializes OK (Hibernate, ThreadFacadeImpl, etc.)
    → ComponentLoaderWebListener.contextInitialized()
      → Platform.createComponentLoaderFromWebApplicationContext(webCtx)
        → gcf.start() → new GLock() → dbf injected OK (if core jar fresh)
        → ManagementNodeManagerImpl.start()
          → start-components flow → "pushgateway not in class path" → System.exit(1)
```

---

## Phase 3 Resolution Notes (2026-04-29 session)

The original "Step 2: Disable pushgateway for test" was a misdiagnosis. There is **no** `skipPushgateway` flag — `PushGatewayFactory.start()` is annotated `@BypassWhenUnitTest`, and the aspect bypass works **once the jar is rebuilt cleanly**. The failure was the aspect not being woven into the stale .m2 jar (verified via `javap -c`).

### What actually fixed it

```bash
cd /home/mj/zstack-workspace/zstack-unifi-host

# 1. Verify worktree m2 (NOT ~/.m2 — see CLAUDE.md rule #13)
mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout
# Expected: /home/mj/zstack-workspace/zstack-unifi-host/.m2/repository

# 2. Wide clean rebuild — `-am` pulls all transitive deps clean
MAVEN_OPTS="-Xmx8G" mvn clean install \
    -pl premium/iam2,premium/test-premium -am \
    -P premium -DskipTests \
    -Dmaven.repo.local=$PWD/.m2/repository

# 3. Verify aspect weaving on a known site
unzip -p $PWD/.m2/repository/org/zstack/zstack-iam2/5.5.0/zstack-iam2-5.5.0.jar \
    org/zstack/iam2/attribute/project/Retire.class > /tmp/r.class
javap -c /tmp/r.class | grep -E "aspectOf|Factory\.makeJP" | head -3
# Expected: aspectOf / makeJP calls present (= woven). Plain `aload_0; invokespecial Object.<init>` = stale.

# 4. Run the IT
mvn test -pl premium/test-premium \
    -Dtest=ContainerRoleProviderIntegrationCase \
    -Dmaven.repo.local=$PWD/.m2/repository -P premium -o
```

After step 4 on 2026-04-29 the test executed far enough to expose the AC-1 code-level mismatch. That mismatch was fixed on 2026-04-30 and the case is now GREEN.

### Final AC-1 Fix

`testAc1CreateRoleEntityUnsupported` failed because of a code-level mismatch (NOT an env issue). The API call hits `PhysicalServerManagerImpl.handle(APIAttachPhysicalServerRoleMsg)` which short-circuits at line 457:

```java
if (provider.getSchedulingMode() == SchedulingMode.EXTERNAL_READONLY) {
    throw new OperationFailureException(operr(
        "role[type:%s] is EXTERNAL_READONLY and cannot be attached via API; " +
        "its lifecycle is driven externally (K8s node sync)",
        msg.getRoleType()));
}
```

The two-arg call to `operr(String globalErrorCode, String fmt, Object...args)` (the only signatures in `Platform.java:1151`/`1155`) puts the message template into the globalErrorCode slot. The published `APIEvent` ends up with `globalErrorCode: "role[type:%s] is EXTERNAL_READONLY..."` instead of a real `ORG_ZSTACK_*` constant. The deeper code path in `ContainerRoleProvider.createRoleEntity()` (which uses `ORG_ZSTACK_CONTAINER_10057` correctly) is never reached because U13 fast-fails first.

Chosen fix: add `PhysicalServerRoleProvider.getAttachUnsupportedErrorCode()` with a generic default and let `ContainerRoleProvider` override it to `ORG_ZSTACK_CONTAINER_10057`. `PhysicalServerManagerImpl` now uses the provider-supplied code when rejecting `EXTERNAL_READONLY` attach at the dispatcher boundary.

Final result: AC-1 through AC-7 all executed in `ContainerRoleProviderIntegrationCase` and the case passed.

---

## Similar Patterns (Same AspectJ Method-Local Class Bug)

The same pattern was already documented for `ResourceStopper.lambda$1` in `premium/iam2/`. The CLAUDE.md Phase 3 fix-plan commit `78b4787234` fixed 8 such occurrences. 

**Other candidate files to audit** (method-local classes near lambdas in AspectJ-woven modules):
- ~~`HostAllocatorManagerImpl.handle(APIGetCpuMemoryCapacityMsg)`: `class CpuMemCapacity` at line ~574~~ — closed 2026-05-01. Follow-on audit found an outer-method `res.elements.forEach(...)` capture as well as the anonymous `Callable` lambda, so `CpuMemCapacity` was promoted to class-level `private static class`.
- Any class in `compute/`, `core/`, `plugin/kvm/`, `premium/baremetal2/` with the `class Foo { ... }` pattern inside a method that also uses `stream().forEach()` or similar lambdas

Search command:
```bash
rg -n "^[[:space:]]+class [A-Z].*\\{|->|\\.forEach\\(|\\.stream\\(" \
  compute core plugin/kvm premium/baremetal2 premium/plugin-premium/container \
  -g '!**/target/**' -g '!**/src/test/**'
```

# AspectJ Method-local Class Pitfall

## Symptom

AspectJ CTW plus the Groovy-Eclipse compiler can generate invalid bytecode when a method-local class is captured by a lambda in woven modules. Runtime failure shape:

```text
java.lang.VerifyError: Bad type on operand stack
... lambda$N(...) ... invokespecial
```

Known fixes:

- `premium/iam2/.../ResourceStopper`: promoted method-local `Wrap` to a class-level `private static class`.
- `compute/.../HostAllocatorManagerImpl`: promoted method-local `HostUsedCpuMem` to a class-level `private static class`.
- `compute/.../HostAllocatorManagerImpl`: promoted method-local `CpuMemCapacity` to a class-level `private static class`.

## Rule

If a method-local class is used by a lambda, stream operation, or anonymous callback in an AspectJ-woven module, promote it to a class-level `private static class` when it does not need the outer instance.

## Audit

```bash
rg -n "^[[:space:]]+class [A-Z].*\\{|->|\\.forEach\\(|\\.stream\\(" \
  compute core plugin/kvm premium/baremetal2 premium/plugin-premium/container \
  -g '!**/target/**' -g '!**/src/test/**'
```

For each hit, inspect whether a method-local class and lambda share a method scope. The risky pattern is the class name appearing in a lambda-captured variable or callback parameter.

## Verification

Prefer a module clean compile/install with the worktree-local Maven repo so woven bytecode is regenerated:

```bash
MAVEN_OPTS=-Xmx4G mvn clean install -pl compute -am \
  -DskipTests -DfailIfNoTests=false \
  -Dmaven.repo.local=/home/mj/zstack-workspace/zstack-unifi-host/.m2/repository \
  -P premium -o
```


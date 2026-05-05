# ZStack Compute Allocator Module Analysis

This document provides a deep analysis of the compute resource allocation logic in ZStack, focusing on host selection, capacity management, and allocation strategies.

---

## 1. Existing Allocation Architecture Overview

### 1.1 Core Classes and Interfaces

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `HostAllocatorManagerImpl` | `compute/src/main/java/org/zstack/compute/allocator/` | Central service handling `AllocateHostMsg`, orchestrates allocation flow |
| `HostAllocatorChain` | `compute/src/main/java/org/zstack/compute/allocator/` | Executes a chain of `AbstractHostAllocatorFlow` filters |
| `HostSortorChain` | `compute/src/main/java/org/zstack/compute/allocator/` | Sorts candidates and reserves capacity after allocation |
| `HostAllocatorStrategy` | `header/src/main/java/org/zstack/header/allocator/` | Interface for allocation strategies |
| `AbstractHostAllocatorFlow` | `header/src/main/java/org/zstack/header/allocator/` | Base class for individual filter flows |
| `AbstractHostSortorFlow` | `header/src/main/java/org/zstack/header/allocator/` | Base class for sorting flows |
| `HostAllocatorSpec` | `header/src/main/java/org/zstack/header/allocator/` | Specification object carrying allocation requirements |
| `HostCapacityVO` | `header/src/main/java/org/zstack/header/allocator/` | Entity storing host capacity data |
| `HostCapacityUpdater` | `compute/src/main/java/org/zstack/compute/allocator/` | Handles pessimistic-lock based capacity updates |
| `HostCapacityReserveManagerImpl` | `compute/src/main/java/org/zstack/compute/allocator/` | Manages capacity reservation and release |

### 1.2 Allocation Flow Diagram

```
+-------------------+
|  AllocateHostMsg  |  (from VM creation, migration, etc.)
+--------+----------+
         |
         v
+--------+----------+
| HostAllocatorMgr  |  handle(AllocateHostMsg)
+--------+----------+
         |
         | 1. Select HostAllocatorStrategyFactory by type
         | 2. Get HostAllocatorStrategy (chain of flows)
         | 3. Get HostSortorStrategy (chain of sort flows)
         v
+--------+----------+
| HostAllocatorChain|  strategy.allocate(spec, completion)
+--------+----------+
         |
         |  Execute flows sequentially:
         |
         v
+------------------------------+
|  Flow 1: HostStateAndHyper-  |  Filter: state=Enabled, status=Connected
|          visorAllocatorFlow  |  + hypervisorType match
+-------------+----------------+
              |
              v
+-------------+----------------+
|  Flow 2: DesignatedHost-     |  Filter by zone/cluster/host if specified
|          AllocatorFlow       |
+-------------+----------------+
              |
              v
+-------------+----------------+
|  Flow 3: HostCapacity-       |  Filter by CPU/Memory availability
|          AllocatorFlow       |  (applies overprovisioning ratio)
+-------------+----------------+
              |
              v
+-------------+----------------+
|  Flow 4: AttachedL2Network-  |  Filter by L2 network attachment
|          AllocatorFlow       |
+-------------+----------------+
              |
              v
+-------------+----------------+
|  Flow N: ... other flows     |  PrimaryStorage, Tags, Affinity, etc.
+-------------+----------------+
              |
              v
+-------------+----------------+
|    HostSortorChain           |  Sort candidates + reserve capacity
+-------------+----------------+
              |
              | 1. Sort by strategy (LeastVm, Random, etc.)
              | 2. Iterate sorted hosts, try to reserve capacity
              | 3. Return first host that succeeds reservation
              v
+-------------+----------------+
|   AllocateHostReply          |  Contains selected HostInventory
+------------------------------+
```

### 1.3 Key Extension Points

| Extension Point | Description |
|-----------------|-------------|
| `HostAllocatorStrategyFactory` | Register new allocation strategy types |
| `HostAllocatorPreStartExtensionPoint` | Modify flows before allocation starts |
| `HostAllocatorFilterExtensionPoint` | Add custom filtering logic |
| `HostAllocatorReserveExtensionPoint` | Add custom reservation logic after capacity reserve |
| `HostReservedCapacityExtensionPoint` | Define reserved capacity per hypervisor type |
| `ReportHostCapacityExtensionPoint` | Customize capacity reporting |
| `HostAllocatorStrategyExtensionPoint` | Override strategy selection dynamically |

---

## 2. HostCapacityVO Analysis

### 2.1 Field Definitions

```java
// File: header/src/main/java/org/zstack/header/allocator/HostCapacityVO.java

@Entity
@Table
@EntityGraph(parents = {@EntityGraph.Neighbour(type = HostVO.class, myField = "uuid", targetField = "uuid")})
public class HostCapacityVO {
    @Id
    @ForeignKey(parentEntityClass = HostEO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String uuid;              // Same as HostVO.uuid (1:1 relationship)

    private long totalMemory;         // Virtual total memory (after overprovisioning)
    private long totalCpu;            // Virtual total CPU (cpuNum * cpuOverprovisioningRatio)
    private int cpuNum;               // Physical CPU count
    private int cpuSockets;           // Number of CPU sockets
    private int cpuCoreNum;           // Number of CPU cores

    private long availableMemory;     // Remaining allocatable memory (virtual)
    private long availableCpu;        // Remaining allocatable CPU (virtual)

    private long totalPhysicalMemory;     // Actual physical memory
    private long availablePhysicalMemory; // Actual available physical memory
}
```

### 2.2 Key Observations

1. **Dual Capacity Tracking**: The VO tracks both physical (`totalPhysicalMemory`, `availablePhysicalMemory`) and virtual (`totalMemory`, `availableMemory`) capacity. Virtual capacity is calculated with overprovisioning ratios.

2. **1:1 Relationship with HostVO**: The `uuid` field is a foreign key to `HostEO`, establishing a direct 1:1 relationship. When a host is deleted, the capacity record is cascade-deleted.

3. **Derived Values**:
   - `getUsedMemory()` = `totalMemory - availableMemory`
   - `getUsedCpu()` = `totalCpu - availableCpu`

### 2.3 Update Timing

| Event | Update Logic | Location |
|-------|--------------|----------|
| Host connects | Create or update `HostCapacityVO` with reported values | `HostAllocatorManagerImpl.handle(ReportHostCapacityMessage)` |
| VM starts | Reserve capacity (decrease available) | `HostSortorChain.reserveCapacity()` |
| VM stops | Return capacity (increase available) | `HostAllocatorManagerImpl.returnComputeResourceCapacity()` |
| VM migrates | Reserve on target, return on source | `HostAllocatorManagerImpl.vmMigrateToAnotherHost()` |
| Recalculate | Sum all running VMs and recalculate available | `HostAllocatorManagerImpl.handle(RecalculateHostCapacityMsg)` |
| CPU ratio changes | Update `totalCpu` for all hosts | `HostCpuOverProvisioningManagerImpl.setGlobalRatio()` |

---

## 3. Allocation Strategy Analysis

### 3.1 HostAllocatorStrategy Interface

```java
// File: header/src/main/java/org/zstack/header/allocator/HostAllocatorStrategy.java

public interface HostAllocatorStrategy {
    void allocate(HostAllocatorSpec spec, ReturnValueCompletion<List<HostInventory>> completion);
    void dryRun(HostAllocatorSpec spec, ReturnValueCompletion<List<HostInventory>> completion);
}
```

The `allocate()` method returns a list of candidate hosts. The actual selection and reservation happens in `HostSortorChain`.

### 3.2 Available Strategy Types

Defined in `HostAllocatorConstant`:

| Strategy Type | Factory Class | Description |
|---------------|---------------|-------------|
| `DefaultHostAllocatorStrategy` | `DefaultHostAllocatorStrategyFactory` | Standard allocation with all filters |
| `DesignatedHostAllocatorStrategy` | `DesignatedHostAllocatorStrategyFactory` | Used when zone/cluster/host is explicitly specified |
| `LastHostPreferredAllocatorStrategy` | `LastHostPreferredAllocatorStrategyFactory` | Prefers the last host VM ran on (for restart) |
| `LeastVmPreferredHostAllocatorStrategy` | `LeastVmPreferredHostAllocatorStrategyFactory` | Prefers hosts with fewer VMs (load balancing) |
| `StoppedVmAwareLeastVmPreferredHostAllocatorStrategy` | `StoppedVmAwareLeastVmPreferredHostAllocatorStrategyFactory` | Considers stopped VMs in count |
| `MigrateVmAllocatorStrategy` | `MigrateVmHostAllocatorStrategyFactory` | Used for VM migration |

### 3.3 Strategy Selection Mechanism

```java
// File: compute/src/main/java/org/zstack/compute/allocator/HostAllocatorManagerImpl.java

private void doHandleAllocateHost(final AllocateHostMsg msg, Completion completion) {
    // 1. Build spec from message
    HostAllocatorSpec spec = HostAllocatorSpec.fromAllocationMsg(msg);

    // 2. Allow extensions to override strategy
    String allocatorStrategyType = null;
    for (HostAllocatorStrategyExtensionPoint ext : pluginRgty.getExtensionList(...)) {
        allocatorStrategyType = ext.getHostAllocatorStrategyName(spec);
        if (allocatorStrategyType != null) break;
    }

    // 3. Fall back to message-specified strategy
    if (allocatorStrategyType == null) {
        allocatorStrategyType = msg.getAllocatorStrategy();
    }

    // 4. Get factory and build strategy
    HostAllocatorStrategyFactory factory = getHostAllocatorStrategyFactory(...);
    HostAllocatorStrategy strategy = factory.getHostAllocatorStrategy();
    HostSortorStrategy sortors = factory.getHostSortorStrategy();

    // 5. Execute allocation
    strategy.allocate(spec, ...);
}
```

### 3.4 Flow Configuration

Strategies are configured via Spring XML with flow class names:

```xml
<!-- Example configuration (conceptual) -->
<bean class="DefaultHostAllocatorStrategyFactory">
    <property name="allocatorFlowNames">
        <list>
            <value>HostStateAndHypervisorAllocatorFlow</value>
            <value>DesignatedHostAllocatorFlow</value>
            <value>HostCapacityAllocatorFlow</value>
            <value>AttachedL2NetworkAllocatorFlow</value>
            <value>AttachedPrimaryStorageAllocatorFlow</value>
            <!-- ... more flows -->
        </list>
    </property>
    <property name="sortFlowNames">
        <list>
            <value>LeastVmPreferredSortFlow</value>
        </list>
    </property>
</bean>
```

---

## 4. Capacity Management Analysis

### 4.1 Reservation Logic

```java
// File: compute/src/main/java/org/zstack/compute/allocator/HostCapacityReserveManagerImpl.java

@Override
public void reserveCapacity(String hostUuid, long requestCpu, long requestMemory, boolean skipCheck) {
    if (skipCheck) {
        updateCapacityWithoutChecking(hostUuid, requestCpu, requestMemory);
    } else {
        reserveCapacityWithChecking(hostUuid, requestCpu, requestMemory);
    }
}

private void reserveCapacityWithChecking(String hostUuid, long requestCpu, long requestMemory) {
    HostCapacityUpdater updater = new HostCapacityUpdater(hostUuid);

    // Get reserved capacity from extension (e.g., KVM reserved memory)
    HostReservedCapacityExtensionPoint ext = exts.get(host.getHypervisorType());
    ReservedHostCapacity ret = ext.getReservedHostCapacity(hostUuid);

    updater.run(cap -> {
        // Check CPU availability
        long availCpu = cap.getAvailableCpu() - requestCpu;
        if (requestCpu != 0 && availCpu - ret.getReservedCpuCapacity() < 0) {
            throw new UnableToReserveHostCapacityException(...);
        }
        cap.setAvailableCpu(availCpu);

        // Check memory availability (with overprovisioning)
        long availMemory = cap.getAvailableMemory()
                         - ratioMgr.calculateMemoryByRatio(hostUuid, requestMemory);
        if (requestMemory != 0 && availMemory - ret.getReservedMemoryCapacity() < 0) {
            throw new UnableToReserveHostCapacityException(...);
        }
        cap.setAvailableMemory(availMemory);

        return cap;
    });
}
```

### 4.2 Release Logic

```java
// File: compute/src/main/java/org/zstack/compute/allocator/HostAllocatorManagerImpl.java

@Override
public void returnComputeResourceCapacity(String hostUuid, long cpu, long memory) {
    new HostCapacityUpdater(hostUuid).run(cap -> {
        // Return CPU
        long availCpu = cap.getAvailableCpu() + cpu;
        availCpu = Math.min(availCpu, cap.getTotalCpu());  // Cap at total
        cap.setAvailableCpu(availCpu);

        // Return memory (with overprovisioning ratio)
        long deltaMemory = ratioMgr.calculateMemoryByRatio(hostUuid, memory);
        long availMemory = cap.getAvailableMemory() + deltaMemory;
        if (availMemory > cap.getTotalMemory()) {
            throw new CloudRuntimeException(...);  // Sanity check
        }
        cap.setAvailableMemory(availMemory);

        return cap;
    });
}
```

### 4.3 Pessimistic Lock Mechanism

```java
// File: compute/src/main/java/org/zstack/compute/allocator/HostCapacityUpdater.java

@Transactional
private boolean _run(HostCapacityUpdaterRunnable runnable) {
    if (!lockCapacity()) {
        logDeletedHost();
        return false;
    }

    HostCapacityVO cap = runnable.call(capacityVO);
    if (cap != null) {
        capacityVO = cap;
        merge();  // Persist changes
        return true;
    }
    return false;
}

private boolean lockCapacity() {
    // Use PESSIMISTIC_WRITE to lock the row
    capacityVO = dbf.getEntityManager().find(
        HostCapacityVO.class,
        hostUuid,
        LockModeType.PESSIMISTIC_WRITE
    );
    // ... copy original values for logging
    return capacityVO != null;
}
```

**Key Points**:
- Uses `LockModeType.PESSIMISTIC_WRITE` for row-level locking
- Annotated with `@DeadlockAutoRestart` to handle deadlock retries
- Entire update is wrapped in a `@Transactional` method

---

## 5. Overprovisioning Ratio Mechanism

### 5.1 CPU Overprovisioning

```java
// File: compute/src/main/java/org/zstack/compute/allocator/HostCpuOverProvisioningManagerImpl.java

public class HostCpuOverProvisioningManagerImpl implements HostCpuOverProvisioningManager {
    private Integer globalRatio;  // Global CPU overprovisioning ratio
    private ConcurrentHashMap<String, Integer> ratios = new ConcurrentHashMap<>();  // Per-host ratios

    @Override
    public int calculateHostCpuByRatio(String hostUuid, int cpuNum) {
        int r = getRatio(hostUuid);
        return cpuNum * r;  // totalCpu = cpuNum * ratio
    }

    @Override
    public int calculateByRatio(String hostUuid, int cpuNum) {
        int r = getRatio(hostUuid);
        int ret = Math.round((float)cpuNum / r);
        return ret == 0 ? 1 : ret;  // Reverse calculation for consumption
    }

    @Override
    public int getRatio(String hostUuid) {
        Integer r = ratios.get(hostUuid);
        return r == null
            ? rcf.getResourceConfigValue(HostGlobalConfig.HOST_CPU_OVER_PROVISIONING_RATIO, hostUuid, Integer.class)
            : r;
    }
}
```

**Calculation**:
- `totalCpu = cpuNum * cpuOverprovisioningRatio`
- Example: 8 physical CPUs with ratio 10 = 80 virtual CPUs available

### 5.2 Memory Overprovisioning

```java
// File: compute/src/main/java/org/zstack/compute/allocator/HostCapacityOverProvisioningManagerImpl.java

public class HostCapacityOverProvisioningManagerImpl implements HostCapacityOverProvisioningManager {
    private double globalMemoryRatio = 1;  // Default: no overprovisioning
    private ConcurrentHashMap<String, Double> hostMemoryRatio = new ConcurrentHashMap<>();

    @Override
    public long calculateMemoryByRatio(String hostUuid, long capacity) {
        double ratio = getMemoryRatio(hostUuid);
        return Math.round(capacity / ratio);  // Consumed memory = requested / ratio
    }

    @Override
    public long calculateHostAvailableMemoryByRatio(String hostUuid, long capacity) {
        double ratio = getMemoryRatio(hostUuid);
        return Math.round(capacity * ratio);  // Available = physical * ratio
    }
}
```

**Calculation**:
- When allocating 4GB with ratio 1.5: actually consumes `4GB / 1.5 = 2.67GB`
- When checking availability: `physicalAvailable * 1.5 = virtualAvailable`

---

## 6. Recommendations for Unified Hardware Management

### 6.1 Mechanisms That Can Be Reused

| Mechanism | Reusability | Notes |
|-----------|-------------|-------|
| `HostCapacityVO` structure | **High** | Can be extended or referenced for PhysicalServer capacity |
| `HostCapacityUpdater` with pessimistic locking | **High** | Pattern can be reused for PhysicalServerCapacity |
| `HostAllocatorChain` pattern | **High** | Filter chain pattern is extensible |
| `HostSortorChain` pattern | **High** | Sorting + reservation pattern is reusable |
| Overprovisioning managers | **High** | Logic is generic, can be applied to physical servers |
| Extension point architecture | **High** | Already plugin-based, easy to extend |

### 6.2 Extension Points for Physical Server Layer

1. **HostCapacityVO Enhancement**:
   - Current VO is tightly coupled to HostVO via foreign key
   - For unified management, consider:
     - Option A: Add a `PhysicalServerCapacityVO` with similar structure
     - Option B: Create a view or interface that abstracts both

2. **Allocation Flow Extension**:
   - Create new `AbstractHostAllocatorFlow` implementations for physical server constraints
   - Examples: `PhysicalServerStateAllocatorFlow`, `RackCapacityAllocatorFlow`

3. **Reserved Capacity Extension**:
   - Implement `HostReservedCapacityExtensionPoint` for different physical server types
   - E.g., reserve capacity for BMC, management agents

### 6.3 What NOT to Change

1. **Do NOT modify `HostCapacityVO` structure**:
   - It is deeply integrated with existing flows
   - Changing field types or removing fields will break compatibility

2. **Do NOT change allocation message interfaces**:
   - `AllocateHostMsg`, `AllocateHostReply` are stable APIs
   - Extend with new message types instead

3. **Do NOT duplicate overprovisioning logic**:
   - Reuse existing `HostCpuOverProvisioningManager` and `HostCapacityOverProvisioningManager`
   - Extend their interfaces if physical servers need different ratios

### 6.4 Suggested Abstraction Approach

```
+------------------------+
|  ComputeCapacityVO     |  (New abstract interface or base class)
+------------------------+
          ^
          |
    +-----+------+
    |            |
+---+---+  +-----+------+
|HostCap|  |PhysicalSrv |
|acityVO|  |CapacityVO  |
+-------+  +------------+
```

**Minimal Changes**:
1. Define `ComputeCapacityOperations` interface with `reserve()`, `release()`, `getAvailable()` methods
2. Have `HostCapacityUpdater` implement this for VMs
3. Create `PhysicalServerCapacityUpdater` implementing same interface
4. Allocation strategies can work with the interface, not concrete VOs

### 6.5 Key Invariants to Preserve

1. **Pessimistic locking for capacity updates** - Essential for consistency
2. **Extension point architecture** - New features should plug in, not modify core
3. **Async message-based allocation** - Keep `AllocateHostMsg`/`AllocateHostReply` pattern
4. **Rollback support in flows** - All capacity changes must be reversible

---

## 7. Key File References

| Category | File Path |
|----------|-----------|
| Core Manager | `H:\ZStack\zstack\compute\src\main\java\org\zstack\compute\allocator\HostAllocatorManagerImpl.java` |
| Allocator Chain | `H:\ZStack\zstack\compute\src\main\java\org\zstack\compute\allocator\HostAllocatorChain.java` |
| Sortor Chain | `H:\ZStack\zstack\compute\src\main\java\org\zstack\compute\allocator\HostSortorChain.java` |
| Capacity VO | `H:\ZStack\zstack\header\src\main\java\org\zstack\header\allocator\HostCapacityVO.java` |
| Capacity Updater | `H:\ZStack\zstack\compute\src\main\java\org\zstack\compute\allocator\HostCapacityUpdater.java` |
| Reserve Manager | `H:\ZStack\zstack\compute\src\main\java\org\zstack\compute\allocator\HostCapacityReserveManagerImpl.java` |
| CPU Overprovisioning | `H:\ZStack\zstack\compute\src\main\java\org\zstack\compute\allocator\HostCpuOverProvisioningManagerImpl.java` |
| Memory Overprovisioning | `H:\ZStack\zstack\compute\src\main\java\org\zstack\compute\allocator\HostCapacityOverProvisioningManagerImpl.java` |
| Capacity Filter Flow | `H:\ZStack\zstack\compute\src\main\java\org\zstack\compute\allocator\HostCapacityAllocatorFlow.java` |
| VM Allocation Flow | `H:\ZStack\zstack\compute\src\main\java\org\zstack\compute\vm\VmAllocateHostForStoppedVmFlow.java` |
| Strategy Interface | `H:\ZStack\zstack\header\src\main\java\org\zstack\header\allocator\HostAllocatorStrategy.java` |
| Spec Class | `H:\ZStack\zstack\header\src\main\java\org\zstack\header\allocator\HostAllocatorSpec.java` |
| Constants | `H:\ZStack\zstack\header\src\main\java\org\zstack\header\allocator\HostAllocatorConstant.java` |
| Global Config | `H:\ZStack\zstack\compute\src\main\java\org\zstack\compute\allocator\HostAllocatorGlobalConfig.java` |

---

## 8. Summary

The ZStack compute allocator module provides a robust, extensible architecture for host selection and capacity management:

1. **Chain-of-Responsibility Pattern**: Allocation flows filter candidates sequentially
2. **Strategy Pattern**: Different allocation strategies share common flows but differ in sorting/preference
3. **Pessimistic Locking**: Ensures consistency in concurrent allocation scenarios
4. **Extension Points**: Plugin architecture allows customization without core changes
5. **Overprovisioning Support**: Both CPU and memory support configurable overprovisioning ratios

For unified hardware management, the existing architecture is highly reusable. The recommended approach is to:
- Extend rather than modify existing VOs and interfaces
- Implement new allocator flows for physical server-specific constraints
- Reuse the capacity update mechanism with pessimistic locking
- Follow the existing extension point pattern for new functionality

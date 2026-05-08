package org.zstack.compute.allocator;

/**
 * Safety-buffer arithmetic shared by {@link PhysicalServerCapacityUpdater#_recalculate}
 * (writes {@code PhysicalServerCapacityVO.availableCpu/availableMemory}) and any
 * downstream hysteresis trigger that needs the same buffer floor + percent rule
 * (e.g. {@code ContainerNodeCordonService.evaluate}).
 *
 * <p>Reads {@link HostAllocatorGlobalConfig#PHYSICAL_SERVER_CPU_SAFETY_BUFFER_PERCENT}
 * and {@link HostAllocatorGlobalConfig#PHYSICAL_SERVER_MEMORY_SAFETY_BUFFER_PERCENT}
 * at call time — config changes take effect on the next recalculate without restart.
 *
 * <p>Floors mirror the constants previously inlined in {@link PhysicalServerCapacityUpdater}.
 */
public final class PhysicalServerCapacityBuffers {
    public static final long CPU_BUFFER_FLOOR = 4L;
    public static final long MEMORY_BUFFER_FLOOR = 4L * 1024L * 1024L * 1024L;

    public static long calcCpuBuffer(long totalCpu) {
        int pct = HostAllocatorGlobalConfig.PHYSICAL_SERVER_CPU_SAFETY_BUFFER_PERCENT
                .value(Integer.class);
        return Math.max(CPU_BUFFER_FLOOR, totalCpu * pct / 100);
    }

    public static long calcMemBuffer(long totalMemory) {
        int pct = HostAllocatorGlobalConfig.PHYSICAL_SERVER_MEMORY_SAFETY_BUFFER_PERCENT
                .value(Integer.class);
        return Math.max(MEMORY_BUFFER_FLOOR, totalMemory * pct / 100);
    }

    private PhysicalServerCapacityBuffers() {}
}

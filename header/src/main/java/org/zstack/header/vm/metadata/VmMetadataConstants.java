package org.zstack.header.vm.metadata;

public final class VmMetadataConstants {
    private VmMetadataConstants() {
        // utility class
    }

    public static final long SBLK_HEADER_SIZE = 4096L;

    public static final long SBLK_SLOT_HEADER_SIZE = 36L;

    public static final long SBLK_MAX_LV_SIZE = 64L * 1024 * 1024;

    public static long slotCapacity(long lvSize) {
        return ((lvSize - SBLK_HEADER_SIZE) / 2 / 4096) * 4096;
    }

    public static final long SBLK_MAX_SLOT_CAPACITY = slotCapacity(SBLK_MAX_LV_SIZE);

    public static final long SBLK_MAX_PAYLOAD_SIZE = SBLK_MAX_SLOT_CAPACITY - SBLK_SLOT_HEADER_SIZE;

    public static final long PAYLOAD_WARN_THRESHOLD = 8L * 1024 * 1024;

    public static final long PAYLOAD_REJECT_THRESHOLD = 30L * 1024 * 1024;
}

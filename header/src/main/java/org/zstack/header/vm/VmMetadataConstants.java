package org.zstack.header.vm;

/**
 * SharedBlock 元数据存储的容量常量与 Payload 大小保护阈值。
 *
 * <p>SharedBlock（sblk）使用固定大小的 LV 存储 VM 元数据，采用双 Slot 布局：
 * <pre>
 *   [ LV Header (4096B) ][ Slot-A ][ Slot-B ]
 *   Slot 大小 = (lvSize - headerSize) / 2，向下对齐到 4096
 *   Slot Header = 36B（Magic 4B + SeqNum 8B + SlotOffset 8B + SlotCapacity 8B + PayloadLen 8B）
 *   可用 Payload = SlotCapacity - SlotHeaderSize
 * </pre>
 *
 * @see <a href="vm-metadata-02b §10.0">Part 02b §10.0 容量公式与常量</a>
 */
public final class VmMetadataConstants {

    private VmMetadataConstants() {
        // utility class
    }

    /** LV 头部大小（字节） */
    public static final long SBLK_HEADER_SIZE = 4096L;

    /** Slot 头部大小（字节）：Magic(4) + SeqNum(8) + SlotOffset(8) + SlotCapacity(8) + PayloadLen(8) */
    public static final long SBLK_SLOT_HEADER_SIZE = 36L;

    /** SharedBlock 元数据 LV 最大大小（64MB） */
    public static final long SBLK_MAX_LV_SIZE = 64L * 1024 * 1024;

    /**
     * 计算给定 LV 大小下单个 Slot 的容量（字节）。
     *
     * <p>公式：((lvSize - headerSize) / 2 / 4096) * 4096（向下对齐到 4096）</p>
     *
     * @param lvSize LV 总大小（字节）
     * @return 单个 Slot 的容量（字节）
     */
    public static long slotCapacity(long lvSize) {
        return ((lvSize - SBLK_HEADER_SIZE) / 2 / 4096) * 4096;
    }

    /** 64MB LV 下单个 Slot 的最大容量（约 33,550,336 字节） */
    public static final long SBLK_MAX_SLOT_CAPACITY = slotCapacity(SBLK_MAX_LV_SIZE);

    /** 64MB LV 下单个 Slot 的最大可用 Payload（约 33,550,300 字节） */
    public static final long SBLK_MAX_PAYLOAD_SIZE = SBLK_MAX_SLOT_CAPACITY - SBLK_SLOT_HEADER_SIZE;

    /** Payload 大小预警阈值（8MB）：超过时输出 WARN 日志 */
    public static final long PAYLOAD_WARN_THRESHOLD = 8L * 1024 * 1024;

    /** Payload 大小拒绝阈值（30MB）：超过时 ERROR + 拒绝写入 */
    public static final long PAYLOAD_REJECT_THRESHOLD = 30L * 1024 * 1024;
}

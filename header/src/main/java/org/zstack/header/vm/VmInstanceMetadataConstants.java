package org.zstack.header.vm;

/**
 * 虚拟机元数据相关常量。
 */
public class VmInstanceMetadataConstants {

    private VmInstanceMetadataConstants() {
    }

    /**
     * 元数据 LV 后缀（sblk 场景）。
     *
     * <p>LV 命名规则：{vm_uuid}_vmmeta</p>
     */
    public static final String SBLK_LV_SUFFIX = "_vmmeta";

    /**
     * 元数据文件名（local/NFS 场景）。
     *
     * <p>文件位于与根盘同目录下。</p>
     */
    public static final String METADATA_FILE_NAME = "vm_metadata.json";

    /**
     * sblk 元数据 LV 默认初始大小（字节）：4MB。
     */
    public static final long SBLK_LV_INITIAL_SIZE = 4L * 1024 * 1024;

    /**
     * sblk 元数据 LV 最大大小（字节）：64MB。
     */
    public static final long SBLK_LV_MAX_SIZE = 64L * 1024 * 1024;

    /**
     * sblk 写入序列号最大值。溢出后回绕到 1。
     */
    public static final long MAX_WRITE_SEQUENCE = 0xFFFFFFFFFFFFFFFFL;

    /**
     * 全局配置：是否启用虚拟机元数据记录。
     *
     * <p>默认关闭。开启后，API 操作成功时自动触发元数据更新。</p>
     */
    public static final String GLOBAL_CONFIG_METADATA_ENABLED = "vm.metadata.enabled";

    /**
     * GC 初始延迟秒数。
     *
     * <p>API 成功后延迟该秒数再触发元数据更新，
     * 避免短时间内多次 API 操作产生过多无用更新。</p>
     */
    public static final int INITIAL_GC_DELAY_SECONDS = 5;

    /**
     * 注册虚拟机 MN 标识 System Tag 前缀。
     *
     * <p>注册过程中在 VM 上打标记，记录执行注册的 MN UUID，
     * 用于 MN 崩溃后的事务回滚判断。</p>
     */
    public static final String REGISTERING_MN_TAG_PREFIX = "vmMetadata::registeringMnUuid::";

    /**
     * VM 状态：注册中。
     *
     * <p>注册开始时 VM 进入此中间状态，注册完成后转为 Stopped。</p>
     */
    public static final String VM_STATE_REGISTERING = "Registering";

    /**
     * ChainTask 最大排队任务数。
     *
     * <p>同一 VM 的元数据更新 ChainTask 最多排队 1 个，
     * 超出的通过 exceedMaxPendingCallback 立即 Done。</p>
     */
    public static final int MAX_PENDING_METADATA_TASKS = 1;

    /**
     * ChainTask syncSignature 前缀。
     */
    public static final String CHAIN_TASK_SIGNATURE_PREFIX = "vm-metadata-update-";
}
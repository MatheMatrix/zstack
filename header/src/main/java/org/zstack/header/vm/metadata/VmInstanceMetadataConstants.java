package org.zstack.header.vm.metadata;

public class VmInstanceMetadataConstants {
    private VmInstanceMetadataConstants() {
    }

    public static final String SBLK_LV_SUFFIX = "_vmmeta";

    public static final String METADATA_DIR_NAME = "vm_metadata";

    public static final String SUMMARY_FILE_SUFFIX = ".json.summary";

    public static final long SBLK_LV_INITIAL_SIZE = 4L * 1024 * 1024;

    public static final long SBLK_LV_MAX_SIZE = 64L * 1024 * 1024;

    public static final long MAX_WRITE_SEQUENCE = 0xFFFFFFFFFFFFFFFFL;

    public static final String GLOBAL_CONFIG_METADATA_ENABLED = "vm.metadata.enabled";

    public static final int INITIAL_GC_DELAY_SECONDS = 5;

    public static final String REGISTERING_MN_TAG_PREFIX = "vmMetadata::registeringMnUuid::";

    public static final String VM_STATE_REGISTERING = "Registering";

    public static final int MAX_PENDING_METADATA_TASKS = 1;

    public static final String CHAIN_TASK_SIGNATURE_PREFIX = "vm-metadata-update-";
}
package org.zstack.header.storage.backup;

import java.io.Serializable;

public abstract class AbstractBackupMetadata implements Serializable {
    public abstract String getMetadataType();

    public static final String METADATA_TYPE_VOLUME = "Volume";
    public static final String METADATA_TYPE_TPM_STATE = "TpmState";
    public static final String METADATA_TYPE_NV_RAM = "NvRam";
}

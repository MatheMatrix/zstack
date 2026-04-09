package org.zstack.header.vm.devices;

import org.zstack.header.storage.backup.AbstractBackupMetadata;
import org.zstack.header.vm.additions.VmHostFileContentVO;
import com.google.gson.annotations.SerializedName;

public class NvRamBackupMetadata extends AbstractBackupMetadata {
    /**
     * from {@link VmHostFileContentVO#getContent()}
     */
    @SerializedName("nvram-content-b64")
    private String nvRamBase64;

    /**
     * from {@link VmHostFileContentVO#getFormat()}
     */
    @SerializedName("nvram-format")
    private String nvRamFormat;

    public String getNvRamBase64() {
        return nvRamBase64;
    }

    public void setNvRamBase64(String nvRamBase64) {
        this.nvRamBase64 = nvRamBase64;
    }

    public String getNvRamFormat() {
        return nvRamFormat;
    }

    public void setNvRamFormat(String nvRamFormat) {
        this.nvRamFormat = nvRamFormat;
    }

    @Override
    public String getMetadataType() {
        return METADATA_TYPE_NV_RAM;
    }
}

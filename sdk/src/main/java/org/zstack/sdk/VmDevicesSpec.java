package org.zstack.sdk;

import org.zstack.sdk.tpm.TpmSpec;
import org.zstack.sdk.NvRamSpec;

public class VmDevicesSpec  {

    public TpmSpec tpm;
    public void setTpm(TpmSpec tpm) {
        this.tpm = tpm;
    }
    public TpmSpec getTpm() {
        return this.tpm;
    }

    public NvRamSpec nvRam;
    public void setNvRam(NvRamSpec nvRam) {
        this.nvRam = nvRam;
    }
    public NvRamSpec getNvRam() {
        return this.nvRam;
    }

}

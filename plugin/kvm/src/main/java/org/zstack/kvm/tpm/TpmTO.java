package org.zstack.kvm.tpm;

import java.io.Serializable;

public class TpmTO implements Serializable {
    private String keyProviderUuid;
    private String secretUuid;
    private String installPath;

    public String getKeyProviderUuid() { return keyProviderUuid; }
    public void setKeyProviderUuid(String v) { this.keyProviderUuid = v; }
    public String getSecretUuid() { return secretUuid; }
    public void setSecretUuid(String v) { this.secretUuid = v; }
    public String getInstallPath() { return installPath; }
    public void setInstallPath(String v) { this.installPath = v; }
}

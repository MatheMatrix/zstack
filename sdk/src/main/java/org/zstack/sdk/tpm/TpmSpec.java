package org.zstack.sdk.tpm;



public class TpmSpec  {

    public boolean enable;
    public void setEnable(boolean enable) {
        this.enable = enable;
    }
    public boolean getEnable() {
        return this.enable;
    }

    public java.lang.String tpmUuid;
    public void setTpmUuid(java.lang.String tpmUuid) {
        this.tpmUuid = tpmUuid;
    }
    public java.lang.String getTpmUuid() {
        return this.tpmUuid;
    }

    public java.lang.String keyProviderUuid;
    public void setKeyProviderUuid(java.lang.String keyProviderUuid) {
        this.keyProviderUuid = keyProviderUuid;
    }
    public java.lang.String getKeyProviderUuid() {
        return this.keyProviderUuid;
    }

}

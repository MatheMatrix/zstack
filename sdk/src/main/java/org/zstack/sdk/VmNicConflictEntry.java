package org.zstack.sdk;

import org.zstack.sdk.VmNicInventory;

public class VmNicConflictEntry {
    public java.lang.String ip;
    public void setIp(java.lang.String ip) {
        this.ip = ip;
    }
    public java.lang.String getIp() {
        return this.ip;
    }

    public java.lang.String mac;
    public void setMac(java.lang.String mac) {
        this.mac = mac;
    }
    public java.lang.String getMac() {
        return this.mac;
    }

    public VmNicInventory vmNicInventory;
    public void setVmNicInventory(VmNicInventory vmNicInventory) {
        this.vmNicInventory = vmNicInventory;
    }
    public VmNicInventory getVmNicInventory() {
        return this.vmNicInventory;
    }

    public java.lang.String vmInstanceUuid;
    public void setVmInstanceUuid(java.lang.String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }
    public java.lang.String getVmInstanceUuid() {
        return this.vmInstanceUuid;
    }

    public java.lang.String vmInstanceName;
    public void setVmInstanceName(java.lang.String vmInstanceName) {
        this.vmInstanceName = vmInstanceName;
    }
    public java.lang.String getVmInstanceName() {
        return this.vmInstanceName;
    }

}

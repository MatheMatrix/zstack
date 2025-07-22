package org.zstack.header.vm;

public class NetworkConflict {
    public String ip;
    public String mac;
    public VmNicInventory vmNicInventory;
    public String vmInstanceUuid;
    public String vmInstanceName;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public VmNicInventory getVmNicInventory() {
        return vmNicInventory;
    }

    public void setVmNicInventory(VmNicInventory vmNicInventory) {
        this.vmNicInventory = vmNicInventory;
    }

    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public String getVmInstanceName() {
        return vmInstanceName;
    }

    public void setVmInstanceName(String vmInstanceName) {
        this.vmInstanceName = vmInstanceName;
    }
}

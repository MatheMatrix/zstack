package org.zstack.sdk.zcex.entity;



public class ZceXInventory  {

    public java.lang.String uuid;
    public void setUuid(java.lang.String uuid) {
        this.uuid = uuid;
    }
    public java.lang.String getUuid() {
        return this.uuid;
    }

    public java.lang.String name;
    public void setName(java.lang.String name) {
        this.name = name;
    }
    public java.lang.String getName() {
        return this.name;
    }

    public java.lang.String managementIp;
    public void setManagementIp(java.lang.String managementIp) {
        this.managementIp = managementIp;
    }
    public java.lang.String getManagementIp() {
        return this.managementIp;
    }

    public int apiPort;
    public void setApiPort(int apiPort) {
        this.apiPort = apiPort;
    }
    public int getApiPort() {
        return this.apiPort;
    }

    public java.sql.Timestamp createDate;
    public void setCreateDate(java.sql.Timestamp createDate) {
        this.createDate = createDate;
    }
    public java.sql.Timestamp getCreateDate() {
        return this.createDate;
    }

    public java.sql.Timestamp lastOpDate;
    public void setLastOpDate(java.sql.Timestamp lastOpDate) {
        this.lastOpDate = lastOpDate;
    }
    public java.sql.Timestamp getLastOpDate() {
        return this.lastOpDate;
    }

}

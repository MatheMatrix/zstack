package org.zstack.sdk;

public class PhysicalServerResourceAssignmentInventory {
    public java.lang.String uuid;
    public java.lang.String serverUuid;
    public java.lang.String roleType;
    public java.lang.String cpuSet;
    public java.lang.Long memory;
    public java.lang.String state;
    public java.sql.Timestamp createDate;
    public java.sql.Timestamp lastOpDate;

    public void setUuid(java.lang.String uuid) { this.uuid = uuid; }
    public java.lang.String getUuid() { return uuid; }
    public void setServerUuid(java.lang.String serverUuid) { this.serverUuid = serverUuid; }
    public java.lang.String getServerUuid() { return serverUuid; }
    public void setRoleType(java.lang.String roleType) { this.roleType = roleType; }
    public java.lang.String getRoleType() { return roleType; }
    public void setCpuSet(java.lang.String cpuSet) { this.cpuSet = cpuSet; }
    public java.lang.String getCpuSet() { return cpuSet; }
    public void setMemory(java.lang.Long memory) { this.memory = memory; }
    public java.lang.Long getMemory() { return memory; }
    public void setState(java.lang.String state) { this.state = state; }
    public java.lang.String getState() { return state; }
    public void setCreateDate(java.sql.Timestamp createDate) { this.createDate = createDate; }
    public java.sql.Timestamp getCreateDate() { return createDate; }
    public void setLastOpDate(java.sql.Timestamp lastOpDate) { this.lastOpDate = lastOpDate; }
    public java.sql.Timestamp getLastOpDate() { return lastOpDate; }
}

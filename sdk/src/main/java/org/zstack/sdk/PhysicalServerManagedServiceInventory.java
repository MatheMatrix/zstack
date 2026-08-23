package org.zstack.sdk;

public class PhysicalServerManagedServiceInventory {
    public String roleType;
    public String serviceName;
    public boolean restartable;
    public String state;
    public String cpuSet;
    public Long cpuTime;
    public Long memory;
    public Long memoryLimit;

    public void setRoleType(String roleType) { this.roleType = roleType; }
    public String getRoleType() { return roleType; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getServiceName() { return serviceName; }
    public void setRestartable(boolean restartable) { this.restartable = restartable; }
    public boolean getRestartable() { return restartable; }
    public void setState(String state) { this.state = state; }
    public String getState() { return state; }
    public void setCpuSet(String cpuSet) { this.cpuSet = cpuSet; }
    public String getCpuSet() { return cpuSet; }
    public void setCpuTime(Long cpuTime) { this.cpuTime = cpuTime; }
    public Long getCpuTime() { return cpuTime; }
    public void setMemory(Long memory) { this.memory = memory; }
    public Long getMemory() { return memory; }
    public void setMemoryLimit(Long memoryLimit) { this.memoryLimit = memoryLimit; }
    public Long getMemoryLimit() { return memoryLimit; }
}

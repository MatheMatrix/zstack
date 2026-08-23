package org.zstack.storage.zbs;

public class ZbsCpuIsolationUpdate {
    private String operation;
    private String desiredCpuSet;
    private Long memory;

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getDesiredCpuSet() {
        return desiredCpuSet;
    }

    public void setDesiredCpuSet(String desiredCpuSet) {
        this.desiredCpuSet = desiredCpuSet;
    }

    public Long getMemory() {
        return memory;
    }

    public void setMemory(Long memory) {
        this.memory = memory;
    }
}

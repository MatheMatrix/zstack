package org.zstack.cbd;

/**
 * @author Xingwei Yu
 * @date 2024/7/17 14:08
 */
public class LogicalPoolInfo {
    private String logicalPoolName;
    private String physicalPoolName;
    private long totalCapacity;
    private long availableCapacity;

    public String getLogicalPoolName() {
        return logicalPoolName;
    }

    public void setLogicalPoolName(String logicalPoolName) {
        this.logicalPoolName = logicalPoolName;
    }

    public String getPhysicalPoolName() {
        return physicalPoolName;
    }

    public void setPhysicalPoolName(String physicalPoolName) {
        this.physicalPoolName = physicalPoolName;
    }

    public long getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(long totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    public long getAvailableCapacity() {
        return availableCapacity;
    }

    public void setAvailableCapacity(long availableCapacity) {
        this.availableCapacity = availableCapacity;
    }
}

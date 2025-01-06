package org.zstack.cbd;

/**
 * @author Xingwei Yu
 * @date 2025/1/6 14:06
 */
public class LogicalPoolInfo {
    /**
     * [root@zbs-8 ~]# zbs list logical-pool --format json
     * {
     *   "error": {
     *     "code": 0,
     *     "message": "success"
     *   },
     *   "result": [
     *     {
     *       "statusCode": 0,
     *       "logicalPoolInfos": [
     *         {
     *           "logicalPoolID": 1,
     *           "logicalPoolName": "pool-e0f7d92fadb3446bb06ac0e48918172a",
     *           "physicalPoolID": 1,
     *           "physicalPoolName": "pool-e0f7d92fadb3446bb06ac0e48918172a_physical",
     *           "type": 0,
     *           "createTime": 1735627690,
     *           "redundanceAndPlaceMentPolicy": "eyJjb3B5c2V0TnVtIjo2MDAsInJlcGxpY2FOdW0iOjMsInpvbmVOdW0iOjN9Cg==",
     *           "userPolicy": "eyJwb2xpY3kiIDogMX0=",
     *           "allocateStatus": 0,
     *           "capacity": 42504271429632,
     *           "usedSize": 84297121792,
     *           "allocatedSize": 129922760704,
     *           "rawUsedSize": 252891365376,
     *           "rawWalUsedSize": 3123609600,
     *           "quota": 0
     *         }
     *       ]
     *     }
     *   ]
     * }
     * [root@zbs-8 ~]#
     */
    private long physicalPoolID;
    private String redundanceAndPlaceMentPolicy;
    private long logicalPoolID;
    private long usedSize;
    private long quota;
    private long createTime;
    private int type;
    private long rawWalUsedSize;
    private int allocateStatus;
    private long rawUsedSize;
    private String physicalPoolName;
    private long capacity;
    private String logicalPoolName;
    private String userPolicy;
    private long allocatedSize;

    public long getPhysicalPoolID() {
        return physicalPoolID;
    }

    public void setPhysicalPoolID(long physicalPoolID) {
        this.physicalPoolID = physicalPoolID;
    }

    public String getRedundanceAndPlaceMentPolicy() {
        return redundanceAndPlaceMentPolicy;
    }

    public void setRedundanceAndPlaceMentPolicy(String redundanceAndPlaceMentPolicy) {
        this.redundanceAndPlaceMentPolicy = redundanceAndPlaceMentPolicy;
    }

    public long getLogicalPoolID() {
        return logicalPoolID;
    }

    public void setLogicalPoolID(long logicalPoolID) {
        this.logicalPoolID = logicalPoolID;
    }

    public long getUsedSize() {
        return usedSize;
    }

    public void setUsedSize(long usedSize) {
        this.usedSize = usedSize;
    }

    public long getQuota() {
        return quota;
    }

    public void setQuota(long quota) {
        this.quota = quota;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getRawWalUsedSize() {
        return rawWalUsedSize;
    }

    public void setRawWalUsedSize(long rawWalUsedSize) {
        this.rawWalUsedSize = rawWalUsedSize;
    }

    public int getAllocateStatus() {
        return allocateStatus;
    }

    public void setAllocateStatus(int allocateStatus) {
        this.allocateStatus = allocateStatus;
    }

    public long getRawUsedSize() {
        return rawUsedSize;
    }

    public void setRawUsedSize(long rawUsedSize) {
        this.rawUsedSize = rawUsedSize;
    }

    public String getPhysicalPoolName() {
        return physicalPoolName;
    }

    public void setPhysicalPoolName(String physicalPoolName) {
        this.physicalPoolName = physicalPoolName;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public String getLogicalPoolName() {
        return logicalPoolName;
    }

    public void setLogicalPoolName(String logicalPoolName) {
        this.logicalPoolName = logicalPoolName;
    }

    public String getUserPolicy() {
        return userPolicy;
    }

    public void setUserPolicy(String userPolicy) {
        this.userPolicy = userPolicy;
    }

    public long getAllocatedSize() {
        return allocatedSize;
    }

    public void setAllocatedSize(long allocatedSize) {
        this.allocatedSize = allocatedSize;
    }
}

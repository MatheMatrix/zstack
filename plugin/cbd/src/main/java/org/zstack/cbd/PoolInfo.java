package org.zstack.cbd;

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

/**
 * @author Xingwei Yu
 * @date 2025/1/6 14:06
 */
public class PoolInfo {
    private String logicalPoolID;
    private String logicalPoolName;
    private long physicalPoolID;
    private long physicalPoolName;
    private String type;
    private String createTime;
    private String redundanceAndPlaceMentPolicy;
    private String userPolicy;
    private String allocateStatus;
    private String capacity;
    private String usedSize;
    private String allocatedSize;
    private String rawUsedSize;
    private String rawWalUsedSize;
    private String quota;

    public String getLogicalPoolID() {
        return logicalPoolID;
    }

    public void setLogicalPoolID(String logicalPoolID) {
        this.logicalPoolID = logicalPoolID;
    }

    public String getLogicalPoolName() {
        return logicalPoolName;
    }

    public void setLogicalPoolName(String logicalPoolName) {
        this.logicalPoolName = logicalPoolName;
    }

    public long getPhysicalPoolID() {
        return physicalPoolID;
    }

    public void setPhysicalPoolID(long physicalPoolID) {
        this.physicalPoolID = physicalPoolID;
    }

    public long getPhysicalPoolName() {
        return physicalPoolName;
    }

    public void setPhysicalPoolName(long physicalPoolName) {
        this.physicalPoolName = physicalPoolName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getRedundanceAndPlaceMentPolicy() {
        return redundanceAndPlaceMentPolicy;
    }

    public void setRedundanceAndPlaceMentPolicy(String redundanceAndPlaceMentPolicy) {
        this.redundanceAndPlaceMentPolicy = redundanceAndPlaceMentPolicy;
    }

    public String getUserPolicy() {
        return userPolicy;
    }

    public void setUserPolicy(String userPolicy) {
        this.userPolicy = userPolicy;
    }

    public String getAllocateStatus() {
        return allocateStatus;
    }

    public void setAllocateStatus(String allocateStatus) {
        this.allocateStatus = allocateStatus;
    }

    public String getCapacity() {
        return capacity;
    }

    public void setCapacity(String capacity) {
        this.capacity = capacity;
    }

    public String getUsedSize() {
        return usedSize;
    }

    public void setUsedSize(String usedSize) {
        this.usedSize = usedSize;
    }

    public String getAllocatedSize() {
        return allocatedSize;
    }

    public void setAllocatedSize(String allocatedSize) {
        this.allocatedSize = allocatedSize;
    }

    public String getRawUsedSize() {
        return rawUsedSize;
    }

    public void setRawUsedSize(String rawUsedSize) {
        this.rawUsedSize = rawUsedSize;
    }

    public String getRawWalUsedSize() {
        return rawWalUsedSize;
    }

    public void setRawWalUsedSize(String rawWalUsedSize) {
        this.rawWalUsedSize = rawWalUsedSize;
    }

    public String getQuota() {
        return quota;
    }

    public void setQuota(String quota) {
        this.quota = quota;
    }
}

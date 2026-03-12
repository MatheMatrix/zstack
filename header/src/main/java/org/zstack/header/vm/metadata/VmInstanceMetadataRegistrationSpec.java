package org.zstack.header.vm.metadata;

/**
 * 虚拟机元数据注册参数�?
 *
 * <p>封装从元数据注册虚拟机时需要的新环境上下文信息�?/p>
 *
 * <p>字段处理矩阵中标记为"API 参数"�?替换"的字段，其新值来源于此对象�?/p>
 */
public class VmInstanceMetadataRegistrationSpec {

    /**
     * 注册目标 Zone UUID（必填）�?
     *
     * <p>替换 VmInstanceVO.zoneUuid�?/p>
     */
    private String zoneUuid;

    /**
     * 注册目标主存�?UUID（必填）�?
     *
     * <p>替换 VolumeVO.primaryStorageUuid、VolumeSnapshotVO.primaryStorageUuid�?/p>
     */
    private String primaryStorageUuid;

    /**
     * 注册操作的账�?UUID�?
     *
     * <p>替换所�?VO �?accountUuid 字段。通常�?admin�?/p>
     */
    private String accountUuid;

    /**
     * 旧存储路径标识符�?
     *
     * <ul>
     *   <li>sblk 场景：旧 VG UUID</li>
     *   <li>local/NFS 场景：旧路径前缀（如 /vms_ds�?/li>
     * </ul>
     */
    private String oldPathIdentifier;

    /**
     * 新存储路径标识符�?
     *
     * <ul>
     *   <li>sblk 场景：新 VG UUID</li>
     *   <li>local/NFS 场景：新路径前缀（如 /vms_ds2�?/li>
     * </ul>
     */
    private String newPathIdentifier;

    public String getZoneUuid() {
        return zoneUuid;
    }

    public void setZoneUuid(String zoneUuid) {
        this.zoneUuid = zoneUuid;
    }

    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    public String getAccountUuid() {
        return accountUuid;
    }

    public void setAccountUuid(String accountUuid) {
        this.accountUuid = accountUuid;
    }

    public String getOldPathIdentifier() {
        return oldPathIdentifier;
    }

    public void setOldPathIdentifier(String oldPathIdentifier) {
        this.oldPathIdentifier = oldPathIdentifier;
    }

    public String getNewPathIdentifier() {
        return newPathIdentifier;
    }

    public void setNewPathIdentifier(String newPathIdentifier) {
        this.newPathIdentifier = newPathIdentifier;
    }
}
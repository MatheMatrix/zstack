package org.zstack.header.vm;

import org.zstack.header.message.NeedReplyMessage;

/**
 * Internal message for LongJob integration of VM registration from metadata.
 * Mirrors fields from APIRegisterVmInstanceFromMetadataMsg.
 */
public class RegisterVmFromMetadataInnerMsg extends NeedReplyMessage {
    private String metadataContent;
    private String targetPrimaryStorageUuid;
    private String zoneUuid;
    private String clusterUuid;
    private Boolean forceVersionMismatch;
    private String accountUuid;

    public String getMetadataContent() {
        return metadataContent;
    }

    public void setMetadataContent(String metadataContent) {
        this.metadataContent = metadataContent;
    }

    public String getTargetPrimaryStorageUuid() {
        return targetPrimaryStorageUuid;
    }

    public void setTargetPrimaryStorageUuid(String targetPrimaryStorageUuid) {
        this.targetPrimaryStorageUuid = targetPrimaryStorageUuid;
    }

    public String getZoneUuid() {
        return zoneUuid;
    }

    public void setZoneUuid(String zoneUuid) {
        this.zoneUuid = zoneUuid;
    }

    public String getClusterUuid() {
        return clusterUuid;
    }

    public void setClusterUuid(String clusterUuid) {
        this.clusterUuid = clusterUuid;
    }

    public Boolean getForceVersionMismatch() {
        return forceVersionMismatch;
    }

    public void setForceVersionMismatch(Boolean forceVersionMismatch) {
        this.forceVersionMismatch = forceVersionMismatch;
    }

    public String getAccountUuid() {
        return accountUuid;
    }

    public void setAccountUuid(String accountUuid) {
        this.accountUuid = accountUuid;
    }
}

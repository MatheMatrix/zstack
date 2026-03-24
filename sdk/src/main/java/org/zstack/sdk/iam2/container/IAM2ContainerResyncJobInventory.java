package org.zstack.sdk.iam2.container;

import org.zstack.sdk.iam2.container.IAM2ContainerResyncJobStatus;
import org.zstack.sdk.iam2.container.IAM2ContainerResyncScope;

public class IAM2ContainerResyncJobInventory  {

    public java.lang.String uuid;
    public void setUuid(java.lang.String uuid) {
        this.uuid = uuid;
    }
    public java.lang.String getUuid() {
        return this.uuid;
    }

    public IAM2ContainerResyncJobStatus status;
    public void setStatus(IAM2ContainerResyncJobStatus status) {
        this.status = status;
    }
    public IAM2ContainerResyncJobStatus getStatus() {
        return this.status;
    }

    public IAM2ContainerResyncScope scope;
    public void setScope(IAM2ContainerResyncScope scope) {
        this.scope = scope;
    }
    public IAM2ContainerResyncScope getScope() {
        return this.scope;
    }

    public java.lang.Boolean dryRun;
    public void setDryRun(java.lang.Boolean dryRun) {
        this.dryRun = dryRun;
    }
    public java.lang.Boolean getDryRun() {
        return this.dryRun;
    }

    public java.lang.String containerUuid;
    public void setContainerUuid(java.lang.String containerUuid) {
        this.containerUuid = containerUuid;
    }
    public java.lang.String getContainerUuid() {
        return this.containerUuid;
    }

    public java.lang.Long clusterId;
    public void setClusterId(java.lang.Long clusterId) {
        this.clusterId = clusterId;
    }
    public java.lang.Long getClusterId() {
        return this.clusterId;
    }

    public java.lang.String reason;
    public void setReason(java.lang.String reason) {
        this.reason = reason;
    }
    public java.lang.String getReason() {
        return this.reason;
    }

    public java.lang.Long totalCount;
    public void setTotalCount(java.lang.Long totalCount) {
        this.totalCount = totalCount;
    }
    public java.lang.Long getTotalCount() {
        return this.totalCount;
    }

    public java.lang.Long successCount;
    public void setSuccessCount(java.lang.Long successCount) {
        this.successCount = successCount;
    }
    public java.lang.Long getSuccessCount() {
        return this.successCount;
    }

    public java.lang.Long failedCount;
    public void setFailedCount(java.lang.Long failedCount) {
        this.failedCount = failedCount;
    }
    public java.lang.Long getFailedCount() {
        return this.failedCount;
    }

    public java.lang.Long skippedCount;
    public void setSkippedCount(java.lang.Long skippedCount) {
        this.skippedCount = skippedCount;
    }
    public java.lang.Long getSkippedCount() {
        return this.skippedCount;
    }

    public java.util.List items;
    public void setItems(java.util.List items) {
        this.items = items;
    }
    public java.util.List getItems() {
        return this.items;
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

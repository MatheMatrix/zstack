package org.zstack.sdk.iam2.container;

import org.zstack.sdk.iam2.container.IAM2ContainerResyncPhase;
import org.zstack.sdk.iam2.container.IAM2ContainerResyncItemStatus;
import org.zstack.sdk.iam2.container.IAM2ContainerResyncErrorType;

public class IAM2ContainerResyncItemInventory  {

    public java.lang.String uuid;
    public void setUuid(java.lang.String uuid) {
        this.uuid = uuid;
    }
    public java.lang.String getUuid() {
        return this.uuid;
    }

    public java.lang.String projectUuid;
    public void setProjectUuid(java.lang.String projectUuid) {
        this.projectUuid = projectUuid;
    }
    public java.lang.String getProjectUuid() {
        return this.projectUuid;
    }

    public IAM2ContainerResyncPhase phase;
    public void setPhase(IAM2ContainerResyncPhase phase) {
        this.phase = phase;
    }
    public IAM2ContainerResyncPhase getPhase() {
        return this.phase;
    }

    public IAM2ContainerResyncItemStatus status;
    public void setStatus(IAM2ContainerResyncItemStatus status) {
        this.status = status;
    }
    public IAM2ContainerResyncItemStatus getStatus() {
        return this.status;
    }

    public IAM2ContainerResyncErrorType errorType;
    public void setErrorType(IAM2ContainerResyncErrorType errorType) {
        this.errorType = errorType;
    }
    public IAM2ContainerResyncErrorType getErrorType() {
        return this.errorType;
    }

    public java.lang.String error;
    public void setError(java.lang.String error) {
        this.error = error;
    }
    public java.lang.String getError() {
        return this.error;
    }

    public java.lang.String message;
    public void setMessage(java.lang.String message) {
        this.message = message;
    }
    public java.lang.String getMessage() {
        return this.message;
    }

    public java.lang.Integer retryCount;
    public void setRetryCount(java.lang.Integer retryCount) {
        this.retryCount = retryCount;
    }
    public java.lang.Integer getRetryCount() {
        return this.retryCount;
    }

    public java.lang.Long elapsedMs;
    public void setElapsedMs(java.lang.Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
    public java.lang.Long getElapsedMs() {
        return this.elapsedMs;
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

package org.zstack.sdk;

import org.zstack.sdk.ErrorCode;

public class ErrorCode  {

    public java.lang.String code;
    public void setCode(java.lang.String code) {
        this.code = code;
    }
    public java.lang.String getCode() {
        return this.code;
    }

    public java.lang.String description;
    public void setDescription(java.lang.String description) {
        this.description = description;
    }
    public java.lang.String getDescription() {
        return this.description;
    }

    public java.lang.String details;
    public void setDetails(java.lang.String details) {
        this.details = details;
    }
    public java.lang.String getDetails() {
        return this.details;
    }

    public java.lang.String elaboration;
    public void setElaboration(java.lang.String elaboration) {
        this.elaboration = elaboration;
    }
    public java.lang.String getElaboration() {
        return this.elaboration;
    }

    public java.lang.String location;
    public void setLocation(java.lang.String location) {
        this.location = location;
    }
    public java.lang.String getLocation() {
        return this.location;
    }

    public java.lang.String cost;
    public void setCost(java.lang.String cost) {
        this.cost = cost;
    }
    public java.lang.String getCost() {
        return this.cost;
    }

    public ErrorCode cause;
    public void setCause(ErrorCode cause) {
        this.cause = cause;
    }
    public ErrorCode getCause() {
        return this.cause;
    }

    public java.util.LinkedHashMap opaque;
    public void setOpaque(java.util.LinkedHashMap opaque) {
        this.opaque = opaque;
    }
    public java.util.LinkedHashMap getOpaque() {
        return this.opaque;
    }

    public java.lang.String globalErrorCode;
    public void setGlobalErrorCode(java.lang.String globalErrorCode) {
        this.globalErrorCode = globalErrorCode;
    }
    public java.lang.String getGlobalErrorCode() {
        return this.globalErrorCode;
    }

    public java.lang.String message;
    public void setMessage(java.lang.String message) {
        this.message = message;
    }
    public java.lang.String getMessage() {
        return this.message;
    }

    public java.lang.String category;
    public void setCategory(java.lang.String category) {
        this.category = category;
    }
    public java.lang.String getCategory() {
        return this.category;
    }

    public java.lang.String messageKey;
    public void setMessageKey(java.lang.String messageKey) {
        this.messageKey = messageKey;
    }
    public java.lang.String getMessageKey() {
        return this.messageKey;
    }

    public java.util.Map params;
    public void setParams(java.util.Map params) {
        this.params = params;
    }
    public java.util.Map getParams() {
        return this.params;
    }

    public java.lang.String localizedMessage;
    public void setLocalizedMessage(java.lang.String localizedMessage) {
        this.localizedMessage = localizedMessage;
    }
    public java.lang.String getLocalizedMessage() {
        return this.localizedMessage;
    }

    public java.lang.Boolean retryable;
    public void setRetryable(java.lang.Boolean retryable) {
        this.retryable = retryable;
    }
    public java.lang.Boolean getRetryable() {
        return this.retryable;
    }

    public java.lang.Integer httpStatus;
    public void setHttpStatus(java.lang.Integer httpStatus) {
        this.httpStatus = httpStatus;
    }
    public java.lang.Integer getHttpStatus() {
        return this.httpStatus;
    }

    public java.lang.String[] formatArgs;
    public void setFormatArgs(java.lang.String[] formatArgs) {
        this.formatArgs = formatArgs;
    }
    public java.lang.String[] getFormatArgs() {
        return this.formatArgs;
    }

    public java.lang.String getDisplayMessage() {
        if (this.localizedMessage != null) {
            return this.localizedMessage;
        }
        if (this.details != null) {
            return this.details;
        }
        return this.description;
    }

}

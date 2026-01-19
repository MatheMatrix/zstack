package org.zstack.sdk.zwatch.resnotify;



public class ResNotifyWebhookRefInventory  {

    public java.lang.String uuid;
    public void setUuid(java.lang.String uuid) {
        this.uuid = uuid;
    }
    public java.lang.String getUuid() {
        return this.uuid;
    }

    public java.lang.String webhookUrl;
    public void setWebhookUrl(java.lang.String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }
    public java.lang.String getWebhookUrl() {
        return this.webhookUrl;
    }

    public java.lang.String secret;
    public void setSecret(java.lang.String secret) {
        this.secret = secret;
    }
    public java.lang.String getSecret() {
        return this.secret;
    }

    public java.lang.String customHeaders;
    public void setCustomHeaders(java.lang.String customHeaders) {
        this.customHeaders = customHeaders;
    }
    public java.lang.String getCustomHeaders() {
        return this.customHeaders;
    }

    public java.sql.Timestamp lastDeliveryTime;
    public void setLastDeliveryTime(java.sql.Timestamp lastDeliveryTime) {
        this.lastDeliveryTime = lastDeliveryTime;
    }
    public java.sql.Timestamp getLastDeliveryTime() {
        return this.lastDeliveryTime;
    }

    public java.lang.Integer consecutiveFailures;
    public void setConsecutiveFailures(java.lang.Integer consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }
    public java.lang.Integer getConsecutiveFailures() {
        return this.consecutiveFailures;
    }

}

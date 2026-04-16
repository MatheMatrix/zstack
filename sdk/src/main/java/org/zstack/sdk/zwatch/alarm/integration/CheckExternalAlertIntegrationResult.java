package org.zstack.sdk.zwatch.alarm.integration;



public class CheckExternalAlertIntegrationResult {
    public boolean healthy;
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }
    public boolean getHealthy() {
        return this.healthy;
    }

    public boolean endpointExists;
    public void setEndpointExists(boolean endpointExists) {
        this.endpointExists = endpointExists;
    }
    public boolean getEndpointExists() {
        return this.endpointExists;
    }

    public boolean urlMatched;
    public void setUrlMatched(boolean urlMatched) {
        this.urlMatched = urlMatched;
    }
    public boolean getUrlMatched() {
        return this.urlMatched;
    }

    public boolean subscribed;
    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
    }
    public boolean getSubscribed() {
        return this.subscribed;
    }

    public boolean templateExists;
    public void setTemplateExists(boolean templateExists) {
        this.templateExists = templateExists;
    }
    public boolean getTemplateExists() {
        return this.templateExists;
    }

}

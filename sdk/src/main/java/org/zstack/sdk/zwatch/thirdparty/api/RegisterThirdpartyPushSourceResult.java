package org.zstack.sdk.zwatch.thirdparty.api;

import org.zstack.sdk.zwatch.thirdparty.entity.ThirdpartyPlatformInventory;

public class RegisterThirdpartyPushSourceResult {
    public ThirdpartyPlatformInventory inventory;
    public void setInventory(ThirdpartyPlatformInventory inventory) {
        this.inventory = inventory;
    }
    public ThirdpartyPlatformInventory getInventory() {
        return this.inventory;
    }

    public java.lang.String platformUuid;
    public void setPlatformUuid(java.lang.String platformUuid) {
        this.platformUuid = platformUuid;
    }
    public java.lang.String getPlatformUuid() {
        return this.platformUuid;
    }

    public java.lang.String pushEndpoint;
    public void setPushEndpoint(java.lang.String pushEndpoint) {
        this.pushEndpoint = pushEndpoint;
    }
    public java.lang.String getPushEndpoint() {
        return this.pushEndpoint;
    }

}

package org.zstack.sdk.zcex.api;

import org.zstack.sdk.zwatch.thirdparty.entity.ThirdpartyPlatformInventory;
import org.zstack.sdk.zcex.entity.ZceXThirdPartyPlatformAlertRefInventory;

public class CreateZceXAlertPlatformResult {
    public ThirdpartyPlatformInventory thirdPartyPlatform;
    public void setThirdPartyPlatform(ThirdpartyPlatformInventory thirdPartyPlatform) {
        this.thirdPartyPlatform = thirdPartyPlatform;
    }
    public ThirdpartyPlatformInventory getThirdPartyPlatform() {
        return this.thirdPartyPlatform;
    }

    public ZceXThirdPartyPlatformAlertRefInventory inventory;
    public void setInventory(ZceXThirdPartyPlatformAlertRefInventory inventory) {
        this.inventory = inventory;
    }
    public ZceXThirdPartyPlatformAlertRefInventory getInventory() {
        return this.inventory;
    }

}

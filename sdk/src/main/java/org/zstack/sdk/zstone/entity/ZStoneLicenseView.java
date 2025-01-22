package org.zstack.sdk.zstone.entity;

import org.zstack.sdk.zstone.entity.ZStoneLicenseInventory;

public class ZStoneLicenseView  {

    public ZStoneLicenseInventory platform;
    public void setPlatform(ZStoneLicenseInventory platform) {
        this.platform = platform;
    }
    public ZStoneLicenseInventory getPlatform() {
        return this.platform;
    }

    public java.util.List addOns;
    public void setAddOns(java.util.List addOns) {
        this.addOns = addOns;
    }
    public java.util.List getAddOns() {
        return this.addOns;
    }

}

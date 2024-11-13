package org.zstack.sdk.zstone.api;

import org.zstack.sdk.zstone.entity.ZStoneLicenseView;

public class GetZStoneCapabilityResult {
    public ZStoneLicenseView licenses;
    public void setLicenses(ZStoneLicenseView licenses) {
        this.licenses = licenses;
    }
    public ZStoneLicenseView getLicenses() {
        return this.licenses;
    }

    public java.util.List clusters;
    public void setClusters(java.util.List clusters) {
        this.clusters = clusters;
    }
    public java.util.List getClusters() {
        return this.clusters;
    }

}

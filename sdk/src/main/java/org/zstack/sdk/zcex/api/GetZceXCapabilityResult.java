package org.zstack.sdk.zcex.api;

import org.zstack.sdk.zcex.entity.ZceXLicenseView;
import org.zstack.sdk.zcex.entity.ZceXClusterView;

public class GetZceXCapabilityResult {
    public ZceXLicenseView licenses;
    public void setLicenses(ZceXLicenseView licenses) {
        this.licenses = licenses;
    }
    public ZceXLicenseView getLicenses() {
        return this.licenses;
    }

    public ZceXClusterView cluster;
    public void setCluster(ZceXClusterView cluster) {
        this.cluster = cluster;
    }
    public ZceXClusterView getCluster() {
        return this.cluster;
    }

}

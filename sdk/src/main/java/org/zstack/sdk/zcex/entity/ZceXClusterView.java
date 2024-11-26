package org.zstack.sdk.zcex.entity;

import org.zstack.sdk.zcex.entity.ZceXHostSummaryView;
import org.zstack.sdk.zcex.entity.ZceXPoolSummaryView;

public class ZceXClusterView  {

    public java.lang.String managementNetworkCidr;
    public void setManagementNetworkCidr(java.lang.String managementNetworkCidr) {
        this.managementNetworkCidr = managementNetworkCidr;
    }
    public java.lang.String getManagementNetworkCidr() {
        return this.managementNetworkCidr;
    }

    public java.lang.String gatewayNetworkCidr;
    public void setGatewayNetworkCidr(java.lang.String gatewayNetworkCidr) {
        this.gatewayNetworkCidr = gatewayNetworkCidr;
    }
    public java.lang.String getGatewayNetworkCidr() {
        return this.gatewayNetworkCidr;
    }

    public java.lang.String publicNetworkCidr;
    public void setPublicNetworkCidr(java.lang.String publicNetworkCidr) {
        this.publicNetworkCidr = publicNetworkCidr;
    }
    public java.lang.String getPublicNetworkCidr() {
        return this.publicNetworkCidr;
    }

    public java.lang.String clusterNetworkCidr;
    public void setClusterNetworkCidr(java.lang.String clusterNetworkCidr) {
        this.clusterNetworkCidr = clusterNetworkCidr;
    }
    public java.lang.String getClusterNetworkCidr() {
        return this.clusterNetworkCidr;
    }

    public ZceXHostSummaryView hosts;
    public void setHosts(ZceXHostSummaryView hosts) {
        this.hosts = hosts;
    }
    public ZceXHostSummaryView getHosts() {
        return this.hosts;
    }

    public ZceXPoolSummaryView pools;
    public void setPools(ZceXPoolSummaryView pools) {
        this.pools = pools;
    }
    public ZceXPoolSummaryView getPools() {
        return this.pools;
    }

}

package org.zstack.sdk.zstone.entity;

import org.zstack.sdk.zstone.entity.ZStoneHostSummaryView;
import org.zstack.sdk.zstone.entity.ZStonePoolSummaryView;

public class ZStoneClusterView  {

    public java.lang.String uuid;
    public void setUuid(java.lang.String uuid) {
        this.uuid = uuid;
    }
    public java.lang.String getUuid() {
        return this.uuid;
    }

    public boolean first;
    public void setFirst(boolean first) {
        this.first = first;
    }
    public boolean getFirst() {
        return this.first;
    }

    public java.lang.String managementNetworkCidr;
    public void setManagementNetworkCidr(java.lang.String managementNetworkCidr) {
        this.managementNetworkCidr = managementNetworkCidr;
    }
    public java.lang.String getManagementNetworkCidr() {
        return this.managementNetworkCidr;
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

    public java.lang.String chronyIp;
    public void setChronyIp(java.lang.String chronyIp) {
        this.chronyIp = chronyIp;
    }
    public java.lang.String getChronyIp() {
        return this.chronyIp;
    }

    public java.lang.String type;
    public void setType(java.lang.String type) {
        this.type = type;
    }
    public java.lang.String getType() {
        return this.type;
    }

    public ZStoneHostSummaryView hosts;
    public void setHosts(ZStoneHostSummaryView hosts) {
        this.hosts = hosts;
    }
    public ZStoneHostSummaryView getHosts() {
        return this.hosts;
    }

    public ZStonePoolSummaryView pools;
    public void setPools(ZStonePoolSummaryView pools) {
        this.pools = pools;
    }
    public ZStonePoolSummaryView getPools() {
        return this.pools;
    }

}

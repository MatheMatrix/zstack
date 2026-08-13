package org.zstack.sdk.network.zns;

import org.zstack.sdk.network.zns.ZnsSegmentProjectionPlan;
import org.zstack.sdk.network.zns.ZnsSegmentRefInventory;
import org.zstack.sdk.L2NetworkInventory;
import org.zstack.sdk.L3NetworkInventory;

public class UseZnsSegmentResult {
    public ZnsSegmentProjectionPlan plan;
    public void setPlan(ZnsSegmentProjectionPlan plan) {
        this.plan = plan;
    }
    public ZnsSegmentProjectionPlan getPlan() {
        return this.plan;
    }

    public ZnsSegmentRefInventory relation;
    public void setRelation(ZnsSegmentRefInventory relation) {
        this.relation = relation;
    }
    public ZnsSegmentRefInventory getRelation() {
        return this.relation;
    }

    public L2NetworkInventory l2Network;
    public void setL2Network(L2NetworkInventory l2Network) {
        this.l2Network = l2Network;
    }
    public L2NetworkInventory getL2Network() {
        return this.l2Network;
    }

    public L3NetworkInventory l3Network;
    public void setL3Network(L3NetworkInventory l3Network) {
        this.l3Network = l3Network;
    }
    public L3NetworkInventory getL3Network() {
        return this.l3Network;
    }

    public java.util.List ipRanges;
    public void setIpRanges(java.util.List ipRanges) {
        this.ipRanges = ipRanges;
    }
    public java.util.List getIpRanges() {
        return this.ipRanges;
    }

}

package org.zstack.network.l2;

import org.zstack.header.network.l2.*;
import org.zstack.header.network.service.NetworkServiceConstants;
import org.zstack.network.service.NetworkServiceGlobalProperty;

public class L2VlanNetwork extends L2NoVlanNetwork implements L2NetworkDefaultMtu {
    
    public L2VlanNetwork(L2NetworkVO self) {
        super(self);
    }


    public L2VlanNetwork() {
    }
    
    private L2VlanNetworkVO getSelf() {
        return (L2VlanNetworkVO) self;
    }

    @Override
    protected L2NetworkInventory getSelfInventory() {
        return L2VlanNetworkInventory.valueOf(getSelf());
    }

    @Override
    public String getL2NetworkType() {
        return L2NetworkConstant.L2_VLAN_NETWORK_TYPE;
    }

    @Override
    public Integer getDefaultMtu() {
        return NetworkServiceGlobalProperty.DHCP_MTU_VLAN;
    }
}

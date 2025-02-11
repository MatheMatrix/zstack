package org.zstack.sdnController;

import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.header.core.workflow.FlowChain;
import org.zstack.header.network.l2.L2NetworkInventory;
import org.zstack.sdnController.header.SdnControllerVO;

public interface SdnControllerFactory {
    SdnControllerType getVendorType();

    SdnController getSdnController(SdnControllerVO vo);

    default SdnController getSdnController(String l2NetworkUuid) {return null;};

    default FlowChain getSyncChain() {return FlowChainBuilder.newSimpleFlowChain();};
}

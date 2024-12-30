package org.zstack.sdnController;

import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.network.l2.L2NetworkInventory;
import org.zstack.header.vm.VmNicInventory;
import org.zstack.network.l2.vxlan.vxlanNetwork.L2VxlanNetworkInventory;
import org.zstack.sdnController.header.*;

import java.util.List;
import java.util.Map;

public interface SdnController {

    void handleMessage(SdnControllerMessage msg);
    /*
    有关sdn控制器的前置检查: pre-event
    对sdn控制器的控制: event
    有关sdn控制器的后置处理: post-event
     */
    void preInitSdnController(APIAddSdnControllerMsg msg, Completion completion);
    void initSdnController(APIAddSdnControllerMsg msg, Completion completion);
    void postInitSdnController(SdnControllerVO vo, Completion completion);

    void preCreateVxlanNetwork(L2VxlanNetworkInventory vxlan, List<String> systemTags, Completion completion);
    void createL2Network(L2NetworkInventory inv, List<String> systemTags, Completion completion);
    void postCreateVxlanNetwork(L2VxlanNetworkInventory vxlan, List<String> systemTags, Completion completion);

    void preAttachL2NetworkToCluster(L2VxlanNetworkInventory vxlan, List<String> systemTags, Completion completion);
    void attachL2NetworkToCluster(L2VxlanNetworkInventory vxlan, List<String> systemTags, Completion completion);
    void postAttachL2NetworkToCluster(L2VxlanNetworkInventory vxlan, List<String> systemTags, Completion completion);

    void deleteSdnController(SdnControllerDeletionMsg msg, SdnControllerInventory sdn, Completion completion);
    void detachL2NetworkFromCluster(L2VxlanNetworkInventory vxlan, String clusterUuid, Completion completion);
    void deleteL2Network(L2NetworkInventory inv, Completion completion);

    List<SdnVniRange> getVniRange(SdnControllerInventory controller);
    List<SdnVlanRange> getVlanRange(SdnControllerInventory controller);

    default void addHost(APISdnControllerAddHostMsg msg, Completion completion) {completion.success();};
    default void removeHost(APISdnControllerRemoveHostMsg msg, Completion completion) {completion.success();};
    default void addLogicalPorts(List<VmNicInventory> nics, Completion completion) {completion.success();};
    default void removeLogicalPorts(List<VmNicInventory> nics, Completion completion) {completion.success();};
}

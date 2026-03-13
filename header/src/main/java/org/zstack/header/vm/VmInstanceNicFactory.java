package org.zstack.header.vm;

import org.zstack.header.core.Completion;
import org.zstack.header.network.l2.VSwitchType;
import org.zstack.header.network.l3.UsedIpInventory;

import java.util.List;

public interface VmInstanceNicFactory {
    VmNicType getType();
    VmNicVO createVmNic(VmNicInventory inv, VmInstanceSpec spec);

    static VmNicVO createVmNic(VmNicInventory nic) {
        VmNicVO vnic = new VmNicVO();
        vnic.setUuid(nic.getUuid());
        vnic.setIp(nic.getIp());
        vnic.setL3NetworkUuid(nic.getL3NetworkUuid());
        vnic.setUsedIpUuid(nic.getUsedIpUuid());
        vnic.setVmInstanceUuid(nic.getVmInstanceUuid());
        vnic.setDeviceId(nic.getDeviceId());
        vnic.setMac(nic.getMac());
        vnic.setHypervisorType(nic.getHypervisorType());
        vnic.setNetmask(nic.getNetmask());
        vnic.setGateway(nic.getGateway());
        vnic.setIpVersion(nic.getIpVersion());
        vnic.setInternalName(nic.getInternalName());
        vnic.setDriverType(nic.getDriverType());
        vnic.setMetaData(nic.getMetaData());
        vnic.setState(VmNicState.fromState(nic.getState()));
        return vnic;
    }

    default boolean addFdbForEipNameSpace(VmNicInventory nic) {
        return false;
    }

    default String  getPhysicalNicName(VmNicInventory nic) {
        return null;
    }

    default void releaseVmNic(VmNicInventory nic) {
        return;
    }

    /**
     * Called after VmNicVO is persisted in VmAllocateNicFlow.
     * SDN controllers (e.g. ZNS) use this to create network ports and allocate IPs.
     */
    default void afterCreateVmNic(VmNicInventory nic, VmInstanceSpec spec, Completion completion) {
        completion.success();
    }

    /**
     * Called before NIC resources are released in VmReturnReleaseNicFlow.
     * SDN controllers (e.g. ZNS) use this to delete network ports.
     */
    default void beforeReleaseVmNic(VmNicInventory nic, Completion completion) {
        completion.success();
    }

}

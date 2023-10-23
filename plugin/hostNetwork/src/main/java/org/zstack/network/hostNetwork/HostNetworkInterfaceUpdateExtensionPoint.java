package org.zstack.network.hostNetwork;

import java.util.List;

public interface HostNetworkInterfaceUpdateExtensionPoint {
    public void afterCreated(String hostUuid, List<HostNetworkInterfaceVO> interfaceVOS, List<HostNetworkBondingVO> bondingVOS);
}

package org.zstack.header.network.l2;


import java.util.ArrayList;
import java.util.List;

public interface L2NetworkGetInterfaceExtensionPoint {
    default List<String> getPhysicalInterfaceNames(String l2NetworkUuid, String hostUuid) {
        return new ArrayList<>();
    }
    L2NetworkType getType();
    default boolean isPhysicalInterfaceOccupied(String interfaceName, String hostUuid) {
        return false;
    }
}

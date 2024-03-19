package org.zstack.header.allocator;

import java.util.List;

/**
 */
public interface HostReservedCapacityExtensionPoint {
    String getHypervisorTypeForHostReserveCapacityExtension();

    ReservedHostCapacity getReservedHostCapacity(String hostUuid);

    ReservedHostCapacity getReservedHostsCapacity(List<String> hostUuids);
}

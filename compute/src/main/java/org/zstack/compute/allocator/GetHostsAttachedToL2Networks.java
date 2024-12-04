package org.zstack.compute.allocator;

import java.util.List;

public interface GetHostsAttachedToL2Networks {
    List<String> GetHostsAttachedToL2Networks(String vswitchType, List<String> l2Uuids);
}

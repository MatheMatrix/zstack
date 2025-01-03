package org.zstack.compute.allocator;

import java.util.List;

public interface AttachedL2NetworkAllocatorExtensionPoint {
    List<String> filter(List<String> hostUuids, List<String> l2Uuids);
}

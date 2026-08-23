package org.zstack.storage.zbs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ZbsNodeRef {
    private String serverUuid;
    private final Set<String> primaryStorageUuids = new LinkedHashSet<>();
    private final Set<String> nodeAddresses = new LinkedHashSet<>();
    private int sourceRefCount;
    private String reasonCode;

    public String getServerUuid() {
        return serverUuid;
    }

    public void setServerUuid(String serverUuid) {
        this.serverUuid = serverUuid;
    }

    public List<String> getPrimaryStorageUuids() {
        return new ArrayList<>(primaryStorageUuids);
    }

    public void addPrimaryStorageUuid(String primaryStorageUuid) {
        primaryStorageUuids.add(primaryStorageUuid);
    }

    public List<String> getNodeAddresses() {
        return new ArrayList<>(nodeAddresses);
    }

    public void addNodeAddress(String nodeAddress) {
        nodeAddresses.add(nodeAddress);
    }

    public int getSourceRefCount() {
        return sourceRefCount;
    }

    public void incrementSourceRefCount() {
        sourceRefCount++;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public boolean includesAnyPrimaryStorage(Collection<String> uuids) {
        for (String uuid : uuids) {
            if (primaryStorageUuids.contains(uuid)) {
                return true;
            }
        }
        return false;
    }
}

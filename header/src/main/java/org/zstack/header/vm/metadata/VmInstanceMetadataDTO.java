package org.zstack.header.vm.metadata;

import java.util.List;

public class VmInstanceMetadataDTO {
    public String schemaVersion;
    public VmMetadataCategory vmCategory;
    public String cacheVmInstanceUuid;
    public ResourceMetadata vm;
    public List<VolumeResourceMetadata> volumes;
    public List<ResourceMetadata> nics;
    public List<String> snapshots;
    public List<String> snapshotGroups;
    public List<String> snapshotGroupRefs;
}

package org.zstack.header.vm;

/**
 * Created by LiangHanYu on 2022/4/13 13:24
 */
public interface UpdateVmInstanceMetadataExtensionPoint {
    void buildVmInstanceMetadata(VmInstanceInventory vmInstanceInventory, VmMetadata vmMetadata);
}

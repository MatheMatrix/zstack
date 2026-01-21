package org.zstack.header.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VmMetadata {
    public String vmInstanceVO;
    // key = vmUuid
    // value = SystemTag
    public List<String> vmSystemTags = new ArrayList<>();
    // key = vmUuid
    // value = ResourceConfig
    public List<String> vmResourceConfigs = new ArrayList<>();

    public List<String> volumeVOs = new ArrayList<>();
    // key = volumeUuid
    // value = SystemTag
    public Map<String, List<String>> volumeSystemTags = new HashMap<>();
    // key = volumeUuid
    // value = ResourceConfig
    public Map<String, List<String>> volumeResourceConfigs = new HashMap<>();

    public List<String> vmNicVOs = new ArrayList<>();
    // key = nicUuid
    // value = SystemTag
    public Map<String, List<String>> vmNicSystemTags = new HashMap<>();
    // key = nicUuid
    // value = ResourceConfig
    public Map<String, List<String>> vmNicResourceConfigs = new HashMap<>();

    // key = volumeUuid
    // value = List<VolumeSnapshotVO.toString>
    public Map<String, List<String>> volumeSnapshots = new HashMap<>();

    // VolumeSnapshotGroupVO.toString
    public List<String> volumeSnapshotGroupVO = new ArrayList<>();
    // VolumeSnapshotGroupRefVO.toString
    public List<String> volumeSnapshotGroupRefVO = new ArrayList<>();

    // key = volumeUuid
    // value = VolumeSnapshotReferenceVO.toString
    public Map<String, String> volumeSnapshotReferenceVO = new HashMap<>();
    // key = volumeUuid
    // value = VolumeSnapshotReferenceTreeVO.toString
    public Map<String, String> volumeSnapshotReferenceTreeVO = new HashMap<>();

    // key = volumeUuid vmUuid
    // value = vo
    public Map<String, String> EncryptedResourceKeyRefVO = new HashMap<>();
}

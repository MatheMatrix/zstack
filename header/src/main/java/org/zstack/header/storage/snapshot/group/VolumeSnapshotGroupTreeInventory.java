package org.zstack.header.storage.snapshot.group;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class VolumeSnapshotGroupTreeInventory {
    private String uuid;
    private String name;
    private String description;
    private String vmInstanceUuid;
    private Timestamp createDate;
    private Timestamp lastOpDate;
    private boolean current;
    private boolean incomplete;
    private String parentGroupUuid;
    private List<VolumeSnapshotGroupTreeInventory> children = new ArrayList<>();
    private List<VolumeSnapshotGroupTreeRefInventory> refs = new ArrayList<>();

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public Timestamp getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Timestamp createDate) {
        this.createDate = createDate;
    }

    public Timestamp getLastOpDate() {
        return lastOpDate;
    }

    public void setLastOpDate(Timestamp lastOpDate) {
        this.lastOpDate = lastOpDate;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    public boolean isIncomplete() {
        return incomplete;
    }

    public void setIncomplete(boolean incomplete) {
        this.incomplete = incomplete;
    }

    public String getParentGroupUuid() {
        return parentGroupUuid;
    }

    public void setParentGroupUuid(String parentGroupUuid) {
        this.parentGroupUuid = parentGroupUuid;
    }

    public List<VolumeSnapshotGroupTreeInventory> getChildren() {
        return children;
    }

    public void setChildren(List<VolumeSnapshotGroupTreeInventory> children) {
        this.children = children;
    }

    public List<VolumeSnapshotGroupTreeRefInventory> getRefs() {
        return refs;
    }

    public void setRefs(List<VolumeSnapshotGroupTreeRefInventory> refs) {
        this.refs = refs;
    }
}

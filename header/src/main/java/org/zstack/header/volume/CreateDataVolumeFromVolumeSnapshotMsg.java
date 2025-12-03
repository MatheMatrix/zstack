package org.zstack.header.volume;

import org.zstack.header.identity.SessionInventory;
import org.zstack.header.message.NeedReplyMessage;

public class CreateDataVolumeFromVolumeSnapshotMsg extends NeedReplyMessage {
    private String name;
    private String description;
    private String volumeSnapshotUuid;
    private SessionInventory session;
    private long size;

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

    public String getVolumeSnapshotUuid() {
        return volumeSnapshotUuid;
    }

    public void setVolumeSnapshotUuid(String volumeSnapshotUuid) {
        this.volumeSnapshotUuid = volumeSnapshotUuid;
    }

    public SessionInventory getSession() {
        return session;
    }

    public void setSession(SessionInventory session) {
        this.session = session;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}

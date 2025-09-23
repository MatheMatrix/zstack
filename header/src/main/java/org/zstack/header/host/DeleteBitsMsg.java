package org.zstack.header.host;

import org.zstack.header.message.DeletionMessage;

public class DeleteBitsMsg extends DeletionMessage implements HostMessage {
    private String hostUuid;
    String path;
    public boolean folder = false;

    @Override
    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isFolder() {
        return folder;
    }

    public void setFolder(boolean folder) {
        this.folder = folder;
    }
}

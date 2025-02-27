package org.zstack.header.storage.addon;

public class IscsiRemoteTarget extends BlockRemoteTarget {
    private String transport = "tcp";

    private String iqn;

    private String ip;

    private int port;

    private String diskId;

    private String diskIdType;

    public String getDiskIdType() {
        return diskIdType;
    }

    public void setDiskIdType(String diskIdType) {
        this.diskIdType = diskIdType;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String getResourceURI() {
        return String.format("iscsi://%s:%s/%s/%s_%s", ip, port, iqn, diskIdType, diskId);
    }

    public String getDiskId() {
        return diskId;
    }

    public void setDiskId(String diskId) {
        this.diskId = diskId;
    }

    public String getIqn() {
        return iqn;
    }

    public void setIqn(String iqn) {
        this.iqn = iqn;
    }

    public enum DiskIdType {
        wwn,
        serial
    }
}

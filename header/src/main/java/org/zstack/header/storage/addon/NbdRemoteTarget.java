package org.zstack.header.storage.addon;

public class NbdRemoteTarget extends BlockRemoteTarget {
    private String ip;
    private int port;
    private long size;
    private String installPath;

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

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public String getInstallPath() {
        return installPath;
    }

    @Override
    public String getResourceURI() {
        return String.format("nbd://%s:%s/%d", ip, port, size);
    }
}

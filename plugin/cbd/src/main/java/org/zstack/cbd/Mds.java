package org.zstack.cbd;

/**
 * @author Xingwei Yu
 * @date 2024/4/10 23:18
 */
public class Mds {
    private String username;
    private String password;
    private int sshPort = 22;
    private String addr;
    private String externalAddr;
    private int mdsPort = 6666;
    private MdsStatus status;
    private String version;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getSshPort() {
        return sshPort;
    }

    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public String getExternalAddr() {
        return externalAddr;
    }

    public void setExternalAddr(String externalAddr) {
        this.externalAddr = externalAddr;
    }

    public int getMdsPort() {
        return mdsPort;
    }

    public void setMdsPort(int mdsPort) {
        this.mdsPort = mdsPort;
    }

    public MdsStatus getStatus() {
        return status;
    }

    public void setStatus(MdsStatus status) {
        this.status = status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}

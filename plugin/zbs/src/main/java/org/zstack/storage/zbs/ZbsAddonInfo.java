package org.zstack.storage.zbs;

import java.util.List;

/**
 * @author Xingwei Yu
 * @date 2024/4/1 18:12
 */
public class ZbsAddonInfo {
    public List<Mds> mds;

    public List<Mds> getMds() {
        return mds;
    }

    public void setMds(List<Mds> mds) {
        this.mds = mds;
    }

    public static class Mds {
        public String sshUsername;
        public String sshPassword;
        public String hostname;
        public String mdsAddr;
        public int sshPort = 22;
        public int mdsPort = 6666;
        public MdsStatus status;

        public String getSshUsername() {
            return sshUsername;
        }

        public void setSshUsername(String sshUsername) {
            this.sshUsername = sshUsername;
        }

        public String getSshPassword() {
            return sshPassword;
        }

        public void setSshPassword(String sshPassword) {
            this.sshPassword = sshPassword;
        }

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public String getMdsAddr() {
            return mdsAddr;
        }

        public void setMdsAddr(String mdsAddr) {
            this.mdsAddr = mdsAddr;
        }

        public int getSshPort() {
            return sshPort;
        }

        public void setSshPort(int sshPort) {
            this.sshPort = sshPort;
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
    }
}

package org.zstack.header.storage.ceph;

import org.zstack.header.message.MessageReply;

public class SiblingFenceVmOnHostReply extends MessageReply {
    private boolean alive;
    private boolean killed;
    private boolean sshReachable;
    private boolean qemuFound;
    private String executorHostUuid;
    private String executorRole;
    private String reason;

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public boolean isKilled() {
        return killed;
    }

    public void setKilled(boolean killed) {
        this.killed = killed;
    }

    public boolean isSshReachable() {
        return sshReachable;
    }

    public void setSshReachable(boolean sshReachable) {
        this.sshReachable = sshReachable;
    }

    public boolean isQemuFound() {
        return qemuFound;
    }

    public void setQemuFound(boolean qemuFound) {
        this.qemuFound = qemuFound;
    }

    public String getExecutorHostUuid() {
        return executorHostUuid;
    }

    public void setExecutorHostUuid(String executorHostUuid) {
        this.executorHostUuid = executorHostUuid;
    }

    public String getExecutorRole() {
        return executorRole;
    }

    public void setExecutorRole(String executorRole) {
        this.executorRole = executorRole;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

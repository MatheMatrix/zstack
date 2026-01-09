package org.zstack.header.vm;

import java.io.Serializable;
import java.util.List;

public class ConsistencyCheckResult implements Serializable {
    private String vmUuid;
    private boolean consistent;
    private List<DiffEntry> diffs;
    private String action;

    public String getVmUuid() {
        return vmUuid;
    }

    public void setVmUuid(String vmUuid) {
        this.vmUuid = vmUuid;
    }

    public boolean isConsistent() {
        return consistent;
    }

    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }

    public List<DiffEntry> getDiffs() {
        return diffs;
    }

    public void setDiffs(List<DiffEntry> diffs) {
        this.diffs = diffs;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}

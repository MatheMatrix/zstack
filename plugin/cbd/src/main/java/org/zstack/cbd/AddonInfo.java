package org.zstack.cbd;

import java.util.List;

/**
 * @author Xingwei Yu
 * @date 2024/4/1 18:12
 */
public class AddonInfo {
    public List<MdsInfo> mdsInfos;
    public List<LogicalPoolInfo> logicalPoolInfos;

    public List<MdsInfo> getMdsInfos() {
        return mdsInfos;
    }

    public void setMdsInfos(List<MdsInfo> mdsInfos) {
        this.mdsInfos = mdsInfos;
    }

    public List<LogicalPoolInfo> getLogicalPoolInfos() {
        return logicalPoolInfos;
    }

    public void setLogicalPoolInfos(List<LogicalPoolInfo> logicalPoolInfos) {
        this.logicalPoolInfos = logicalPoolInfos;
    }
}

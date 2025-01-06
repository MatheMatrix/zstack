package org.zstack.cbd;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Xingwei Yu
 * @date 2024/4/1 18:12
 */
public class AddonInfo {
    private List<MdsInfo> mdsInfos = new ArrayList<>();;
    private PoolInfo poolInfo;

    public List<MdsInfo> getMdsInfos() {
        return mdsInfos;
    }

    public void setMdsInfos(List<MdsInfo> mdsInfos) {
        this.mdsInfos = mdsInfos;
    }

    public PoolInfo getPoolInfo() {
        return poolInfo;
    }

    public void setPoolInfo(PoolInfo poolInfo) {
        this.poolInfo = poolInfo;
    }
}

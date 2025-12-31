package org.zstack.storage.zbs;

import java.util.Collections;
import org.zstack.utils.gson.JSONObjectUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Xingwei Yu
 * @date 2024/4/1 18:12
 */
public class AddonInfo implements org.zstack.header.storage.addon.primary.AddonInfo {
    private ClusterInfo clusterInfo;
    private List<MdsInfo> mdsInfos = Collections.synchronizedList(new ArrayList<>());
    private List<LogicalPoolInfo> logicalPoolInfos = Collections.synchronizedList(new ArrayList<>());

    public ClusterInfo getClusterInfo() {
        return clusterInfo;
    }

    public void setClusterInfo(ClusterInfo clusterInfo) {
        this.clusterInfo = clusterInfo;
    }

    public List<MdsInfo> getMdsInfos() {
        return mdsInfos;
    }

    public void setMdsInfos(List<MdsInfo> mdsInfos) {
        int size = mdsInfos == null ? 0 : mdsInfos.size();
        List<MdsInfo> newList = Collections.synchronizedList(new ArrayList<>(size));
        if (size > 0) {
            newList.addAll(mdsInfos);
        }
        this.mdsInfos = newList;
    }

    public List<LogicalPoolInfo> getLogicalPoolInfos() {
        return logicalPoolInfos;
    }

    public void setLogicalPoolInfos(List<LogicalPoolInfo> logicalPoolInfos) {
        int size = logicalPoolInfos == null ? 0 : logicalPoolInfos.size();
        List<LogicalPoolInfo> newList = Collections.synchronizedList(new ArrayList<>(size));
        if (size > 0) {
            newList.addAll(logicalPoolInfos);
        }
        this.logicalPoolInfos = newList;
    }

    public void addLogicalPoolInfo(LogicalPoolInfo logicalPoolInfo) {
        this.logicalPoolInfos.add(logicalPoolInfo);
    }

    @Override
    public boolean changed(String infoJson) {
        return !JSONObjectUtil.toJsonString(this).equals(infoJson);
    }
}

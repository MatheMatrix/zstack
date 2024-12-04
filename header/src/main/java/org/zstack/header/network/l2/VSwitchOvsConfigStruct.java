package org.zstack.header.network.l2;

import java.util.List;

public class VSwitchOvsConfigStruct {
    private String vswitchType;
    private List<String> nicList;
    private String vtepIp;

    public String getVswitchType() {
        return vswitchType;
    }

    public void setVswitchType(String vswitchType) {
        this.vswitchType = vswitchType;
    }

    public List<String> getNicList() {
        return nicList;
    }

    public void setNicList(List<String> nicList) {
        this.nicList = nicList;
    }

    public void setVtepIp(String vtepIp) {
        this.vtepIp = vtepIp;
    }

    public String getVtepIp() {
        return vtepIp;
    }
}

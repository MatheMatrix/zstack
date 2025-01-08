package org.zstack.header.network.l2;

import java.util.List;

public class VSwitchOvsConfigStruct {
    private String ovnControllerIp;
    private String vSwitchType;
    private String brExName;
    private List<String> nicNames;
    private String ovnEncapIP;
    private String ovnRemoteConnection;
    private String ovnEncapType;
    private int hugePageNumber;

    public String getvSwitchType() {
        return vSwitchType;
    }

    public void setvSwitchType(String vSwitchType) {
        this.vSwitchType = vSwitchType;
    }

    public String getOvnControllerIp() {
        return ovnControllerIp;
    }

    public void setOvnControllerIp(String ovnControllerIp) {
        this.ovnControllerIp = ovnControllerIp;
    }

    public String getBrExName() {
        return brExName;
    }

    public void setBrExName(String brExName) {
        this.brExName = brExName;
    }

    public List<String> getNicNames() {
        return nicNames;
    }

    public void setNicNames(List<String> nicNames) {
        this.nicNames = nicNames;
    }

    public String getOvnEncapIP() {
        return ovnEncapIP;
    }

    public void setOvnEncapIP(String ovnEncapIP) {
        this.ovnEncapIP = ovnEncapIP;
    }

    public String getOvnRemoteConnection() {
        return ovnRemoteConnection;
    }

    public void setOvnRemoteConnection(String ovnRemoteConnection) {
        this.ovnRemoteConnection = ovnRemoteConnection;
    }

    public String getOvnEncapType() {
        return ovnEncapType;
    }

    public void setOvnEncapType(String ovnEncapType) {
        this.ovnEncapType = ovnEncapType;
    }

    public int getHugePageNumber() {
        return hugePageNumber;
    }

    public void setHugePageNumber(int hugePageNumber) {
        this.hugePageNumber = hugePageNumber;
    }
}

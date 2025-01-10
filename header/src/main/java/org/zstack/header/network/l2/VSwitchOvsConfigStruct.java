package org.zstack.header.network.l2;

import java.util.Map;

public class VSwitchOvsConfigStruct {
    private String ovnControllerIp;
    private String vSwitchType;
    private String brExName;
    private Map<String, String> nicNamePciMap;
    private Map<String, String> nicNameDriverMap;
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

    public Map<String, String> getNicNamePciMap() {
        return nicNamePciMap;
    }

    public void setNicNamePciMap(Map<String, String> nicNamePciMap) {
        this.nicNamePciMap = nicNamePciMap;
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

    public Map<String, String> getNicNameDriverMap() {
        return nicNameDriverMap;
    }

    public void setNicNameDriverMap(Map<String, String> nicNameDriverMap) {
        this.nicNameDriverMap = nicNameDriverMap;
    }
}

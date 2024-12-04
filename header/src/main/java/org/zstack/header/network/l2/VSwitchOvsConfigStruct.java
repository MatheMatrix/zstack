package org.zstack.header.network.l2;

import java.util.Map;

public class VSwitchOvsConfigStruct {
    private boolean hostStatusCheck = true;

    private String ovnControllerIp;
    private String vSwitchType;
    private String brExName;
    private Map<String, String> nicNamePciMap;
    private Map<String, String> nicNameDriverMap;
    private String ovnEncapIP;
    private String ovnEncapNetmask;
    private String ovnRemoteConnection;
    private String ovnEncapType;
    private int hugePageNumber;
    private String bondMode;
    private String lacpMode;

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

    public boolean isHostStatusCheck() {
        return hostStatusCheck;
    }

    public void setHostStatusCheck(boolean hostStatusCheck) {
        this.hostStatusCheck = hostStatusCheck;
    }

    public String getBondMode() {
        return bondMode;
    }

    public void setBondMode(String bondMode) {
        this.bondMode = bondMode;
    }

    public String getLacpMode() {
        return lacpMode;
    }

    public void setLacpMode(String lacpMode) {
        this.lacpMode = lacpMode;
    }

    public String getOvnEncapNetmask() {
        return ovnEncapNetmask;
    }

    public void setOvnEncapNetmask(String ovnEncapNetmask) {
        this.ovnEncapNetmask = ovnEncapNetmask;
    }
}

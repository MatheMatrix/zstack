package org.zstack.network.service.virtualrouter.portforwarding;

import org.zstack.core.upgrade.GrayVersion;

import java.io.Serializable;

public class PortForwardingRuleTO implements Serializable {
    @GrayVersion(value = "5.0.0")
    private String uuid;
    @GrayVersion(value = "5.0.0")
    private int vipPortStart;
    @GrayVersion(value = "5.0.0")
    private int vipPortEnd;
    @GrayVersion(value = "5.0.0")
    private int privatePortStart;
    @GrayVersion(value = "5.0.0")
    private int privatePortEnd;
    @GrayVersion(value = "5.0.0")
    private String protocolType;
    @GrayVersion(value = "5.0.0")
    private String vipIp;
    @GrayVersion(value = "5.5.0")
    private String vipUuid;
    @GrayVersion(value = "5.0.0")
    private String publicMac;
    @GrayVersion(value = "5.0.0")
    private String privateIp;
    @GrayVersion(value = "5.0.0")
    private String privateMac;
    @GrayVersion(value = "5.0.0")
    private String allowedCidr;
    @GrayVersion(value = "5.0.0")
    private boolean snatInboundTraffic;

    public boolean isSnatInboundTraffic() {
        return snatInboundTraffic;
    }

    public void setSnatInboundTraffic(boolean snatInboundTraffic) {
        this.snatInboundTraffic = snatInboundTraffic;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public int getVipPortStart() {
        return vipPortStart;
    }
    public void setVipPortStart(int vipPortStart) {
        this.vipPortStart = vipPortStart;
    }
    public int getVipPortEnd() {
        return vipPortEnd;
    }
    public void setVipPortEnd(int vipPortEnd) {
        this.vipPortEnd = vipPortEnd;
    }
    public int getPrivatePortStart() {
        return privatePortStart;
    }
    public void setPrivatePortStart(int privatePortStart) {
        this.privatePortStart = privatePortStart;
    }
    public int getPrivatePortEnd() {
        return privatePortEnd;
    }
    public void setPrivatePortEnd(int privatePortEnd) {
        this.privatePortEnd = privatePortEnd;
    }
    public String getProtocolType() {
        return protocolType;
    }
    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
    }
    public String getVipIp() {
        return vipIp;
    }
    public void setVipIp(String vipIp) {
        this.vipIp = vipIp;
    }
    public String getPrivateIp() {
        return privateIp;
    }
    public void setPrivateIp(String privateIp) {
        this.privateIp = privateIp;
    }
    public String getPrivateMac() {
        return privateMac;
    }
    public void setPrivateMac(String privateMac) {
        this.privateMac = privateMac;
    }
    public String getAllowedCidr() {
        return allowedCidr;
    }
    public void setAllowedCidr(String allowedCidr) {
        this.allowedCidr = allowedCidr;
    }

    public String getPublicMac() {
        return publicMac;
    }

    public void setPublicMac(String publicMac) {
        this.publicMac = publicMac;
    }

    public String getVipUuid() {
        return vipUuid;
    }

    public void setVipUuid(String vipUuid) {
        this.vipUuid = vipUuid;
    }
}

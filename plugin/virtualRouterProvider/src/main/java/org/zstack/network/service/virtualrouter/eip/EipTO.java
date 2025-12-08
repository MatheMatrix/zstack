package org.zstack.network.service.virtualrouter.eip;

import org.zstack.core.upgrade.GrayVersion;

import java.io.Serializable;

/**
 */
public class EipTO implements Serializable {
    @GrayVersion(value = "5.0.0")
    private String vipIp;
    @GrayVersion(value = "5.5.0")
    private String vipUuid;
    @GrayVersion(value = "5.0.0")
    private String privateMac;
    @GrayVersion(value = "5.0.0")
    private String publicMac;
    @GrayVersion(value = "5.0.0")
    private String guestIp;
    @GrayVersion(value = "5.0.0")
    private boolean snatInboundTraffic;
    @GrayVersion(value = "5.0.0")
    private boolean needCleanGuestIp;
    @GrayVersion(value = "5.0.0")
    private String ipVersion;

    public boolean isNeedCleanGuestIp() {
        return needCleanGuestIp;
    }

    public void setNeedCleanGuestIp(boolean needCleanGuestIp) {
        this.needCleanGuestIp = needCleanGuestIp;
    }

    public boolean isSnatInboundTraffic() {
        return snatInboundTraffic;
    }

    public void setSnatInboundTraffic(boolean snatInboundTraffic) {
        this.snatInboundTraffic = snatInboundTraffic;
    }

    public String getGuestIp() {
        return guestIp;
    }

    public void setGuestIp(String guestIp) {
        this.guestIp = guestIp;
    }

    public String getVipIp() {
        return vipIp;
    }

    public void setVipIp(String vipIp) {
        this.vipIp = vipIp;
    }

    public String getPrivateMac() {
        return privateMac;
    }

    public void setPrivateMac(String privateMac) {
        this.privateMac = privateMac;
    }

    public String getPublicMac() {
        return publicMac;
    }

    public void setPublicMac(String publicMac) {
        this.publicMac = publicMac;
    }

    public String getIpVersion() {
        return ipVersion;
    }

    public void setIpVersion(String ipVersion) {
        this.ipVersion = ipVersion;
    }

    public String getVipUuid() {
        return vipUuid;
    }

    public void setVipUuid(String vipUuid) {
        this.vipUuid = vipUuid;
    }
}

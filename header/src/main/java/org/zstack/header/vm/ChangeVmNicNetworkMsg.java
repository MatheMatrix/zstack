package org.zstack.header.vm;

import org.zstack.header.message.NeedReplyMessage;

import java.util.List;
import java.util.Map;

/**
 * Created by LiangHanYu on 2022/6/24 10:58
 */
public class ChangeVmNicNetworkMsg extends NeedReplyMessage implements VmInstanceMessage {
    private String vmNicUuid;
    private String destL3NetworkUuid;
    private String vmInstanceUuid;
    private Map<String, List<String>> requiredIpMap;
    private String staticIp;
    private String ip;
    private String ip6;
    private String netmask;
    private String gateway;
    private String ipv6Gateway;
    private String ipv6Prefix;
    private List<String> dnsAddresses;

    public String getVmNicUuid() {
        return vmNicUuid;
    }

    public void setVmNicUuid(String vmNicUuid) {
        this.vmNicUuid = vmNicUuid;
    }

    public String getDestL3NetworkUuid() {
        return destL3NetworkUuid;
    }

    public void setDestL3NetworkUuid(String destL3NetworkUuid) {
        this.destL3NetworkUuid = destL3NetworkUuid;
    }

    @Override
    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public Map<String, List<String>> getRequiredIpMap() {
        return requiredIpMap;
    }

    public void setRequiredIpMap(Map<String, List<String>> requiredIpMap) {
        this.requiredIpMap = requiredIpMap;
    }

    public String getStaticIp() {
        return staticIp;
    }

    public void setStaticIp(String staticIp) {
        this.staticIp = staticIp;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getIp6() {
        return ip6;
    }

    public void setIp6(String ip6) {
        this.ip6 = ip6;
    }

    public String getNetmask() {
        return netmask;
    }

    public void setNetmask(String netmask) {
        this.netmask = netmask;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public String getIpv6Gateway() {
        return ipv6Gateway;
    }

    public void setIpv6Gateway(String ipv6Gateway) {
        this.ipv6Gateway = ipv6Gateway;
    }

    public String getIpv6Prefix() {
        return ipv6Prefix;
    }

    public void setIpv6Prefix(String ipv6Prefix) {
        this.ipv6Prefix = ipv6Prefix;
    }

    public List<String> getDnsAddresses() {
        return dnsAddresses;
    }

    public void setDnsAddresses(List<String> dnsAddresses) {
        this.dnsAddresses = dnsAddresses;
    }
}

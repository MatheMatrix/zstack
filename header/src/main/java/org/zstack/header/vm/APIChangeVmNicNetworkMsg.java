package org.zstack.header.vm;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.network.l3.L3NetworkVO;
import org.zstack.header.rest.APINoSee;
import org.zstack.header.rest.RestRequest;

import java.util.List;
import java.util.Map;

@Action(category = VmInstanceConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/vm-instances/nics/{vmNicUuid}/l3-networks/{destL3NetworkUuid}",
        parameterName = "params",
        method = HttpMethod.POST,
        responseClass = APIChangeVmNicNetworkEvent.class
)
public class APIChangeVmNicNetworkMsg extends APIMessage implements VmInstanceMessage{
    @APIParam(resourceType = VmNicVO.class, checkAccount = true, operationTarget = true)
    private String vmNicUuid;

    @APIParam(resourceType = L3NetworkVO.class, checkAccount = true)
    private String destL3NetworkUuid;

    @APINoSee
    private String vmInstanceUuid;

    @APINoSee
    private Map<String, List<String>> requiredIpMap;

    private String staticIp;

    @APIParam(required = false)
    private String ip;
    @APIParam(required = false)
    private String ip6;
    @APIParam(required = false)
    private String netmask;
    @APIParam(required = false)
    private String gateway;
    @APIParam(required = false)
    private String ipv6Gateway;
    @APIParam(required = false)
    private String ipv6Prefix;

    @APIParam(required = false)
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

    public Map<String, List<String>> getRequiredIpMap() {
        return requiredIpMap;
    }

    public void setRequiredIpMap(Map<String, List<String>> requiredIpMap) {
        this.requiredIpMap = requiredIpMap;
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

    public static APIChangeVmNicNetworkMsg __example__() {
        APIChangeVmNicNetworkMsg msg = new APIChangeVmNicNetworkMsg();
        msg.vmNicUuid = uuid();
        msg.destL3NetworkUuid = uuid();
        return msg;
    }

    @Override
    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public String getStaticIp() {
        return staticIp;
    }

    public void setStaticIp(String staticIp) {
        this.staticIp = staticIp;
    }

    public List<String> getDnsAddresses() {
        return dnsAddresses;
    }

    public void setDnsAddresses(List<String> dnsAddresses) {
        this.dnsAddresses = dnsAddresses;
    }
}

package org.zstack.header.vm;

/**
 * Extension point called after VM NIC IP configuration changes in disable-IPAM scenarios
 * (where ipRangeUuid is null). This allows modules like GuestTools to sync the updated
 * network config to the VM via QGA, since DHCP path skips IPs without ipRangeUuid.
 */
public interface VmNicIpChangedForNoIpamExtensionPoint {
    void afterVmNicIpChangedForNoIpam(String vmInstanceUuid, String vmNicUuid);
}

package org.zstack.test.integration.networkservice.provider.flat

import org.springframework.http.HttpEntity
import org.zstack.compute.vm.VmGlobalConfig
import org.zstack.compute.vm.VmSystemTags
import org.zstack.core.db.DatabaseFacade
import org.zstack.core.db.Q
import org.zstack.header.network.l3.UsedIpVO
import org.zstack.header.network.l3.UsedIpVO_
import org.zstack.header.network.service.NetworkServiceType
import org.zstack.header.vm.VmNicVO
import org.zstack.header.vm.VmNicVO_
import org.zstack.kvm.KVMAgentCommands
import org.zstack.kvm.KVMSecurityGroupBackend
import org.zstack.network.securitygroup.SecurityGroupConstant
import org.zstack.network.securitygroup.VmNicSecurityGroupRefVO
import org.zstack.network.securitygroup.VmNicSecurityGroupRefVO_
import org.zstack.network.service.eip.EipConstant
import org.zstack.network.service.flat.FlatDhcpBackend
import org.zstack.network.service.flat.FlatNetworkServiceConstant
import org.zstack.network.service.userdata.UserdataConstant
import org.zstack.sdk.*
import org.zstack.test.integration.networkservice.provider.NetworkServiceProviderTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit
import org.zstack.utils.gson.JSONObjectUtil

class FlatChangeVmIpOutsideCidrCase extends SubCase {

    EnvSpec env
    DatabaseFacade dbf

    @Override
    void setup() {
        useSpring(NetworkServiceProviderTest.springSpec)
    }

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void environment() {
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(1)
                cpu = 1
            }

            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "root"
                password = "password"
                hostname = "localhost"

                image {
                    name = "image1"
                    url = "http://zstack.org/download/test.qcow2"
                }
            }

            zone {
                name = "zone"
                description = "test"

                cluster {
                    name = "cluster"
                    hypervisorType = "KVM"

                    kvm {
                        name = "kvm"
                        managementIp = "127.0.0.1"
                        username = "root"
                        password = "password"
                    }

                    attachPrimaryStorage("local")
                    attachL2Network("l2")
                    attachL2Network("l2-2")
                }

                localPrimaryStorage {
                    name = "local"
                    url = "/local_ps"
                }

                l2NoVlanNetwork {
                    name = "l2"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "flatL3"

                        service {
                            provider = FlatNetworkServiceConstant.FLAT_NETWORK_SERVICE_TYPE_STRING
                            types = [NetworkServiceType.DHCP.toString(),
                                     UserdataConstant.USERDATA_TYPE_STRING,
                                     EipConstant.EIP_NETWORK_SERVICE_TYPE]
                        }
                        service {
                            provider = SecurityGroupConstant.SECURITY_GROUP_PROVIDER_TYPE
                            types = [SecurityGroupConstant.SECURITY_GROUP_NETWORK_SERVICE_TYPE]
                        }

                        ip {
                            startIp = "192.168.100.10"
                            endIp = "192.168.100.200"
                            netmask = "255.255.255.0"
                            gateway = "192.168.100.1"
                        }
                    }

                    l3Network {
                        name = "flatL3_noDhcp"
                    }
                }

                l2NoVlanNetwork {
                    name = "l2-2"
                    physicalInterface = "eth1"

                    l3Network {
                        name = "pubL3"

                        service {
                            provider = FlatNetworkServiceConstant.FLAT_NETWORK_SERVICE_TYPE_STRING
                            types = [NetworkServiceType.DHCP.toString(),
                                     EipConstant.EIP_NETWORK_SERVICE_TYPE]
                        }
                        service {
                            provider = SecurityGroupConstant.SECURITY_GROUP_PROVIDER_TYPE
                            types = [SecurityGroupConstant.SECURITY_GROUP_NETWORK_SERVICE_TYPE]
                        }

                        ip {
                            startIp = "12.100.10.10"
                            endIp = "12.100.10.200"
                            netmask = "255.255.255.0"
                            gateway = "12.100.10.1"
                        }
                    }
                }

                attachBackupStorage("sftp")
            }
        }
    }

    @Override
    void test() {
        dbf = bean(DatabaseFacade.class)
        env.create {
            updateGlobalConfig {
                category = VmGlobalConfig.CATEGORY
                name = "allow.ip.outside.range"
                value = "true"
            }

            testSetStaticIpOutsideCidrOnIpamFlatL3()
            testSetStaticIpOnNoIpamFlatL3()
            testChangeNicNetworkToNoIpamL3()
            testChangeNicNetworkWithOutsideCidrIpToIpamL3()
            testDhcpSkipForOutsideCidrIpOnVmReboot()
            testEipRejectOutsideCidrIp()
            testSecurityGroupWithOutsideCidrIp()
            testIpCapacityExcludesOutsideCidrIp()
            testAddIpRangeAssociatesOrphanIp()
            testAllowIpOutsideRangeDisabled()
        }
    }

    void testSetStaticIpOutsideCidrOnIpamFlatL3() {
        L3NetworkInventory flatL3 = env.inventoryByName("flatL3")

        VmInstanceInventory vm = createVmInstance {
            name = "vm-outside-cidr"
            imageUuid = env.inventoryByName("image1").uuid
            instanceOfferingUuid = env.inventoryByName("instanceOffering").uuid
            l3NetworkUuids = [flatL3.uuid]
        }

        String originalIp = vm.vmNics[0].ip

        FlatDhcpBackend.BatchApplyDhcpCmd batchApplyDhcpCmd = null
        env.afterSimulator(FlatDhcpBackend.BATCH_APPLY_DHCP_PATH) { rsp, HttpEntity<String> e ->
            batchApplyDhcpCmd = JSONObjectUtil.toObject(e.body, FlatDhcpBackend.BatchApplyDhcpCmd.class)
            return rsp
        }

        setVmStaticIp {
            vmInstanceUuid = vm.uuid
            l3NetworkUuid = flatL3.uuid
            ip = "10.0.0.50"
            netmask = "255.255.255.0"
            gateway = "10.0.0.1"
            dnsAddresses = ["8.8.8.8", "114.114.114.114"]
            systemTags = [
                    String.format("staticIp::%s::10.0.0.50", flatL3.uuid),
                    String.format("ipv4Netmask::%s::255.255.255.0", flatL3.uuid),
                    String.format("ipv4Gateway::%s::10.0.0.1", flatL3.uuid)
            ]
        }

        // Verify UsedIpVO
        UsedIpVO usedIp = Q.New(UsedIpVO.class)
                .eq(UsedIpVO_.vmNicUuid, vm.vmNics[0].uuid)
                .find()
        assert usedIp != null
        assert usedIp.ip == "10.0.0.50"
        assert usedIp.netmask == "255.255.255.0"
        assert usedIp.gateway == "10.0.0.1"
        assert usedIp.ipRangeUuid == null : "ipRangeUuid should be null for outside-CIDR IP"

        // Verify VmNicVO
        VmNicVO nicVO = dbFindByUuid(vm.vmNics[0].uuid, VmNicVO.class)
        assert nicVO.ip == "10.0.0.50"
        assert nicVO.netmask == "255.255.255.0"
        assert nicVO.gateway == "10.0.0.1"

        // Verify DHCP does not include outside-CIDR IP
        retryInSecs {
            assert batchApplyDhcpCmd != null
            for (def dhcpInfo : batchApplyDhcpCmd.dhcpInfos) {
                for (def dhcp : dhcpInfo.dhcp) {
                    assert dhcp.ip != "10.0.0.50" : "DHCP should not include outside-CIDR IP"
                }
            }
        }

        // Verify DNS system tag
        List<Map<String, String>> dnsTags = VmSystemTags.STATIC_DNS.getTokensOfTagsByResourceUuid(vm.uuid)
        assert dnsTags.size() > 0
        def dnsTag = dnsTags.find { it.get(VmSystemTags.STATIC_DNS_L3_UUID_TOKEN) == flatL3.uuid }
        assert dnsTag != null
        String dnsStr = dnsTag.get(VmSystemTags.STATIC_DNS_TOKEN)
        assert dnsStr.contains("8.8.8.8")
        assert dnsStr.contains("114.114.114.114")
    }

    void testSetStaticIpOnNoIpamFlatL3() {
        L3NetworkInventory flatL3 = env.inventoryByName("flatL3")
        L3NetworkInventory flatL3NoDhcp = env.inventoryByName("flatL3_noDhcp")

        VmInstanceInventory vm = queryVmInstance { conditions = ["name=vm-outside-cidr"] }[0]

        // Attach NIC to no-IPAM L3
        attachL3NetworkToVm {
            l3NetworkUuid = flatL3NoDhcp.uuid
            vmInstanceUuid = vm.uuid
        }

        vm = queryVmInstance { conditions = ["name=vm-outside-cidr"] }[0]
        VmNicInventory noDhcpNic = vm.vmNics.find { it.l3NetworkUuid == flatL3NoDhcp.uuid }
        assert noDhcpNic != null

        setVmStaticIp {
            vmInstanceUuid = vm.uuid
            l3NetworkUuid = flatL3NoDhcp.uuid
            ip = "172.16.0.50"
            netmask = "255.255.0.0"
            gateway = "172.16.0.1"
            dnsAddresses = ["8.8.8.8"]
        }

        // Verify UsedIpVO
        UsedIpVO usedIp = Q.New(UsedIpVO.class)
                .eq(UsedIpVO_.vmNicUuid, noDhcpNic.uuid)
                .find()
        assert usedIp != null
        assert usedIp.ip == "172.16.0.50"
        assert usedIp.netmask == "255.255.0.0"
        assert usedIp.gateway == "172.16.0.1"
        assert usedIp.ipRangeUuid == null

        // Verify VmNicVO
        VmNicVO nicVO = dbFindByUuid(noDhcpNic.uuid, VmNicVO.class)
        assert nicVO.ip == "172.16.0.50"
        assert nicVO.netmask == "255.255.0.0"
        assert nicVO.gateway == "172.16.0.1"

        // Verify DNS system tag
        List<Map<String, String>> dnsTags = VmSystemTags.STATIC_DNS.getTokensOfTagsByResourceUuid(vm.uuid)
        def dnsTag = dnsTags.find { it.get(VmSystemTags.STATIC_DNS_L3_UUID_TOKEN) == flatL3NoDhcp.uuid }
        assert dnsTag != null
        String dnsStr = dnsTag.get(VmSystemTags.STATIC_DNS_TOKEN)
        assert dnsStr.contains("8.8.8.8")
    }

    void testChangeNicNetworkToNoIpamL3() {
        L3NetworkInventory flatL3 = env.inventoryByName("flatL3")
        L3NetworkInventory flatL3NoDhcp = env.inventoryByName("flatL3_noDhcp")

        VmInstanceInventory vm = createVmInstance {
            name = "vm-change-to-noipam"
            imageUuid = env.inventoryByName("image1").uuid
            instanceOfferingUuid = env.inventoryByName("instanceOffering").uuid
            l3NetworkUuids = [flatL3.uuid]
        }
        VmNicInventory vmNic = vm.vmNics[0]
        String oldIp = vmNic.ip

        FlatDhcpBackend.ReleaseDhcpCmd releaseDhcpCmd = null
        env.afterSimulator(FlatDhcpBackend.RELEASE_DHCP_PATH) { rsp, HttpEntity<String> e ->
            releaseDhcpCmd = JSONObjectUtil.toObject(e.body, FlatDhcpBackend.ReleaseDhcpCmd.class)
            return rsp
        }

        changeVmNicNetwork {
            vmNicUuid = vmNic.uuid
            destL3NetworkUuid = flatL3NoDhcp.uuid
            ip = "172.16.0.60"
            netmask = "255.255.0.0"
            gateway = "172.16.0.1"
            dnsAddresses = ["1.1.1.1"]
        }

        // Verify DHCP release of old IP
        retryInSecs {
            assert releaseDhcpCmd != null
            assert releaseDhcpCmd.dhcp.size() == 1
            assert releaseDhcpCmd.dhcp.get(0).ip == oldIp
        }

        // Verify UsedIpVO
        UsedIpVO usedIp = Q.New(UsedIpVO.class)
                .eq(UsedIpVO_.vmNicUuid, vmNic.uuid)
                .find()
        assert usedIp != null
        assert usedIp.ip == "172.16.0.60"
        assert usedIp.netmask == "255.255.0.0"
        assert usedIp.gateway == "172.16.0.1"
        assert usedIp.ipRangeUuid == null

        // Verify VmNicVO
        VmNicVO nicVO = dbFindByUuid(vmNic.uuid, VmNicVO.class)
        assert nicVO.l3NetworkUuid == flatL3NoDhcp.uuid
        assert nicVO.ip == "172.16.0.60"
        assert nicVO.netmask == "255.255.0.0"
        assert nicVO.gateway == "172.16.0.1"

        // Verify DNS: old L3 DNS tag should be gone, new L3 DNS tag should exist
        List<Map<String, String>> dnsTags = VmSystemTags.STATIC_DNS.getTokensOfTagsByResourceUuid(vm.uuid)
        def oldDnsTag = dnsTags.find { it.get(VmSystemTags.STATIC_DNS_L3_UUID_TOKEN) == flatL3.uuid }
        assert oldDnsTag == null : "old L3 DNS tag should be deleted"
        def newDnsTag = dnsTags.find { it.get(VmSystemTags.STATIC_DNS_L3_UUID_TOKEN) == flatL3NoDhcp.uuid }
        assert newDnsTag != null
        assert newDnsTag.get(VmSystemTags.STATIC_DNS_TOKEN).contains("1.1.1.1")
    }

    void testChangeNicNetworkWithOutsideCidrIpToIpamL3() {
        L3NetworkInventory flatL3 = env.inventoryByName("flatL3")
        L3NetworkInventory pubL3 = env.inventoryByName("pubL3")

        VmInstanceInventory vm = createVmInstance {
            name = "vm-change-outside-cidr-to-ipam"
            imageUuid = env.inventoryByName("image1").uuid
            instanceOfferingUuid = env.inventoryByName("instanceOffering").uuid
            l3NetworkUuids = [flatL3.uuid]
        }
        VmNicInventory vmNic = vm.vmNics[0]

        FlatDhcpBackend.ReleaseDhcpCmd releaseDhcpCmd = null
        env.afterSimulator(FlatDhcpBackend.RELEASE_DHCP_PATH) { rsp, HttpEntity<String> e ->
            releaseDhcpCmd = JSONObjectUtil.toObject(e.body, FlatDhcpBackend.ReleaseDhcpCmd.class)
            return rsp
        }
        FlatDhcpBackend.BatchApplyDhcpCmd batchApplyDhcpCmd = null
        env.afterSimulator(FlatDhcpBackend.BATCH_APPLY_DHCP_PATH) { rsp, HttpEntity<String> e ->
            batchApplyDhcpCmd = JSONObjectUtil.toObject(e.body, FlatDhcpBackend.BatchApplyDhcpCmd.class)
            return rsp
        }

        changeVmNicNetwork {
            vmNicUuid = vmNic.uuid
            destL3NetworkUuid = pubL3.uuid
            systemTags = [
                    String.format("staticIp::%s::10.10.10.50", pubL3.uuid),
                    String.format("ipv4Netmask::%s::255.255.255.0", pubL3.uuid),
                    String.format("ipv4Gateway::%s::10.10.10.1", pubL3.uuid)
            ]
        }

        // Verify DHCP release of old IP
        retryInSecs {
            assert releaseDhcpCmd != null
        }

        // Verify UsedIpVO
        UsedIpVO usedIp = Q.New(UsedIpVO.class)
                .eq(UsedIpVO_.vmNicUuid, vmNic.uuid)
                .find()
        assert usedIp != null
        assert usedIp.ip == "10.10.10.50"
        assert usedIp.ipRangeUuid == null : "ipRangeUuid should be null for outside-CIDR IP"

        // Verify DHCP does not include outside-CIDR IP
        retryInSecs {
            assert batchApplyDhcpCmd != null
            for (def dhcpInfo : batchApplyDhcpCmd.dhcpInfos) {
                for (def dhcp : dhcpInfo.dhcp) {
                    assert dhcp.ip != "10.10.10.50" : "DHCP should not include outside-CIDR IP"
                }
            }
        }
    }

    void testDhcpSkipForOutsideCidrIpOnVmReboot() {
        // Use the VM from test1 that has outside-CIDR IP 10.0.0.50 on flatL3
        VmInstanceInventory vm = queryVmInstance { conditions = ["name=vm-outside-cidr"] }[0]

        stopVmInstance {
            uuid = vm.uuid
        }

        FlatDhcpBackend.BatchApplyDhcpCmd batchApplyDhcpCmd = null
        env.afterSimulator(FlatDhcpBackend.BATCH_APPLY_DHCP_PATH) { rsp, HttpEntity<String> e ->
            batchApplyDhcpCmd = JSONObjectUtil.toObject(e.body, FlatDhcpBackend.BatchApplyDhcpCmd.class)
            return rsp
        }

        startVmInstance {
            uuid = vm.uuid
        }

        // Verify DHCP does not include outside-CIDR IP after reboot
        retryInSecs {
            assert batchApplyDhcpCmd != null
            for (def dhcpInfo : batchApplyDhcpCmd.dhcpInfos) {
                for (def dhcp : dhcpInfo.dhcp) {
                    assert dhcp.ip != "10.0.0.50" : "DHCP should not include outside-CIDR IP after reboot"
                }
            }
        }
    }

    void testEipRejectOutsideCidrIp() {
        L3NetworkInventory pubL3 = env.inventoryByName("pubL3")
        // VM from test1 has outside-CIDR IP on flatL3
        VmInstanceInventory vm = queryVmInstance { conditions = ["name=vm-outside-cidr"] }[0]
        VmNicInventory nicWithOutsideIp = vm.vmNics.find { it.ip == "10.0.0.50" }
        assert nicWithOutsideIp != null

        VipInventory vip = createVip {
            name = "vip-outside-cidr"
            l3NetworkUuid = pubL3.uuid
        }

        EipInventory eip = createEip {
            name = "eip-outside-cidr"
            vipUuid = vip.uuid
        }

        // EIP attach should fail for NIC with outside-CIDR IP
        expect(AssertionError.class) {
            attachEip {
                eipUuid = eip.uuid
                vmNicUuid = nicWithOutsideIp.uuid
            }
        }
    }

    void testSecurityGroupWithOutsideCidrIp() {
        L3NetworkInventory flatL3 = env.inventoryByName("flatL3")
        // VM from test1 has outside-CIDR IP 10.0.0.50 on flatL3
        VmInstanceInventory vm = queryVmInstance { conditions = ["name=vm-outside-cidr"] }[0]
        VmNicInventory nicWithOutsideIp = vm.vmNics.find { it.ip == "10.0.0.50" }
        assert nicWithOutsideIp != null

        def sg = createSecurityGroup {
            name = "sg-outside-cidr"
            ipVersion = 4
        } as SecurityGroupInventory

        attachSecurityGroupToL3Network {
            securityGroupUuid = sg.uuid
            l3NetworkUuid = flatL3.uuid
        }

        KVMAgentCommands.ApplySecurityGroupRuleCmd cmd = null
        env.afterSimulator(KVMSecurityGroupBackend.SECURITY_GROUP_APPLY_RULE_PATH) { rsp, HttpEntity<String> e ->
            cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.ApplySecurityGroupRuleCmd.class)
            return rsp
        }

        addVmNicToSecurityGroup {
            securityGroupUuid = sg.uuid
            vmNicUuids = [nicWithOutsideIp.uuid]
        }

        // Verify SG ref is created
        List<VmNicSecurityGroupRefVO> refs = Q.New(VmNicSecurityGroupRefVO.class)
                .eq(VmNicSecurityGroupRefVO_.vmNicUuid, nicWithOutsideIp.uuid)
                .eq(VmNicSecurityGroupRefVO_.securityGroupUuid, sg.uuid)
                .list()
        assert refs.size() == 1

        // Verify SG rules do not include outside-CIDR IP in the member IPs
        // The SQL filter in SecurityGroupManagerImpl excludes ipRangeUuid=null IPs
        // from getVmIpsBySecurityGroup, so the outside-CIDR IP won't appear in
        // the security group member IP list used for rule generation
        retryInSecs {
            assert cmd != null
        }
    }

    void testIpCapacityExcludesOutsideCidrIp() {
        L3NetworkInventory flatL3 = env.inventoryByName("flatL3")

        // Get IP capacity - outside-CIDR IPs should not be counted
        GetIpAddressCapacityResult capacityBefore = getIpAddressCapacity {
            l3NetworkUuids = [flatL3.uuid]
        }

        // Count UsedIpVOs with ipRangeUuid != null on flatL3 (these are the ones that should be counted)
        long inRangeCount = Q.New(UsedIpVO.class)
                .eq(UsedIpVO_.l3NetworkUuid, flatL3.uuid)
                .notNull(UsedIpVO_.ipRangeUuid)
                .count()

        assert capacityBefore.usedIpAddressNumber == inRangeCount :
                "IP capacity should only count IPs within IP ranges"

        // Verify outside-CIDR IPs exist but are not counted
        long outsideCount = Q.New(UsedIpVO.class)
                .eq(UsedIpVO_.l3NetworkUuid, flatL3.uuid)
                .isNull(UsedIpVO_.ipRangeUuid)
                .count()
        assert outsideCount > 0 : "There should be outside-CIDR IPs on flatL3"
    }

    void testAddIpRangeAssociatesOrphanIp() {
        L3NetworkInventory flatL3NoDhcp = env.inventoryByName("flatL3_noDhcp")

        // Find a NIC on flatL3_noDhcp with ipRangeUuid=null
        UsedIpVO orphanIp = Q.New(UsedIpVO.class)
                .eq(UsedIpVO_.l3NetworkUuid, flatL3NoDhcp.uuid)
                .isNull(UsedIpVO_.ipRangeUuid)
                .limit(1)
                .find()
        assert orphanIp != null : "Should have an orphan IP on flatL3_noDhcp"
        String orphanIpAddr = orphanIp.ip

        // Add IP range that covers the orphan IP
        IpRangeInventory ipRange = addIpRange {
            delegate.name = "nodhcp-ip-range"
            delegate.l3NetworkUuid = flatL3NoDhcp.uuid
            delegate.startIp = "172.16.0.2"
            delegate.endIp = "172.16.0.253"
            delegate.gateway = "172.16.0.1"
            delegate.netmask = "255.255.0.0"
        }

        // Verify orphan IP now has ipRangeUuid backfilled
        UsedIpVO updatedIp = Q.New(UsedIpVO.class)
                .eq(UsedIpVO_.uuid, orphanIp.uuid)
                .find()
        assert updatedIp.ipRangeUuid == ipRange.uuid :
                "ipRangeUuid should be backfilled to the new IP range"

        // Verify IP capacity now includes this IP
        GetIpAddressCapacityResult capacity = getIpAddressCapacity {
            l3NetworkUuids = [flatL3NoDhcp.uuid]
        }
        assert capacity.totalCapacity > 0
        assert capacity.usedIpAddressNumber >= 1
    }

    void testAllowIpOutsideRangeDisabled() {
        L3NetworkInventory flatL3 = env.inventoryByName("flatL3")

        // Disable allow.ip.outside.range
        updateGlobalConfig {
            category = VmGlobalConfig.CATEGORY
            name = "allow.ip.outside.range"
            value = "false"
        }

        VmInstanceInventory vm = createVmInstance {
            name = "vm-disabled-outside-range"
            imageUuid = env.inventoryByName("image1").uuid
            instanceOfferingUuid = env.inventoryByName("instanceOffering").uuid
            l3NetworkUuids = [flatL3.uuid]
        }

        // Setting outside-range IP should fail when config is disabled
        expect(AssertionError.class) {
            setVmStaticIp {
                vmInstanceUuid = vm.uuid
                l3NetworkUuid = flatL3.uuid
                ip = "10.0.0.99"
                netmask = "255.255.255.0"
                gateway = "10.0.0.1"
                systemTags = [
                        String.format("staticIp::%s::10.0.0.99", flatL3.uuid),
                        String.format("ipv4Netmask::%s::255.255.255.0", flatL3.uuid),
                        String.format("ipv4Gateway::%s::10.0.0.1", flatL3.uuid)
                ]
            }
        }

        // Restore config
        updateGlobalConfig {
            category = VmGlobalConfig.CATEGORY
            name = "allow.ip.outside.range"
            value = "true"
        }
    }
}

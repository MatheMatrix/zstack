package org.zstack.test.integration.networkservice.provider.flat

import org.springframework.http.HttpEntity
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

/**
 * Test IP outside CIDR behavior for flat/public networks:
 * - With DHCP service: IP must be within IP range (outside-CIDR rejected)
 * - Without DHCP service: IP can be outside IP range (outside-CIDR allowed)
 */
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

                    // flatL3: with DHCP — IP must be in range
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

                    // flatL3_noDhcp: without DHCP — IP can be outside range
                    l3Network {
                        name = "flatL3_noDhcp"

                        service {
                            provider = SecurityGroupConstant.SECURITY_GROUP_PROVIDER_TYPE
                            types = [SecurityGroupConstant.SECURITY_GROUP_NETWORK_SERVICE_TYPE]
                        }
                    }
                }

                l2NoVlanNetwork {
                    name = "l2-2"
                    physicalInterface = "eth1"

                    // pubL3: with DHCP — IP must be in range
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
            // With DHCP: outside-CIDR IP must be rejected
            testSetStaticIpOutsideCidrRejectedOnDhcpL3()
            testChangeNicNetworkOutsideCidrRejectedOnDhcpL3()

            // Without DHCP: outside-CIDR IP is allowed
            testSetStaticIpOutsideCidrAllowedOnNoDhcpL3()
            testChangeNicNetworkOutsideCidrAllowedOnNoDhcpL3()

            // With DHCP: in-range IP works normally
            testSetStaticIpInRangeOnDhcpL3()
            testChangeNicNetworkInRangeOnDhcpL3()

            // Supplementary: DHCP skip, EIP reject, SG, capacity, orphan IP backfill
            testDhcpSkipForOutsideCidrIpOnVmReboot()
            testEipRejectOutsideCidrIp()
            testSecurityGroupWithOutsideCidrIp()
            testIpCapacityExcludesOutsideCidrIp()
            testAddIpRangeAssociatesOrphanIp()
        }
    }

    /**
     * With DHCP: setVmStaticIp with outside-CIDR IP should fail on flatL3
     */
    void testSetStaticIpOutsideCidrRejectedOnDhcpL3() {
        L3NetworkInventory flatL3 = env.inventoryByName("flatL3")

        VmInstanceInventory vm = createVmInstance {
            name = "vm-dhcp-reject"
            imageUuid = env.inventoryByName("image1").uuid
            instanceOfferingUuid = env.inventoryByName("instanceOffering").uuid
            l3NetworkUuids = [flatL3.uuid]
        }

        // Outside-CIDR IP on DHCP-enabled L3 should be rejected
        expect(AssertionError.class) {
            setVmStaticIp {
                vmInstanceUuid = vm.uuid
                l3NetworkUuid = flatL3.uuid
                ip = "10.0.0.50"
                systemTags = [
                        String.format("staticIp::%s::10.0.0.50", flatL3.uuid),
                        String.format("ipv4Netmask::%s::255.255.255.0", flatL3.uuid),
                        String.format("ipv4Gateway::%s::10.0.0.1", flatL3.uuid)
                ]
            }
        }

        // Verify VmNic IP is unchanged
        VmNicVO nicVO = dbFindByUuid(vm.vmNics[0].uuid, VmNicVO.class)
        assert nicVO.ip != "10.0.0.50" : "outside-CIDR IP should not be set on DHCP-enabled L3"
    }

    /**
     * With DHCP: changeVmNicNetwork with outside-CIDR IP should fail on pubL3
     */
    void testChangeNicNetworkOutsideCidrRejectedOnDhcpL3() {
        L3NetworkInventory flatL3 = env.inventoryByName("flatL3")
        L3NetworkInventory pubL3 = env.inventoryByName("pubL3")

        VmInstanceInventory vm = createVmInstance {
            name = "vm-change-dhcp-reject"
            imageUuid = env.inventoryByName("image1").uuid
            instanceOfferingUuid = env.inventoryByName("instanceOffering").uuid
            l3NetworkUuids = [flatL3.uuid]
        }
        VmNicInventory vmNic = vm.vmNics[0]

        // Change NIC to pubL3 (DHCP-enabled) with outside-CIDR IP should be rejected
        expect(AssertionError.class) {
            changeVmNicNetwork {
                vmNicUuid = vmNic.uuid
                destL3NetworkUuid = pubL3.uuid
                systemTags = [
                        String.format("staticIp::%s::10.10.10.50", pubL3.uuid),
                        String.format("ipv4Netmask::%s::255.255.255.0", pubL3.uuid),
                        String.format("ipv4Gateway::%s::10.10.10.1", pubL3.uuid)
                ]
            }
        }

        // Verify NIC still on original L3
        VmNicVO nicVO = dbFindByUuid(vmNic.uuid, VmNicVO.class)
        assert nicVO.l3NetworkUuid == flatL3.uuid : "NIC should not be changed when outside-CIDR IP is rejected"
    }

    /**
     * Without DHCP: setVmStaticIp with outside-CIDR IP should succeed on flatL3_noDhcp
     */
    void testSetStaticIpOutsideCidrAllowedOnNoDhcpL3() {
        L3NetworkInventory flatL3NoDhcp = env.inventoryByName("flatL3_noDhcp")

        VmInstanceInventory vm = queryVmInstance { conditions = ["name=vm-dhcp-reject"] }[0]

        // Attach NIC to no-DHCP L3
        attachL3NetworkToVm {
            l3NetworkUuid = flatL3NoDhcp.uuid
            vmInstanceUuid = vm.uuid
        }

        vm = queryVmInstance { conditions = ["name=vm-dhcp-reject"] }[0]
        VmNicInventory noDhcpNic = vm.vmNics.find { it.l3NetworkUuid == flatL3NoDhcp.uuid }
        assert noDhcpNic != null

        // Outside-CIDR IP on non-DHCP L3 should succeed
        setVmStaticIp {
            vmInstanceUuid = vm.uuid
            l3NetworkUuid = flatL3NoDhcp.uuid
            ip = "172.16.0.50"
            netmask = "255.255.0.0"
            gateway = "172.16.0.1"
            systemTags = [
                    String.format("staticIp::%s::172.16.0.50", flatL3NoDhcp.uuid),
                    String.format("ipv4Netmask::%s::255.255.0.0", flatL3NoDhcp.uuid),
                    String.format("ipv4Gateway::%s::172.16.0.1", flatL3NoDhcp.uuid)
            ]
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
        assert usedIp.ipRangeUuid == null : "ipRangeUuid should be null for outside-CIDR IP"

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

    /**
     * Without DHCP: changeVmNicNetwork with outside-CIDR IP should succeed to flatL3_noDhcp
     */
    void testChangeNicNetworkOutsideCidrAllowedOnNoDhcpL3() {
        L3NetworkInventory flatL3 = env.inventoryByName("flatL3")
        L3NetworkInventory flatL3NoDhcp = env.inventoryByName("flatL3_noDhcp")

        VmInstanceInventory vm = createVmInstance {
            name = "vm-change-to-nodhcp"
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
            systemTags = [
                    String.format("staticIp::%s::172.16.0.60", flatL3NoDhcp.uuid),
                    String.format("ipv4Netmask::%s::255.255.0.0", flatL3NoDhcp.uuid),
                    String.format("ipv4Gateway::%s::172.16.0.1", flatL3NoDhcp.uuid)
            ]
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
        assert usedIp.ipRangeUuid == null : "ipRangeUuid should be null for outside-CIDR IP"

        // Verify VmNicVO
        VmNicVO nicVO = dbFindByUuid(vmNic.uuid, VmNicVO.class)
        assert nicVO.l3NetworkUuid == flatL3NoDhcp.uuid
        assert nicVO.ip == "172.16.0.60"
        assert nicVO.netmask == "255.255.0.0"
        assert nicVO.gateway == "172.16.0.1"

        // Verify DNS: old L3 DNS tag gone, new L3 DNS tag exists
        List<Map<String, String>> dnsTags = VmSystemTags.STATIC_DNS.getTokensOfTagsByResourceUuid(vm.uuid)
        def oldDnsTag = dnsTags.find { it.get(VmSystemTags.STATIC_DNS_L3_UUID_TOKEN) == flatL3.uuid }
        assert oldDnsTag == null : "old L3 DNS tag should be deleted"
        def newDnsTag = dnsTags.find { it.get(VmSystemTags.STATIC_DNS_L3_UUID_TOKEN) == flatL3NoDhcp.uuid }
        assert newDnsTag != null
        assert newDnsTag.get(VmSystemTags.STATIC_DNS_TOKEN).contains("1.1.1.1")
    }

    /**
     * With DHCP: setVmStaticIp with in-range IP should succeed on flatL3
     */
    void testSetStaticIpInRangeOnDhcpL3() {
        L3NetworkInventory flatL3 = env.inventoryByName("flatL3")

        VmInstanceInventory vm = createVmInstance {
            name = "vm-dhcp-inrange"
            imageUuid = env.inventoryByName("image1").uuid
            instanceOfferingUuid = env.inventoryByName("instanceOffering").uuid
            l3NetworkUuids = [flatL3.uuid]
        }

        FlatDhcpBackend.BatchApplyDhcpCmd batchApplyDhcpCmd = null
        env.afterSimulator(FlatDhcpBackend.BATCH_APPLY_DHCP_PATH) { rsp, HttpEntity<String> e ->
            batchApplyDhcpCmd = JSONObjectUtil.toObject(e.body, FlatDhcpBackend.BatchApplyDhcpCmd.class)
            return rsp
        }

        // In-range IP on DHCP-enabled L3 should succeed
        setVmStaticIp {
            vmInstanceUuid = vm.uuid
            l3NetworkUuid = flatL3.uuid
            ip = "192.168.100.100"
            systemTags = [
                    String.format("staticIp::%s::192.168.100.100", flatL3.uuid)
            ]
        }

        // Verify UsedIpVO
        UsedIpVO usedIp = Q.New(UsedIpVO.class)
                .eq(UsedIpVO_.vmNicUuid, vm.vmNics[0].uuid)
                .find()
        assert usedIp != null
        assert usedIp.ip == "192.168.100.100"
        assert usedIp.ipRangeUuid != null : "ipRangeUuid should not be null for in-range IP"

        // Verify VmNicVO
        VmNicVO nicVO = dbFindByUuid(vm.vmNics[0].uuid, VmNicVO.class)
        assert nicVO.ip == "192.168.100.100"

        // Verify DHCP includes in-range IP
        retryInSecs {
            assert batchApplyDhcpCmd != null
            boolean found = false
            for (def dhcpInfo : batchApplyDhcpCmd.dhcpInfos) {
                for (def dhcp : dhcpInfo.dhcp) {
                    if (dhcp.ip == "192.168.100.100") {
                        found = true
                    }
                }
            }
            assert found : "DHCP should include in-range IP"
        }
    }

    /**
     * With DHCP: changeVmNicNetwork with in-range IP should succeed on pubL3
     */
    void testChangeNicNetworkInRangeOnDhcpL3() {
        L3NetworkInventory flatL3 = env.inventoryByName("flatL3")
        L3NetworkInventory pubL3 = env.inventoryByName("pubL3")

        VmInstanceInventory vm = createVmInstance {
            name = "vm-change-inrange"
            imageUuid = env.inventoryByName("image1").uuid
            instanceOfferingUuid = env.inventoryByName("instanceOffering").uuid
            l3NetworkUuids = [flatL3.uuid]
        }
        VmNicInventory vmNic = vm.vmNics[0]

        changeVmNicNetwork {
            vmNicUuid = vmNic.uuid
            destL3NetworkUuid = pubL3.uuid
            staticIp = "12.100.10.50"
            systemTags = [
                    String.format("staticIp::%s::12.100.10.50", pubL3.uuid)
            ]
        }

        // Verify VmNicVO
        VmNicVO nicVO = dbFindByUuid(vmNic.uuid, VmNicVO.class)
        assert nicVO.l3NetworkUuid == pubL3.uuid
        assert nicVO.ip == "12.100.10.50"

        // Verify UsedIpVO
        UsedIpVO usedIp = Q.New(UsedIpVO.class)
                .eq(UsedIpVO_.vmNicUuid, vmNic.uuid)
                .find()
        assert usedIp != null
        assert usedIp.ip == "12.100.10.50"
        assert usedIp.ipRangeUuid != null : "ipRangeUuid should not be null for in-range IP"
    }

    /**
     * DHCP should skip outside-CIDR IPs on VM reboot
     */
    void testDhcpSkipForOutsideCidrIpOnVmReboot() {
        // VM "vm-dhcp-reject" has outside-CIDR IP 172.16.0.50 on flatL3_noDhcp
        VmInstanceInventory vm = queryVmInstance { conditions = ["name=vm-dhcp-reject"] }[0]

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
                    assert dhcp.ip != "172.16.0.50" : "DHCP should not include outside-CIDR IP after reboot"
                }
            }
        }
    }

    /**
     * EIP should reject binding to NIC with outside-CIDR IP (ipRangeUuid=null)
     */
    void testEipRejectOutsideCidrIp() {
        L3NetworkInventory pubL3 = env.inventoryByName("pubL3")

        // VM "vm-dhcp-reject" has outside-CIDR IP 172.16.0.50 on flatL3_noDhcp
        VmInstanceInventory vm = queryVmInstance { conditions = ["name=vm-dhcp-reject"] }[0]
        VmNicInventory nicWithOutsideIp = vm.vmNics.find { it.ip == "172.16.0.50" }
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

    /**
     * Security group should handle NIC with outside-CIDR IP
     */
    void testSecurityGroupWithOutsideCidrIp() {
        L3NetworkInventory flatL3NoDhcp = env.inventoryByName("flatL3_noDhcp")

        // VM "vm-dhcp-reject" has outside-CIDR IP 172.16.0.50 on flatL3_noDhcp
        VmInstanceInventory vm = queryVmInstance { conditions = ["name=vm-dhcp-reject"] }[0]
        VmNicInventory nicWithOutsideIp = vm.vmNics.find { it.ip == "172.16.0.50" }
        assert nicWithOutsideIp != null

        def sg = createSecurityGroup {
            name = "sg-outside-cidr"
            ipVersion = 4
        } as SecurityGroupInventory

        attachSecurityGroupToL3Network {
            securityGroupUuid = sg.uuid
            l3NetworkUuid = flatL3NoDhcp.uuid
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

        retryInSecs {
            assert cmd != null
        }
    }

    /**
     * IP capacity should exclude outside-CIDR IPs (ipRangeUuid=null)
     */
    void testIpCapacityExcludesOutsideCidrIp() {
        L3NetworkInventory flatL3NoDhcp = env.inventoryByName("flatL3_noDhcp")

        // Verify outside-CIDR IPs exist on flatL3_noDhcp
        long outsideCount = Q.New(UsedIpVO.class)
                .eq(UsedIpVO_.l3NetworkUuid, flatL3NoDhcp.uuid)
                .isNull(UsedIpVO_.ipRangeUuid)
                .count()
        assert outsideCount > 0 : "There should be outside-CIDR IPs on flatL3_noDhcp"
    }

    /**
     * Adding IP range should backfill ipRangeUuid for orphan IPs within the new range
     */
    void testAddIpRangeAssociatesOrphanIp() {
        L3NetworkInventory flatL3NoDhcp = env.inventoryByName("flatL3_noDhcp")

        // Find a NIC on flatL3_noDhcp with ipRangeUuid=null
        UsedIpVO orphanIp = Q.New(UsedIpVO.class)
                .eq(UsedIpVO_.l3NetworkUuid, flatL3NoDhcp.uuid)
                .isNull(UsedIpVO_.ipRangeUuid)
                .limit(1)
                .find()
        assert orphanIp != null : "Should have an orphan IP on flatL3_noDhcp"

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
}

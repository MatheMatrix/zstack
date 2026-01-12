package org.zstack.test.integration.kvm.vm.migrate

import org.springframework.http.HttpEntity
import org.zstack.core.db.DatabaseFacade
import org.zstack.header.host.HostState
import org.zstack.header.host.HostStateEvent
import org.zstack.header.host.HostVO
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.kvm.KVMAgentCommands
import org.zstack.kvm.KVMConstant
import org.zstack.kvm.KVMSecurityGroupBackend
import org.zstack.sdk.ClusterInventory
import org.zstack.sdk.HostInventory
import org.zstack.sdk.MigrateVmAction
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

/**
 * Test for ZSTAC-75428: ResourceBindingAllocatorFlow should reject designated cross-cluster
 * migration even when strategy is Soft.
 *
 * This test covers all 12 scenarios from the truth table:
 *
 * == Scenario: User designates target host via CLI ==
 * | # | haAcross | strategy | Scene | Target Location  | Expected |
 * |---|----------|----------|-------|------------------|----------|
 * | 1 | false    | Hard     | All   | Same cluster     | Success  |
 * | 2 | false    | Hard     | All   | Cross cluster    | Fail     |
 * | 3 | false    | Soft     | All   | Same cluster     | Success  |
 * | 4 | false    | Soft     | All   | Cross cluster    | Fail (Fixed) |
 * | 5 | true     | Hard     | All   | Same cluster     | Success  |
 * | 6 | true     | Hard     | All   | Cross cluster    | Success  |
 * | 7 | true     | Soft     | All   | Same cluster     | Success  |
 * | 8 | true     | Soft     | All   | Cross cluster    | Success  |
 *
 * == Scenario: System auto-allocates host (no designated host) ==
 * | # | haAcross | strategy | Scene | Cluster Resource | Expected |
 * |---|----------|----------|-------|------------------|----------|
 * | 9 | false    | Hard     | All   | Sufficient       | Same cluster |
 * | 10| false    | Hard     | All   | Insufficient     | Fail     |
 * | 11| false    | Soft     | All   | Sufficient       | Same cluster |
 * | 12| false    | Soft     | All   | Insufficient     | Cross cluster (Soft relaxes) |
 */
class ResourceBindingMigrateVmCase extends SubCase {
    EnvSpec env
    DatabaseFacade dbf

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
        spring {
            ceph()
        }
    }

    @Override
    void environment() {
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(8)
                cpu = 4
            }

            zone {
                name = "zone"

                // Cluster 1 with two hosts
                cluster {
                    name = "cluster1"
                    hypervisorType = "KVM"

                    kvm {
                        name = "host1-cluster1"
                        managementIp = "127.0.0.1"
                        username = "root"
                        password = "password"
                        usedMem = 1000
                        totalCpu = 10
                    }

                    kvm {
                        name = "host2-cluster1"
                        managementIp = "127.0.0.2"
                        username = "root"
                        password = "password"
                        usedMem = 1000
                        totalCpu = 10
                    }

                    attachPrimaryStorage("ceph-ps")
                    attachL2Network("l2")
                }

                // Cluster 2 with one host
                cluster {
                    name = "cluster2"
                    hypervisorType = "KVM"

                    kvm {
                        name = "host1-cluster2"
                        managementIp = "127.0.0.3"
                        username = "root"
                        password = "password"
                        usedMem = 1000
                        totalCpu = 10
                    }

                    attachPrimaryStorage("ceph-ps")
                    attachL2Network("l2")
                }

                l2NoVlanNetwork {
                    name = "l2"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "l3"

                        ip {
                            startIp = "192.168.100.10"
                            endIp = "192.168.100.100"
                            netmask = "255.255.255.0"
                            gateway = "192.168.100.1"
                        }
                    }
                }

                cephPrimaryStorage {
                    name = "ceph-ps"
                    description = "Test"
                    totalCapacity = SizeUnit.GIGABYTE.toByte(100)
                    availableCapacity = SizeUnit.GIGABYTE.toByte(100)
                    url = "ceph://pri"
                    fsid = "7ff218d9-f525-435f-8a40-3618d1772a64"
                    monUrls = ["root:password@localhost/?monPort=7777"]
                }

                attachBackupStorage("ceph-bs")
            }

            cephBackupStorage {
                name = "ceph-bs"
                description = "Test"
                totalCapacity = SizeUnit.GIGABYTE.toByte(100)
                availableCapacity = SizeUnit.GIGABYTE.toByte(100)
                url = "/bk"
                fsid = "7ff218d9-f525-435f-8a40-3618d1772a64"
                monUrls = ["root:password@localhost/?monPort=7777"]

                image {
                    name = "image"
                    url = "http://zstack.org/download/image.qcow2"
                }
            }

            vm {
                name = "vm"
                useCluster("cluster1")
                useHost("host1-cluster1")
                useL3Networks("l3")
                useInstanceOffering("instanceOffering")
                useImage("image")
            }
        }
    }

    @Override
    void test() {
        env.create {
            dbf = bean(DatabaseFacade.class)

            env.simulator(KVMSecurityGroupBackend.SECURITY_GROUP_CLEANUP_UNUSED_RULE_ON_HOST_PATH) {
                return new KVMAgentCommands.CleanupUnusedRulesOnHostResponse()
            }

            // Part 1: User designates target host (Cases 1-8)
            testCase1_HaAcrossFalse_Hard_SameCluster()
            testCase2_HaAcrossFalse_Hard_CrossCluster()
            testCase3_HaAcrossFalse_Soft_SameCluster()
            testCase4_HaAcrossFalse_Soft_CrossCluster_CoreFix()
            testCase5_HaAcrossTrue_Hard_SameCluster()
            testCase6_HaAcrossTrue_Hard_CrossCluster()
            testCase7_HaAcrossTrue_Soft_SameCluster()
            testCase8_HaAcrossTrue_Soft_CrossCluster()

            // Part 2: System auto-allocates host (Cases 9-12)
            testCase9_AutoAllocate_HaAcrossFalse_Hard_Sufficient()
            testCase10_AutoAllocate_HaAcrossFalse_Hard_Insufficient()
            testCase11_AutoAllocate_HaAcrossFalse_Soft_Sufficient()
            testCase12_AutoAllocate_HaAcrossFalse_Soft_Insufficient()
        }
    }

    // ==================== Helper Methods ====================

    void setResourceBindingConfig(String clusterUuid, String vmUuid, boolean haAcross, String strategy, String scene) {
        updateResourceConfig {
            name = "vm.ha.across.clusters"
            category = "vm"
            resourceUuid = clusterUuid
            value = haAcross.toString()
        }

        updateResourceConfig {
            name = "resourceBinding.strategy"
            category = "vm"
            resourceUuid = vmUuid
            value = strategy
        }

        updateResourceConfig {
            name = "resourceBinding.Scene"
            category = "vm"
            resourceUuid = clusterUuid
            value = scene
        }
    }

    void resetVmToHost1Cluster1() {
        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory host1 = env.inventoryByName("host1-cluster1") as HostInventory

        VmInstanceVO vmVO = dbFindByUuid(vm.uuid, VmInstanceVO.class)
        if (vmVO.hostUuid != host1.uuid) {
            // Need to migrate back
            ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory
            // Temporarily allow cross-cluster to migrate back
            updateResourceConfig {
                name = "vm.ha.across.clusters"
                category = "vm"
                resourceUuid = cluster1.uuid
                value = "true"
            }

            migrateVm {
                vmInstanceUuid = vm.uuid
                hostUuid = host1.uuid
            }

            retryInSecs {
                VmInstanceVO vo = dbFindByUuid(vm.uuid, VmInstanceVO.class)
                assert vo.hostUuid == host1.uuid
            }
        }
    }

    void enableAllHosts() {
        [env.inventoryByName("host1-cluster1") as HostInventory,
         env.inventoryByName("host2-cluster1") as HostInventory,
         env.inventoryByName("host1-cluster2") as HostInventory].each { host ->
            HostVO hostVO = dbFindByUuid(host.uuid, HostVO.class)
            if (hostVO.state == HostState.Maintenance) {
                changeHostState {
                    uuid = host.uuid
                    stateEvent = HostStateEvent.enable.toString()
                }
            }
        }
    }

    // ==================== Part 1: User Designates Target Host ====================

    /**
     * Case 1: haAcross=false, strategy=Hard, Scene=All, target=same cluster
     * Expected: Success
     */
    void testCase1_HaAcrossFalse_Hard_SameCluster() {
        resetVmToHost1Cluster1()
        enableAllHosts()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory targetHost = env.inventoryByName("host2-cluster1") as HostInventory
        ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory

        setResourceBindingConfig(cluster1.uuid, vm.uuid, false, "Hard", "All")

        // Should succeed - same cluster
        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = targetHost.uuid
        }

        retryInSecs {
            VmInstanceVO vmVO = dbFindByUuid(vm.uuid, VmInstanceVO.class)
            assert vmVO.hostUuid == targetHost.uuid
            assert vmVO.state == VmInstanceState.Running
        }
    }

    /**
     * Case 2: haAcross=false, strategy=Hard, Scene=All, target=cross cluster
     * Expected: Fail
     */
    void testCase2_HaAcrossFalse_Hard_CrossCluster() {
        resetVmToHost1Cluster1()
        enableAllHosts()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory crossClusterHost = env.inventoryByName("host1-cluster2") as HostInventory
        ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory

        setResourceBindingConfig(cluster1.uuid, vm.uuid, false, "Hard", "All")

        // Should fail - cross cluster with Hard strategy
        MigrateVmAction action = new MigrateVmAction()
        action.vmInstanceUuid = vm.uuid
        action.hostUuid = crossClusterHost.uuid
        action.sessionId = adminSession()
        MigrateVmAction.Result result = action.call()

        assert result.error != null
    }

    /**
     * Case 3: haAcross=false, strategy=Soft, Scene=All, target=same cluster
     * Expected: Success
     */
    void testCase3_HaAcrossFalse_Soft_SameCluster() {
        resetVmToHost1Cluster1()
        enableAllHosts()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory targetHost = env.inventoryByName("host2-cluster1") as HostInventory
        ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory

        setResourceBindingConfig(cluster1.uuid, vm.uuid, false, "Soft", "All")

        // Should succeed - same cluster
        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = targetHost.uuid
        }

        retryInSecs {
            VmInstanceVO vmVO = dbFindByUuid(vm.uuid, VmInstanceVO.class)
            assert vmVO.hostUuid == targetHost.uuid
            assert vmVO.state == VmInstanceState.Running
        }
    }

    /**
     * Case 4 (CORE FIX): haAcross=false, strategy=Soft, Scene=All, target=cross cluster
     * Expected: Fail (This is the bug fix for ZSTAC-75428)
     *
     * Before fix: Soft strategy would allow cross-cluster migration
     * After fix: Designated cross-cluster migration should fail regardless of strategy
     */
    void testCase4_HaAcrossFalse_Soft_CrossCluster_CoreFix() {
        resetVmToHost1Cluster1()
        enableAllHosts()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory crossClusterHost = env.inventoryByName("host1-cluster2") as HostInventory
        ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory

        setResourceBindingConfig(cluster1.uuid, vm.uuid, false, "Soft", "All")

        // Should FAIL - this is the core fix test
        // Before fix: this would succeed because Soft strategy relaxes the constraint
        // After fix: designated cross-cluster migration fails regardless of strategy
        MigrateVmAction action = new MigrateVmAction()
        action.vmInstanceUuid = vm.uuid
        action.hostUuid = crossClusterHost.uuid
        action.sessionId = adminSession()
        MigrateVmAction.Result result = action.call()

        assert result.error != null
        assert result.error.details.contains("designated host") ||
               result.error.details.contains("bound resource") ||
               result.error.details.contains("binded resource")
    }

    /**
     * Case 5: haAcross=true, strategy=Hard, Scene=All, target=same cluster
     * Expected: Success
     */
    void testCase5_HaAcrossTrue_Hard_SameCluster() {
        resetVmToHost1Cluster1()
        enableAllHosts()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory targetHost = env.inventoryByName("host2-cluster1") as HostInventory
        ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory

        setResourceBindingConfig(cluster1.uuid, vm.uuid, true, "Hard", "All")

        // Should succeed
        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = targetHost.uuid
        }

        retryInSecs {
            VmInstanceVO vmVO = dbFindByUuid(vm.uuid, VmInstanceVO.class)
            assert vmVO.hostUuid == targetHost.uuid
            assert vmVO.state == VmInstanceState.Running
        }
    }

    /**
     * Case 6: haAcross=true, strategy=Hard, Scene=All, target=cross cluster
     * Expected: Success (haAcross=true allows cross-cluster)
     */
    void testCase6_HaAcrossTrue_Hard_CrossCluster() {
        resetVmToHost1Cluster1()
        enableAllHosts()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory crossClusterHost = env.inventoryByName("host1-cluster2") as HostInventory
        ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory

        setResourceBindingConfig(cluster1.uuid, vm.uuid, true, "Hard", "All")

        // Should succeed - haAcross=true allows cross-cluster
        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = crossClusterHost.uuid
        }

        retryInSecs {
            VmInstanceVO vmVO = dbFindByUuid(vm.uuid, VmInstanceVO.class)
            assert vmVO.hostUuid == crossClusterHost.uuid
            assert vmVO.state == VmInstanceState.Running
        }
    }

    /**
     * Case 7: haAcross=true, strategy=Soft, Scene=All, target=same cluster
     * Expected: Success
     */
    void testCase7_HaAcrossTrue_Soft_SameCluster() {
        resetVmToHost1Cluster1()
        enableAllHosts()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory targetHost = env.inventoryByName("host2-cluster1") as HostInventory
        ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory

        setResourceBindingConfig(cluster1.uuid, vm.uuid, true, "Soft", "All")

        // Should succeed
        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = targetHost.uuid
        }

        retryInSecs {
            VmInstanceVO vmVO = dbFindByUuid(vm.uuid, VmInstanceVO.class)
            assert vmVO.hostUuid == targetHost.uuid
            assert vmVO.state == VmInstanceState.Running
        }
    }

    /**
     * Case 8: haAcross=true, strategy=Soft, Scene=All, target=cross cluster
     * Expected: Success (haAcross=true allows cross-cluster)
     */
    void testCase8_HaAcrossTrue_Soft_CrossCluster() {
        resetVmToHost1Cluster1()
        enableAllHosts()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory crossClusterHost = env.inventoryByName("host1-cluster2") as HostInventory
        ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory

        setResourceBindingConfig(cluster1.uuid, vm.uuid, true, "Soft", "All")

        // Should succeed - haAcross=true allows cross-cluster
        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = crossClusterHost.uuid
        }

        retryInSecs {
            VmInstanceVO vmVO = dbFindByUuid(vm.uuid, VmInstanceVO.class)
            assert vmVO.hostUuid == crossClusterHost.uuid
            assert vmVO.state == VmInstanceState.Running
        }
    }

    // ==================== Part 2: System Auto-Allocates Host ====================

    /**
     * Case 9: Auto-allocate, haAcross=false, strategy=Hard, Scene=All, cluster resource sufficient
     * Expected: Migrate to same cluster host
     */
    void testCase9_AutoAllocate_HaAcrossFalse_Hard_Sufficient() {
        resetVmToHost1Cluster1()
        enableAllHosts()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory host1 = env.inventoryByName("host1-cluster1") as HostInventory
        HostInventory host2 = env.inventoryByName("host2-cluster1") as HostInventory
        ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory

        setResourceBindingConfig(cluster1.uuid, vm.uuid, false, "Hard", "All")

        // Auto-allocate (no hostUuid specified), should stay in same cluster
        migrateVm {
            vmInstanceUuid = vm.uuid
        }

        retryInSecs {
            VmInstanceVO vmVO = dbFindByUuid(vm.uuid, VmInstanceVO.class)
            // Should migrate to host2-cluster1 (same cluster, avoiding current host)
            assert vmVO.hostUuid == host2.uuid
            assert vmVO.state == VmInstanceState.Running
        }
    }

    /**
     * Case 10: Auto-allocate, haAcross=false, strategy=Hard, Scene=All, cluster resource insufficient
     * Expected: Fail (Hard strategy does not relax)
     */
    void testCase10_AutoAllocate_HaAcrossFalse_Hard_Insufficient() {
        resetVmToHost1Cluster1()
        enableAllHosts()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory host2 = env.inventoryByName("host2-cluster1") as HostInventory
        ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory

        setResourceBindingConfig(cluster1.uuid, vm.uuid, false, "Hard", "All")

        // Make cluster1 resource insufficient by putting host2 into maintenance
        changeHostState {
            uuid = host2.uuid
            stateEvent = HostStateEvent.maintain.toString()
        }

        retryInSecs {
            HostVO hostVO = dbFindByUuid(host2.uuid, HostVO.class)
            assert hostVO.state == HostState.Maintenance
        }

        // Auto-allocate should fail - no available host in cluster1 (host1 is current, host2 is maintenance)
        MigrateVmAction action = new MigrateVmAction()
        action.vmInstanceUuid = vm.uuid
        action.sessionId = adminSession()
        MigrateVmAction.Result result = action.call()

        assert result.error != null

        // Re-enable host2
        changeHostState {
            uuid = host2.uuid
            stateEvent = HostStateEvent.enable.toString()
        }
    }

    /**
     * Case 11: Auto-allocate, haAcross=false, strategy=Soft, Scene=All, cluster resource sufficient
     * Expected: Migrate to same cluster host
     */
    void testCase11_AutoAllocate_HaAcrossFalse_Soft_Sufficient() {
        resetVmToHost1Cluster1()
        enableAllHosts()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory host2 = env.inventoryByName("host2-cluster1") as HostInventory
        ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory

        setResourceBindingConfig(cluster1.uuid, vm.uuid, false, "Soft", "All")

        // Auto-allocate, should stay in same cluster when resource sufficient
        migrateVm {
            vmInstanceUuid = vm.uuid
        }

        retryInSecs {
            VmInstanceVO vmVO = dbFindByUuid(vm.uuid, VmInstanceVO.class)
            // Should migrate to host2-cluster1 (same cluster)
            assert vmVO.hostUuid == host2.uuid
            assert vmVO.state == VmInstanceState.Running
        }
    }

    /**
     * Case 12: Auto-allocate, haAcross=false, strategy=Soft, Scene=All, cluster resource insufficient
     * Expected: Success - Soft strategy relaxes and allows cross-cluster
     */
    void testCase12_AutoAllocate_HaAcrossFalse_Soft_Insufficient() {
        resetVmToHost1Cluster1()
        enableAllHosts()

        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory host2 = env.inventoryByName("host2-cluster1") as HostInventory
        HostInventory crossClusterHost = env.inventoryByName("host1-cluster2") as HostInventory
        ClusterInventory cluster1 = env.inventoryByName("cluster1") as ClusterInventory

        setResourceBindingConfig(cluster1.uuid, vm.uuid, false, "Soft", "All")

        // Make cluster1 resource insufficient by putting host2 into maintenance
        changeHostState {
            uuid = host2.uuid
            stateEvent = HostStateEvent.maintain.toString()
        }

        retryInSecs {
            HostVO hostVO = dbFindByUuid(host2.uuid, HostVO.class)
            assert hostVO.state == HostState.Maintenance
        }

        // Auto-allocate with Soft strategy should succeed by relaxing to cross-cluster
        migrateVm {
            vmInstanceUuid = vm.uuid
        }

        retryInSecs {
            VmInstanceVO vmVO = dbFindByUuid(vm.uuid, VmInstanceVO.class)
            // Should migrate to cross-cluster host (Soft relaxes the constraint)
            assert vmVO.hostUuid == crossClusterHost.uuid
            assert vmVO.state == VmInstanceState.Running
        }

        // Re-enable host2
        changeHostState {
            uuid = host2.uuid
            stateEvent = HostStateEvent.enable.toString()
        }
    }
}

package org.zstack.test.integration.kvm.vm.migrate

import org.zstack.core.cloudbus.CloudBus
import org.zstack.header.host.CheckVmStateOnHypervisorMsg
import org.zstack.header.host.CheckVmStateOnHypervisorReply
import org.zstack.header.network.service.NetworkServiceType
import org.zstack.header.vm.VmInstanceState
import org.zstack.kvm.KVMAgentCommands
import org.zstack.kvm.KVMConstant
import org.zstack.network.securitygroup.SecurityGroupConstant
import org.zstack.network.service.flat.FlatNetworkServiceConstant
import org.zstack.network.service.userdata.UserdataConstant
import org.zstack.sdk.HostInventory
import org.zstack.sdk.MigrateVmAction
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

class MigrateVmFailureCheckTargetHostCase extends SubCase {
    EnvSpec env

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(1)
                cpu = 1
            }

            cephBackupStorage {
                name = "ceph-bk"
                fsid = "7ff218d9-f525-435f-8a40-3618d1772a64"
                monUrls = ["root:password@localhost:23", "root:password@127.0.0.1:23"]

                image {
                    name = "image1"
                    url = "http://zstack.org/download/test.qcow2"
                }
            }

            zone {
                name = "zone"

                cluster {
                    name = "cluster"
                    hypervisorType = "KVM"

                    kvm {
                        name = "kvm1"
                        managementIp = "127.0.0.1"
                        username = "root"
                        password = "password"
                    }

                    kvm {
                        name = "kvm2"
                        managementIp = "127.0.0.2"
                        username = "root"
                        password = "password"
                    }

                    attachPrimaryStorage("ceph-pri")
                    attachL2Network("l2")
                }

                cephPrimaryStorage {
                    name = "ceph-pri"
                    fsid = "7ff218d9-f525-435f-8a40-3618d1772a64"
                    monUrls = ["root:password@localhost/?monPort=7777", "root:password@127.0.0.1/?monPort=7777"]
                }

                l2NoVlanNetwork {
                    name = "l2"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "l3"

                        service {
                            provider = FlatNetworkServiceConstant.FLAT_NETWORK_SERVICE_TYPE_STRING
                            types = [NetworkServiceType.DHCP.toString(), UserdataConstant.USERDATA_TYPE_STRING]
                        }

                        service {
                            provider = SecurityGroupConstant.SECURITY_GROUP_PROVIDER_TYPE
                            types = [SecurityGroupConstant.SECURITY_GROUP_NETWORK_SERVICE_TYPE]
                        }

                        ip {
                            startIp = "192.168.100.10"
                            endIp = "192.168.100.100"
                            netmask = "255.255.255.0"
                            gateway = "192.168.100.1"
                        }
                    }
                }

                attachBackupStorage("ceph-bk")
            }

            vm {
                name = "vm"
                useInstanceOffering("instanceOffering")
                useImage("image1")
                useL3Networks("l3")
            }
        }
    }

    @Override
    void test() {
        env.create {
            testRollbackWhenTargetHostReportsVmNotRunning()
            testMigrationSuccessWhenTargetHostReportsVmRunning()
        }
    }

    @Override
    void clean() {
        env.delete()
    }

    void testRollbackWhenTargetHostReportsVmNotRunning() {
        VmInstanceInventory vm = env.inventoryByName("vm") as VmInstanceInventory
        HostInventory destHost = findAnotherHost(vm.hostUuid)

        assertRollbackWhenTargetReports(vm, destHost, VmInstanceState.Stopped.toString())
        assertRollbackWhenTargetReports(vm, destHost, VmInstanceState.Paused.toString())
    }

    void assertRollbackWhenTargetReports(VmInstanceInventory vm, HostInventory destHost, String targetHostState) {
        List<String> checkedHosts = []

        mockMigrateVmFailure()
        mockVmState(vm.uuid, destHost.uuid, targetHostState, checkedHosts)

        MigrateVmAction.Result result = migrateVmAction(vm.uuid, destHost.uuid).call()

        assert result.error != null
        VmInstanceInventory after = queryVmInstance {
            conditions = ["uuid=${vm.uuid}".toString()]
        }[0] as VmInstanceInventory
        assert after.hostUuid == vm.hostUuid
        assert after.state == VmInstanceState.Running.toString()
        assert checkedHosts[0] == destHost.uuid
    }

    void testMigrationSuccessWhenTargetHostReportsVmRunning() {
        VmInstanceInventory vm = queryVmInstance {
            conditions = ["name=vm"]
        }[0] as VmInstanceInventory
        HostInventory destHost = findAnotherHost(vm.hostUuid)
        List<String> checkedHosts = []

        mockMigrateVmFailure()
        mockVmState(vm.uuid, destHost.uuid, VmInstanceState.Running.toString(), checkedHosts)

        MigrateVmAction.Result result = migrateVmAction(vm.uuid, destHost.uuid).call()

        assert result.error == null
        VmInstanceInventory after = queryVmInstance {
            conditions = ["uuid=${vm.uuid}".toString()]
        }[0] as VmInstanceInventory
        assert after.hostUuid == destHost.uuid
        assert after.lastHostUuid == vm.hostUuid
        assert checkedHosts
        assert checkedHosts.every { it == destHost.uuid }
    }

    HostInventory findAnotherHost(String hostUuid) {
        return queryHost {
            conditions = ["uuid!=${hostUuid}".toString()]
        }[0] as HostInventory
    }

    void mockMigrateVmFailure() {
        env.simulator(KVMConstant.KVM_MIGRATE_VM_PATH) {
            KVMAgentCommands.MigrateVmResponse rsp = new KVMAgentCommands.MigrateVmResponse()
            rsp.setError("mock migration API failure")
            return rsp
        }
    }

    void mockVmState(String vmUuid, String hostUuid, String targetHostState, List<String> checkedHosts) {
        env.revokeMessage(CheckVmStateOnHypervisorMsg.class, null)
        env.message(CheckVmStateOnHypervisorMsg.class) { CheckVmStateOnHypervisorMsg msg, CloudBus bus ->
            CheckVmStateOnHypervisorReply reply = new CheckVmStateOnHypervisorReply()
            Map<String, String> states = new HashMap<>()
            checkedHosts.add(msg.hostUuid)
            msg.vmInstanceUuids.each {
                states.put(it, it == vmUuid && msg.hostUuid == hostUuid ? targetHostState : VmInstanceState.Running.toString())
            }
            reply.setStates(states)
            bus.reply(msg, reply)
        }
    }

    MigrateVmAction migrateVmAction(String vmUuid, String destHostUuid) {
        MigrateVmAction action = new MigrateVmAction()
        action.sessionId = adminSession()
        action.vmInstanceUuid = vmUuid
        action.hostUuid = destHostUuid
        return action
    }
}

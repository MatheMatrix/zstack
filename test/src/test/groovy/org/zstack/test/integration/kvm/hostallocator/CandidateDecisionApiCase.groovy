package org.zstack.test.integration.kvm.hostallocator

import org.zstack.header.candidate.CandidateReasonCodes
import org.zstack.header.candidate.CandidateTypes
import org.zstack.header.vm.*
import org.zstack.network.securitygroup.SecurityGroupConstant
import org.zstack.sdk.HostInventory
import org.zstack.sdk.ImageInventory
import org.zstack.sdk.InstanceOfferingInventory
import org.zstack.sdk.L3NetworkInventory
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.Api
import org.zstack.test.ApiSender
import org.zstack.test.ApiSenderException
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.testlib.Test
import org.zstack.utils.data.SizeUnit

class CandidateDecisionApiCase extends SubCase {
    EnvSpec env
    def session
    ApiSender sender
    HostInventory host1
    HostInventory host2
    VmInstanceInventory vm
    ImageInventory image
    InstanceOfferingInventory offering
    L3NetworkInventory l3

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = Test.makeEnv {
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

                cluster {
                    name = "cluster"
                    hypervisorType = "KVM"

                    kvm {
                        name = "kvm1"
                        managementIp = "127.0.0.2"
                        username = "root"
                        password = "password"
                        totalCpu = 8
                        totalMem = SizeUnit.GIGABYTE.toByte(8)
                    }

                    kvm {
                        name = "kvm2"
                        managementIp = "127.0.0.3"
                        username = "root"
                        password = "password"
                        totalCpu = 8
                        totalMem = SizeUnit.GIGABYTE.toByte(8)
                    }

                    attachPrimaryStorage("nfs")
                    attachL2Network("l2")
                }

                nfsPrimaryStorage {
                    name = "nfs"
                    url = "/nfs_root"
                }

                l2NoVlanNetwork {
                    name = "l2"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "l3"

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

                attachBackupStorage("sftp")
            }

            vm {
                name = "vm1"
                useInstanceOffering("instanceOffering")
                useImage("image1")
                useL3Networks("l3")
                useHost("kvm1")
            }
        }
    }

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void test() {
        env.create {
            prepare()
            testMigrationCandidatesIncludeAvoidedCurrentHost()
            changeHostState {
                sessionId = Test.currentEnvSpec.session.uuid
                uuid = host2.uuid
                stateEvent = "disable"
            }
            testMigrationFailureOpaqueIncludesAllRejectedHosts()
            stopVmInstance {
                uuid = vm.uuid
            }
            testCreationCandidatesIncludeDisabledHost()
            testStartingCandidatesIncludeDisabledHost()
            changeHostState {
                sessionId = Test.currentEnvSpec.session.uuid
                uuid = host1.uuid
                stateEvent = "disable"
            }
            testCreationFailureOpaqueIncludesAllRejectedHosts()
            testStartingFailureOpaqueIncludesAllRejectedHosts()
        }
    }

    void prepare() {
        session = new Api().loginAsAdmin()
        sender = new ApiSender()
        sender.setTimeout(60)
        host1 = env.inventoryByName("kvm1") as HostInventory
        host2 = env.inventoryByName("kvm2") as HostInventory
        vm = env.inventoryByName("vm1") as VmInstanceInventory
        image = env.inventoryByName("image1") as ImageInventory
        offering = env.inventoryByName("instanceOffering") as InstanceOfferingInventory
        l3 = env.inventoryByName("l3") as L3NetworkInventory
    }

    void testMigrationCandidatesIncludeAvoidedCurrentHost() {
        APIGetVmMigrationCandidatesMsg msg = new APIGetVmMigrationCandidatesMsg()
        msg.setSession(session)
        msg.setVmInstanceUuid(vm.uuid)

        APIGetVmMigrationCandidatesReply reply = sender.call(msg, APIGetVmMigrationCandidatesReply.class)
        assert reply.candidates.size() == 2
        assert reply.summary.total == 2
        assert reply.summary.selected == 1
        assert reply.summary.rejected == 1

        def records = reply.candidates.collectEntries { [(it.candidateUuid): it] }
        assert records[host1.uuid].decision == CandidateTypes.REJECTED
        assert records[host1.uuid].reason.code == CandidateReasonCodes.HOST_IN_AVOID_LIST
        assert records[host2.uuid].decision == CandidateTypes.SELECTED
    }

    void testCreationCandidatesIncludeDisabledHost() {
        APIGetVmCreationCandidatesMsg msg = new APIGetVmCreationCandidatesMsg()
        msg.setSession(session)
        msg.setInstanceOfferingUuid(offering.uuid)
        msg.setImageUuid(image.uuid)
        msg.setL3NetworkUuids([l3.uuid])

        APIGetVmCreationCandidatesReply reply = sender.call(msg, APIGetVmCreationCandidatesReply.class)
        assert reply.candidates.size() == 2
        assert reply.summary.total == 2
        assert reply.summary.selected == 1
        assert reply.summary.rejected == 1
        assert reply.zones.size() == 1
        assert reply.clusters.size() == 1

        def records = reply.candidates.collectEntries { [(it.candidateUuid): it] }
        assert records[host1.uuid].decision == CandidateTypes.SELECTED
        assert records[host2.uuid].decision == CandidateTypes.REJECTED
        assert records[host2.uuid].reason.code == CandidateReasonCodes.HOST_STATE_NOT_ENABLED
    }

    void testStartingCandidatesIncludeDisabledHost() {
        APIGetVmStartingCandidatesMsg msg = new APIGetVmStartingCandidatesMsg()
        msg.setSession(session)
        msg.setUuid(vm.uuid)

        APIGetVmStartingCandidatesReply reply = sender.call(msg, APIGetVmStartingCandidatesReply.class)
        assert reply.candidates.size() == 2
        assert reply.summary.total == 2
        assert reply.summary.selected == 1
        assert reply.summary.rejected == 1
        assert reply.clusters.size() == 1

        def records = reply.candidates.collectEntries { [(it.candidateUuid): it] }
        assert records[host1.uuid].decision == CandidateTypes.SELECTED
        assert records[host2.uuid].decision == CandidateTypes.REJECTED
        assert records[host2.uuid].reason.code == CandidateReasonCodes.HOST_STATE_NOT_ENABLED
    }

    void testMigrationFailureOpaqueIncludesAllRejectedHosts() {
        APIMigrateVmMsg msg = new APIMigrateVmMsg()
        msg.setSession(session)
        msg.setVmInstanceUuid(vm.uuid)

        def result = expectCandidateDecisionFailure {
            sender.send(msg, APIMigrateVmEvent.class)
        }

        assert result.candidates.size() == 2
        assert result.summary.total == 2
        assert result.summary.selected == 0
        assert result.summary.rejected == 2

        def records = result.candidates.collectEntries { [(it.candidateUuid): it] }
        assert records[host1.uuid].decision == CandidateTypes.REJECTED
        assert records[host1.uuid].reason.code == CandidateReasonCodes.HOST_IN_AVOID_LIST
        assert records[host2.uuid].decision == CandidateTypes.REJECTED
        assert records[host2.uuid].reason.code == CandidateReasonCodes.HOST_STATE_NOT_ENABLED
    }

    void testCreationFailureOpaqueIncludesAllRejectedHosts() {
        APICreateVmInstanceMsg msg = new APICreateVmInstanceMsg()
        msg.setSession(session)
        msg.setName("vm-fail")
        msg.setInstanceOfferingUuid(offering.uuid)
        msg.setImageUuid(image.uuid)
        msg.setL3NetworkUuids([l3.uuid])

        def result = expectCandidateDecisionFailure {
            sender.send(msg, APICreateVmInstanceEvent.class)
        }

        assert result.candidates.size() == 2
        assert result.summary.total == 2
        assert result.summary.selected == 0
        assert result.summary.rejected == 2

        def records = result.candidates.collectEntries { [(it.candidateUuid): it] }
        assert records[host1.uuid].decision == CandidateTypes.REJECTED
        assert records[host1.uuid].reason.code == CandidateReasonCodes.HOST_STATE_NOT_ENABLED
        assert records[host2.uuid].decision == CandidateTypes.REJECTED
        assert records[host2.uuid].reason.code == CandidateReasonCodes.HOST_STATE_NOT_ENABLED
    }

    void testStartingFailureOpaqueIncludesAllRejectedHosts() {
        APIStartVmInstanceMsg msg = new APIStartVmInstanceMsg()
        msg.setSession(session)
        msg.setUuid(vm.uuid)

        def result = expectCandidateDecisionFailure {
            sender.send(msg, APIStartVmInstanceEvent.class)
        }

        assert result.candidates.size() == 2
        assert result.summary.total == 2
        assert result.summary.selected == 0
        assert result.summary.rejected == 2

        def records = result.candidates.collectEntries { [(it.candidateUuid): it] }
        assert records[host1.uuid].decision == CandidateTypes.REJECTED
        assert records[host1.uuid].reason.code == CandidateReasonCodes.HOST_STATE_NOT_ENABLED
        assert records[host2.uuid].decision == CandidateTypes.REJECTED
        assert records[host2.uuid].reason.code == CandidateReasonCodes.HOST_STATE_NOT_ENABLED
    }

    def expectCandidateDecisionFailure(Closure action) {
        try {
            action.call()
            assert false
        } catch (ApiSenderException e) {
            def result = e.error.getFromOpaque("candidateDecisionResult")
            assert result != null
            return result
        }
    }
}

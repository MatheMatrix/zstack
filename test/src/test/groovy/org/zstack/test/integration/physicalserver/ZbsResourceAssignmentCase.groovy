package org.zstack.test.integration.physicalserver

import org.springframework.http.HttpEntity
import org.zstack.core.componentloader.PluginRegistry
import org.zstack.core.db.Q
import org.zstack.header.core.Completion
import org.zstack.header.core.ReturnValueCompletion
import org.zstack.header.errorcode.ErrorCode
import org.zstack.header.host.HostNUMANode
import org.zstack.header.host.HostVO
import org.zstack.header.physicalserver.ResourceControlCommand
import org.zstack.header.physicalserver.ResourceControlResponse
import org.zstack.header.physicalserver.ResourceControlResult
import org.zstack.kvm.KVMConstant
import org.zstack.kvm.KVMAgentCommands
import org.zstack.kvm.KvmPhysicalServerAdapter
import org.zstack.sdk.DeletePrimaryStorageAction
import org.zstack.sdk.HostInventory
import org.zstack.sdk.PhysicalServerResourceAssignmentInventory
import org.zstack.sdk.PrimaryStorageInventory
import org.zstack.sdk.RefreshPhysicalServerResourceAssignmentsAction
import org.zstack.sdk.SystemTagInventory
import org.zstack.storage.zbs.AddonInfo
import org.zstack.storage.zbs.FakeZbsCpuIsolationProvider
import org.zstack.storage.zbs.LogicalPoolInfo
import org.zstack.storage.zbs.MdsInfo
import org.zstack.storage.zbs.ZbsCpuIsolationFact
import org.zstack.storage.zbs.ZbsCpuIsolationProvider
import org.zstack.storage.zbs.ZbsNodeRefContributor
import org.zstack.storage.zbs.ZbsNodeRefContributorImpl
import org.zstack.storage.zbs.ZbsPrimaryStorageMdsBase
import org.zstack.storage.zbs.ZbsResourceAssignmentBackend
import org.zstack.storage.zbs.ZbsResourceAssignmentGlobalConfig
import org.zstack.storage.zbs.ZbsStorageController
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageVO
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageVO_
import org.zstack.test.integration.kvm.host.HostEnv
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SpringSpec
import org.zstack.testlib.SubCase
import org.zstack.testlib.Test
import org.zstack.utils.data.SizeUnit
import org.zstack.utils.gson.JSONObjectUtil

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ZbsResourceAssignmentCase extends SubCase {
    static SpringSpec springSpec = Test.makeSpring {
        sftpBackupStorage()
        localStorage()
        flatNetwork()
        securityGroup()
        kvm()
        externalPrimaryStorage()
        zbs()
        physicalServer()
    }

    static final String SERIAL = "zbs-hci-physical-server-case"

    EnvSpec env
    HostInventory host
    String serverUuid
    FakeZbsCpuIsolationProvider provider
    ZbsResourceAssignmentBackend backend
    PrimaryStorageInventory first
    PrimaryStorageInventory second

    @Override
    void setup() {
        useSpring(springSpec)
    }

    @Override
    void environment() {
        env = HostEnv.oneHostEnv()
    }

    @Override
    void clean() {
        ZbsResourceAssignmentGlobalConfig.PROVIDER_CALL_TIMEOUT.resetValue()
        if (provider != null) {
            provider.disable()
        }
        env.delete()
    }

    @Override
    void test() {
        env.create {
            host = env.inventoryByName("kvm") as HostInventory
            provider = bean(FakeZbsCpuIsolationProvider.class)
            backend = bean(ZbsResourceAssignmentBackend.class)
            installKvmSimulators()
            installZbsSerialSimulator()
            provider.enable()
            provider.setAutoApply(true)

            first = addZbs("zbs-assignment-1", "127.0.1.11")
            serverUuid = physicalServerUuid(SERIAL)
            provider.setFact(serverUuid, disabledFact())
            associateHost(SERIAL)

            verifyHyperconvergedDefaults()
            verifyProviderUpdateNoopFailureAndTimeout()
            verifySerialPriorityAndIpBackfill()
            verifyCascadeKeepsSharedRelationAndReleasesLastRelation()
            verifyForceDeleteForgetsLedgerAfterProviderFailure()
            verifyAddonInfoMoveReleasesOldRelation()
            verifyProviderAmbiguityIsRejected()
        }
    }

    private void installKvmSimulators() {
        env.simulator(KVMConstant.KVM_HOST_NUMA_PATH) {
            HostNUMANode node = new HostNUMANode()
            node.nodeID = "0"
            node.cpus = ["0", "1", "2", "3", "4", "5", "6", "7"]
            node.onlineCpus = node.cpus
            node.coreGroups = [
                    ["0", "4"], ["1", "5"],
                    ["2", "6"], ["3", "7"]
            ]
            node.distance = ["10"]
            node.free = SizeUnit.GIGABYTE.toByte(8)
            node.size = SizeUnit.GIGABYTE.toByte(8)
            KVMAgentCommands.GetHostNUMATopologyResponse response =
                    new KVMAgentCommands.GetHostNUMATopologyResponse()
            response.topology = ["0": node]
            return response
        }
        env.simulator(KvmPhysicalServerAdapter.APPLY_RESOURCE_CONTROL_PATH) {
            HttpEntity<String> entity ->
                KvmPhysicalServerAdapter.ResourceControlAgentCommand command =
                        JSONObjectUtil.toObject(
                                entity.body,
                                KvmPhysicalServerAdapter.ResourceControlAgentCommand.class)
                boolean release = command.operation == "RELEASE"
                KvmPhysicalServerAdapter.ResourceControlAgentResponse response =
                        new KvmPhysicalServerAdapter.ResourceControlAgentResponse()
                response.cpuSet = release ? "" : command.cpuSet
                response.memory = command.memory == null
                        ? null : release ? 0L : command.memory
                response.expectedServiceCount = 1
                response.coveredServiceCount = 1
                ResourceControlResult result = new ResourceControlResult()
                result.state = release ? "DISABLED" : "READY"
                result.cpuSet = response.cpuSet
                result.memory = response.memory
                response.results = [result]
                return response
        }
    }

    private void installZbsSerialSimulator() {
        env.simulator(ZbsStorageController.GET_FACTS_PATH) {
            ZbsStorageController.GetFactsRsp response =
                    new ZbsStorageController.GetFactsRsp()
            response.uuid = "zbs-resource-assignment-case"
            response.version = "1.6.1-for-test"
            response.success = true
            return response
        }
        env.simulator(ZbsStorageController.GET_CAPACITY_PATH) {
            HttpEntity<String> entity ->
                ZbsStorageController.GetCapacityCmd command =
                        JSONObjectUtil.toObject(
                                entity.body,
                                ZbsStorageController.GetCapacityCmd.class)
                LogicalPoolInfo pool = new LogicalPoolInfo()
                pool.logicalPoolName = command.logicalPoolNames[0]
                pool.physicalPoolName = "pool1"
                pool.capacity = SizeUnit.TERABYTE.toByte(1)
                pool.allocatedSize = SizeUnit.GIGABYTE.toByte(1)
                pool.usedSize = SizeUnit.GIGABYTE.toByte(1)
                ZbsStorageController.GetCapacityRsp response =
                        new ZbsStorageController.GetCapacityRsp()
                response.logicalPoolInfos = [pool]
                return response
        }
        env.afterSimulator(ZbsPrimaryStorageMdsBase.SYNC_METADATA_PATH) {
            ZbsPrimaryStorageMdsBase.SyncMetadataRsp response,
            HttpEntity<String> entity ->
                ZbsPrimaryStorageMdsBase.SyncMetadataCmd command =
                        JSONObjectUtil.toObject(
                                entity.body,
                                ZbsPrimaryStorageMdsBase.SyncMetadataCmd.class)
                if (command.addr.startsWith("127.0.9.")) {
                    response.physicalServerSerialNumber =
                            "zbs-moved-physical-server-case"
                } else if (command.addr != host.managementIp) {
                    response.physicalServerSerialNumber = SERIAL
                }
                return response
        }
    }

    private void verifyHyperconvergedDefaults() {
        String targetUuid = serverUuid
        refreshPhysicalServerResourceAssignments {
            delegate.serverUuid = targetUuid
        }
        retryInSecs {
            PhysicalServerResourceAssignmentInventory zbs = assignment(
                    serverUuid, "ZBS")
            PhysicalServerResourceAssignmentInventory compute = assignment(
                    serverUuid, "COMPUTE")
            assert zbs.state == "Synced" &&
                    zbs.cpuSet == "2-3,6-7" :
                    "ZBS must own the rear 50 percent as complete SMT cores: " +
                            "expected=Synced/2-3,6-7 " +
                            "actual=${zbs.state}/${zbs.cpuSet}"
            assert compute.state == "Synced" &&
                    compute.cpuSet == "0-1,4-5" :
                    "COMPUTE shared CPUs must exclude the ZBS exclusive set: " +
                            "expected=Synced/0-1,4-5 " +
                            "actual=${compute.state}/${compute.cpuSet}"
        }
        assert provider.updateCalls >= 1 :
                "default ZBS reconciliation must call the Provider update API: " +
                        "actualCalls=${provider.updateCalls}"

        def service = getPhysicalServerManagedServices {
            delegate.serverUuid = targetUuid
        }.services.find { it.roleType == "ZBS" }
        assert service?.serviceName == "zbs-node" &&
                service.state == "RUNNING" :
                "ZBS owner fact must be exposed as a managed service: " +
                        "actual=${service}"
        assert !service.restartable :
                "Cloud must not restart a Provider-managed ZBS service: " +
                        "actualRestartable=${service.restartable}"

        RefreshPhysicalServerResourceAssignmentsAction restart =
                new RefreshPhysicalServerResourceAssignmentsAction(
                        sessionId: adminSession(),
                        serverUuid: serverUuid,
                        roleType: "ZBS",
                        serviceNames: ["zbs-node"])
        def result = restart.call()
        assert result.error?.details?.contains(
                "SERVICE_RESTART_NOT_SUPPORTED") :
                "Provider-managed ZBS must reject Cloud restart: " +
                        "actual=${result.error}"
    }

    private void verifyProviderUpdateNoopFailureAndTimeout() {
        String targetUuid = serverUuid
        updatePhysicalServerResourceAssignment {
            delegate.serverUuid = targetUuid
            roleType = "ZBS"
            cpuSet = "3,7"
            memory = SizeUnit.GIGABYTE.toByte(1)
        }
        retryInSecs {
            PhysicalServerResourceAssignmentInventory current = assignment(
                    serverUuid, "ZBS")
            assert current.state == "Synced" &&
                    current.cpuSet == "3,7" &&
                    current.memory == SizeUnit.GIGABYTE.toByte(1) :
                    "ZBS PATCH must be confirmed by a second Provider Query: " +
                            "actual=${current.state}/${current.cpuSet}/${current.memory}"
        }
        int updatesBeforeNoop = provider.updateCalls
        refreshPhysicalServerResourceAssignments {
            delegate.serverUuid = targetUuid
        }
        retryInSecs {
            assert assignment(serverUuid, "ZBS").state == "Synced" :
                    "matching Provider fact must keep the Assignment Synced"
        }
        assert provider.updateCalls == updatesBeforeNoop :
                "matching desired and observed facts must not write Provider state again: " +
                        "before=${updatesBeforeNoop} after=${provider.updateCalls}"

        provider.setAutoApply(false)
        updatePhysicalServerResourceAssignment {
            delegate.serverUuid = targetUuid
            roleType = "ZBS"
            cpuSet = "2,6"
        }
        retryInSecs {
            assert assignment(serverUuid, "ZBS").state == "Unsynced" :
                    "Provider success without observed convergence must remain Unsynced"
        }
        provider.setAutoApply(true)
        refreshPhysicalServerResourceAssignments {
            delegate.serverUuid = targetUuid
        }
        retryInSecs {
            PhysicalServerResourceAssignmentInventory recovered = assignment(
                    serverUuid, "ZBS")
            assert recovered.state == "Synced" &&
                    recovered.cpuSet == "2,6" :
                    "a later Provider convergence must recover the same ledger row: " +
                            "actual=${recovered.state}/${recovered.cpuSet}"
        }

        verifyProviderTimeout(false)
        verifyProviderTimeout(true)
    }

    private void verifyProviderTimeout(boolean holdUpdate) {
        ZbsResourceAssignmentGlobalConfig.PROVIDER_CALL_TIMEOUT.updateValue(1)
        if (holdUpdate) {
            provider.setFact(serverUuid, disabledFact())
            provider.holdNextUpdate()
        } else {
            provider.holdNextQuery()
        }
        ResourceControlCommand command = new ResourceControlCommand()
        command.roleType = "ZBS"
        command.operation = "APPLY"
        command.cpuSet = "2,6"
        command.memory = SizeUnit.GIGABYTE.toByte(1)
        CountDownLatch completed = new CountDownLatch(1)
        AtomicReference<ErrorCode> failure = new AtomicReference<>()
        backend.apply(
                serverUuid,
                null,
                command,
                new ReturnValueCompletion<ResourceControlResponse>(null) {
            @Override
            void success(ResourceControlResponse ignored) {
                completed.countDown()
            }

            @Override
            void fail(ErrorCode errorCode) {
                failure.set(errorCode)
                completed.countDown()
            }
        })
        assert completed.await(5, TimeUnit.SECONDS) :
                "Provider timeout must complete within the configured bound: " +
                        "phase=${holdUpdate ? 'Update' : 'Query'}"
        assert failure.get()?.details?.contains("ZBS_PROVIDER_CALL_TIMEOUT") :
                "Provider timeout must retain a typed Query/Update failure: " +
                        "phase=${holdUpdate ? 'Update' : 'Query'} " +
                        "actual=${failure.get()}"
        if (holdUpdate) {
            provider.completeHeldUpdate()
        } else {
            provider.completeHeldQuery()
        }
        provider.setAutoApply(true)
        provider.setFact(
                serverUuid,
                readyFact("2,6", SizeUnit.GIGABYTE.toByte(1)))
        ZbsResourceAssignmentGlobalConfig.PROVIDER_CALL_TIMEOUT.resetValue()
    }

    private void verifySerialPriorityAndIpBackfill() {
        ZbsNodeRefContributorImpl refs = bean(
                ZbsNodeRefContributorImpl.class)
        def serialRef = refs.bulkList([serverUuid])[serverUuid]
        assert serialRef?.primaryStorageUuids?.contains(first.uuid) :
                "a stable MDS serial must relate ZBS even when its address differs from Host IP: " +
                        "primaryStorageUuid=${first.uuid} actual=${serialRef?.primaryStorageUuids}"

        second = addZbs("zbs-assignment-ip-backfill", host.managementIp)
        retryInSecs {
            List<MdsInfo> infos = mdsInfos(second.uuid)
            assert infos.size() == 1 &&
                    infos[0].physicalServerSerialNumber == SERIAL :
                    "legacy address matching may only backfill the stable serial identity: " +
                            "expected=${SERIAL} actual=${infos*.physicalServerSerialNumber}"
        }
        def combined = refs.bulkList([serverUuid])[serverUuid]
        assert combined.primaryStorageUuids.containsAll([
                first.uuid, second.uuid
        ]) && combined.sourceRefCount == 2 :
                "multiple ZBS relations on one machine must collapse into one node ref: " +
                        "actualPrimaryStorages=${combined.primaryStorageUuids} " +
                        "actualCount=${combined.sourceRefCount}"
    }

    private void verifyCascadeKeepsSharedRelationAndReleasesLastRelation() {
        int updatesBeforeFirstDelete = provider.updateCalls
        DeletePrimaryStorageAction.Result firstDelete =
                deletePrimaryStorage(first.uuid, "Permissive")
        assert firstDelete.error == null :
                "deleting one of multiple ZBS relations must succeed: " +
                        "actual=${firstDelete.error}"
        assert provider.updateCalls == updatesBeforeFirstDelete :
                "deleting a non-last relation must not disable machine isolation: " +
                        "before=${updatesBeforeFirstDelete} " +
                        "after=${provider.updateCalls}"
        assert assignment(serverUuid, "ZBS") != null :
                "the shared ZBS Assignment must survive while another relation exists"

        provider.disable()
        DeletePrimaryStorageAction.Result blocked =
                deletePrimaryStorage(second.uuid, "Permissive")
        assert blocked.error?.details?.contains("ZBS_PROVIDER_UNAVAILABLE") :
                "normal deletion of the last relation must stop if release cannot be verified: " +
                        "actual=${blocked.error}"
        assert queryPrimaryStorage {
            conditions = ["uuid=${second.uuid}"]
        }.size() == 1 :
                "blocked Cascade must preserve the last ZBS PrimaryStorage relation"
        assert assignment(serverUuid, "ZBS").state == "Unsynced" :
                "failed release must preserve an Unsynced ledger for retry"

        provider.enable()
        provider.setAutoApply(true)
        provider.setFact(
                serverUuid,
                readyFact("2,6", SizeUnit.GIGABYTE.toByte(1)))
        DeletePrimaryStorageAction.Result deleted =
                deletePrimaryStorage(second.uuid, "Permissive")
        assert deleted.error == null :
                "last relation deletion must continue after Provider release succeeds: " +
                        "actual=${deleted.error}"
        retryInSecs {
            assert assignments(serverUuid, "ZBS").isEmpty() :
                    "last relation Cascade must delete the ZBS Assignment ledger"
        }
    }

    private void verifyForceDeleteForgetsLedgerAfterProviderFailure() {
        String targetUuid = serverUuid
        PrimaryStorageInventory forced = addZbs(
                "zbs-assignment-force-delete", "127.0.1.13")
        provider.setFact(serverUuid, disabledFact())
        refreshPhysicalServerResourceAssignments {
            delegate.serverUuid = targetUuid
        }
        retryInSecs {
            assert assignment(serverUuid, "ZBS").state == "Synced" :
                    "force-delete scenario requires a converged ZBS Assignment first"
        }

        provider.disable()
        DeletePrimaryStorageAction.Result result =
                deletePrimaryStorage(forced.uuid, "Enforcing")
        assert result.error == null :
                "Enforcing deletion must not be blocked by an unreachable Provider: " +
                        "actual=${result.error}"
        retryInSecs {
            assert assignments(serverUuid, "ZBS").isEmpty() :
                    "Enforcing last-relation deletion must not leave Role configuration"
        }
        provider.enable()
        provider.setAutoApply(true)
    }

    private void verifyAddonInfoMoveReleasesOldRelation() {
        String targetUuid = serverUuid
        provider.setFact(serverUuid, disabledFact())
        PrimaryStorageInventory moved = addZbs(
                "zbs-assignment-addon-move", "127.0.1.14")
        refreshPhysicalServerResourceAssignments {
            delegate.serverUuid = targetUuid
        }
        retryInSecs {
            assert assignment(serverUuid, "ZBS").state == "Synced" :
                    "addonInfo move scenario requires the old relation to be active"
        }

        updateExternalPrimaryStorage {
            uuid = moved.uuid
            config = zbsConfig("127.0.9.14")
        }
        retryInSecs {
            assert assignments(serverUuid, "ZBS").isEmpty() :
                    "moving the last MDS identity must release the old server Assignment before persist"
        }
        String movedServerUuid = physicalServerUuid(
                "zbs-moved-physical-server-case")
        assert movedServerUuid != serverUuid :
                "a different stable serial must resolve to a different PhysicalServer: " +
                        "old=${serverUuid} new=${movedServerUuid}"

        DeletePrimaryStorageAction.Result cleanup =
                deletePrimaryStorage(moved.uuid, "Enforcing")
        assert cleanup.error == null :
                "moved test PrimaryStorage must be force-cleanable: " +
                        "actual=${cleanup.error}"
        retryInSecs {
            assert assignments(movedServerUuid, "ZBS").isEmpty() :
                    "force cleanup must forget the moved server ledger"
        }
    }

    private void verifyProviderAmbiguityIsRejected() {
        ZbsCpuIsolationProvider duplicate = [
                getProviderType: { "DUPLICATE" },
                isAvailable: { ref -> true },
                query: { ref, completion ->
                    completion.success(disabledFact())
                },
                update: { ref, update, completion ->
                    completion.success()
                }
        ] as ZbsCpuIsolationProvider
        bean(PluginRegistry.class).defineDynamicExtension(
                ZbsCpuIsolationProvider.class, duplicate)

        PrimaryStorageInventory ambiguous = addZbs(
                "zbs-assignment-provider-ambiguous", "127.0.1.15")
        retryInSecs {
            backend.refreshAssociations([serverUuid])
            assert backend.getState(serverUuid).name() == "UNAVAILABLE" :
                    "two available Providers must disable execution for the node"
            assert backend.getUnavailableReason(serverUuid) ==
                    "ZBS_PROVIDER_AMBIGUOUS" :
                    "Provider ambiguity must expose a deterministic reason: " +
                            "actual=${backend.getUnavailableReason(serverUuid)}"
        }
        deletePrimaryStorage(ambiguous.uuid, "Enforcing")
    }

    private PrimaryStorageInventory addZbs(String name, String address) {
        return addExternalPrimaryStorage {
            delegate.zoneUuid = host.zoneUuid
            delegate.name = name
            delegate.identity = "zbs"
            delegate.defaultOutputProtocol = "CBD"
            delegate.config = zbsConfig(address)
            delegate.url = "zbs"
        } as PrimaryStorageInventory
    }

    private String zbsConfig(String address) {
        return JSONObjectUtil.toJsonString([
                mdsUrls: ["root:password@${address}".toString()],
                logicalPoolName: "lpool1"
        ])
    }

    private void associateHost(String serialNumber) {
        List<SystemTagInventory> tags = querySystemTag {
            conditions = [
                    "resourceUuid=${host.uuid}",
                    "tag~=systemSerialNumber::%"
            ]
        } as List<SystemTagInventory>
        if (tags.isEmpty()) {
            createSystemTag {
                resourceUuid = host.uuid
                resourceType = "HostVO"
                tag = "systemSerialNumber::${serialNumber}"
            }
        } else {
            updateSystemTag {
                uuid = tags[0].uuid
                tag = "systemSerialNumber::${serialNumber}"
            }
        }
        bean(KvmPhysicalServerAdapter.class).associate(
                org.zstack.header.host.HostInventory.valueOf(
                        dbFindByUuid(host.uuid, HostVO.class)))
        retryInSecs {
            HostInventory current = (queryHost {
                conditions = ["uuid=${host.uuid}"]
            } as List<HostInventory>)[0]
            assert current.serverUuid == serverUuid :
                    "Host and ZBS serials must compose onto one PhysicalServer: " +
                            "expected=${serverUuid} actual=${current.serverUuid}"
            host = current
        }
    }

    private String physicalServerUuid(String serialNumber) {
        AtomicReference<String> found = new AtomicReference<>()
        retryInSecs {
            def servers = queryPhysicalServer {
                conditions = ["serialNumber=${serialNumber}"]
            }
            assert servers.size() == 1 :
                    "one stable serial must resolve to exactly one PhysicalServer: " +
                            "serial=${serialNumber} actual=${servers.size()}"
            found.set(servers[0].uuid)
        }
        return found.get()
    }

    private List<MdsInfo> mdsInfos(String primaryStorageUuid) {
        String addonInfo = Q.New(ExternalPrimaryStorageVO.class)
                .select(ExternalPrimaryStorageVO_.addonInfo)
                .eq(ExternalPrimaryStorageVO_.uuid, primaryStorageUuid)
                .findValue()
        assert addonInfo != null :
                "the ZBS PrimaryStorage must exist while reading addonInfo: " +
                        "uuid=${primaryStorageUuid} actual=missing"
        return JSONObjectUtil.toObject(
                addonInfo, AddonInfo.class).mdsInfos
    }

    private DeletePrimaryStorageAction.Result deletePrimaryStorage(
            String uuid, String mode) {
        return new DeletePrimaryStorageAction(
                sessionId: adminSession(),
                uuid: uuid,
                deleteMode: mode).call()
    }

    private PhysicalServerResourceAssignmentInventory assignment(
            String targetServerUuid, String roleType) {
        List<PhysicalServerResourceAssignmentInventory> rows = assignments(
                targetServerUuid, roleType)
        assert rows.size() == 1 :
                "server and Role must identify exactly one Assignment: " +
                        "serverUuid=${targetServerUuid} roleType=${roleType} " +
                        "actual=${rows.size()}"
        return rows[0]
    }

    private List<PhysicalServerResourceAssignmentInventory> assignments(
            String targetServerUuid, String roleType) {
        return queryPhysicalServerResourceAssignment {
            conditions = [
                    "serverUuid=${targetServerUuid}",
                    "roleType=${roleType}"
            ]
        } as List<PhysicalServerResourceAssignmentInventory>
    }

    private static ZbsCpuIsolationFact disabledFact() {
        ZbsCpuIsolationFact fact = new ZbsCpuIsolationFact()
        fact.isolationState = "DISABLED"
        fact.configuredCpuSet = ""
        fact.effectiveCpuSet = ""
        fact.expectedServiceCount = 1
        fact.coveredServiceCount = 1
        fact.serviceReady = true
        return fact
    }

    private static ZbsCpuIsolationFact readyFact(
            String cpuSet, Long memory) {
        ZbsCpuIsolationFact fact = new ZbsCpuIsolationFact()
        fact.isolationState = "READY"
        fact.configuredCpuSet = cpuSet
        fact.effectiveCpuSet = cpuSet
        fact.memory = memory
        fact.expectedServiceCount = 1
        fact.coveredServiceCount = 1
        fact.serviceReady = true
        return fact
    }
}

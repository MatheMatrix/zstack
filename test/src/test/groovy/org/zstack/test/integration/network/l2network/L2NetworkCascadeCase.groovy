package org.zstack.test.integration.network.l2network

import org.zstack.core.componentloader.PluginRegistry
import org.zstack.core.cloudbus.CloudBus
import org.zstack.core.db.DatabaseFacade
import org.zstack.header.core.Completion
import org.zstack.header.errorcode.ErrorCode
import org.zstack.header.message.AbstractBeforeDeliveryMessageInterceptor
import org.zstack.header.message.Message
import org.zstack.header.network.NetworkConfigLocalContinuation
import org.zstack.header.network.NetworkConfigMutation
import org.zstack.header.network.NetworkConfigMutationExtensionPoint
import org.zstack.header.network.l2.L2DeleteConfirmExtensionPoint
import org.zstack.header.network.l2.L2NetworkInventory
import org.zstack.header.network.l2.L2NetworkVO
import org.zstack.header.network.l2.NetworkDeletionContext
import org.zstack.header.network.l3.IpRangeDeletionMsg
import org.zstack.header.network.l3.L3NetworkDeleteExtensionPoint
import org.zstack.header.network.l3.L3NetworkDeletionMsg
import org.zstack.header.network.l3.L3NetworkException
import org.zstack.header.network.l3.L3NetworkInventory
import org.zstack.header.network.l3.L3NetworkVO
import org.zstack.sdk.L2NetworkInventory as SdkL2NetworkInventory
import org.zstack.sdk.ZoneInventory
import org.zstack.test.integration.network.NetworkTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

class L2NetworkCascadeCase extends SubCase {
    EnvSpec env
    DatabaseFacade dbf

    @Override
    void setup() {
        useSpring(NetworkTest.springSpec)
    }

    @Override
    void environment() {
        env = env {
            zone {
                name = "check-zone"

                l2NoVlanNetwork {
                    name = "check-l2-1"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "check-l3"
                    }
                }

                l2NoVlanNetwork {
                    name = "check-l2-2"
                    physicalInterface = "eth1"
                }
            }

            zone {
                name = "cleanup-zone"

                l2NoVlanNetwork {
                    name = "cleanup-l2"
                    physicalInterface = "eth2"

                    l3Network {
                        name = "cleanup-l3"

                        ip {
                            startIp = "192.168.80.10"
                            endIp = "192.168.80.20"
                            netmask = "255.255.255.0"
                            gateway = "192.168.80.1"
                        }
                    }
                }

                l2NoVlanNetwork {
                    name = "force-l2"
                    physicalInterface = "eth3"
                }

                l2NoVlanNetwork {
                    name = "force-check-l2"
                    physicalInterface = "eth6"

                    l3Network {
                        name = "force-check-l3"
                    }
                }

                l2NoVlanNetwork {
                    name = "metadata-l2"
                    physicalInterface = "eth4"
                }

                l2NoVlanNetwork {
                    name = "plain-l2"
                    physicalInterface = "eth5"
                }

                l2NoVlanNetwork {
                    name = "metadata-update-l2"
                    physicalInterface = "eth7"
                }
            }
        }
    }

    @Override
    void test() {
        env.create {
            dbf = bean(DatabaseFacade.class)
            testFenceExistsBeforeChildCheck()
            testDeleteZoneCancelsEveryConfirmedDeleteOnCheckFailure()
            testWholeL2ContextPropagatesToChildren()
            testParentForceChecksBeforeDeletingChildren()
            testParentForcePreservesProviderError()
            testUnsupportedProviderKeepsExistingDeleteBehavior()
            testL2MetadataMutationCommitsRemoteBeforeLocal()
            testDeleteL2NetworkRemovesConfirmedMetadataOnce()
        }
    }

    void testFenceExistsBeforeChildCheck() {
        ZoneInventory zone = env.inventoryByName("check-zone")
        List<String> events = []
        def confirmation = new RecordingDeleteConfirmation(events: events)
        def childCheck = new RecordingL3DeleteCheck(events: events, failCheck: true)
        List<L2DeleteConfirmExtensionPoint> confirmations = bean(PluginRegistry.class)
                .getExtensionList(L2DeleteConfirmExtensionPoint.class)
        List<L3NetworkDeleteExtensionPoint> childChecks = bean(PluginRegistry.class)
                .getExtensionList(L3NetworkDeleteExtensionPoint.class)
        confirmations.add(confirmation)
        childChecks.add(childCheck)

        try {
            expectError {
                deleteZone {
                    uuid = zone.uuid
                }
            }
        } finally {
            childChecks.remove(childCheck)
            confirmations.remove(confirmation)
        }

        assert events.findIndexOf { it.startsWith("child-check:") } >
                events.findLastIndexOf { it.startsWith("begin:") }
    }

    void testDeleteZoneCancelsEveryConfirmedDeleteOnCheckFailure() {
        ZoneInventory zone = env.inventoryByName("check-zone")
        def extension = new RecordingDeleteConfirmation(failOnSecondCheck: true)
        List<L2DeleteConfirmExtensionPoint> extensions = bean(PluginRegistry.class)
                .getExtensionList(L2DeleteConfirmExtensionPoint.class)
        extensions.add(extension)

        try {
            expectError {
                deleteZone {
                    uuid = zone.uuid
                }
            }
        } finally {
            extensions.remove(extension)
        }

        assert extension.begun.size() == 2
        assert extension.cancelled == extension.begun.toList().reverse()
        assert dbf.isExist(zone.uuid, org.zstack.header.zone.ZoneVO.class)
        assert extension.begun.every { dbf.isExist(it, L2NetworkVO.class) }
    }

    void testDeleteL2NetworkRemovesConfirmedMetadataOnce() {
        SdkL2NetworkInventory l2 = env.inventoryByName("metadata-l2")
        def extension = new RecordingDeleteConfirmation()
        List<L2DeleteConfirmExtensionPoint> extensions = bean(PluginRegistry.class)
                .getExtensionList(L2DeleteConfirmExtensionPoint.class)
        extensions.add(extension)

        try {
            deleteL2Network {
                uuid = l2.uuid
            }
        } finally {
            extensions.remove(extension)
        }

        assert !dbf.isExist(l2.uuid, L2NetworkVO.class)
        assert extension.checkCount == 1
        assert extension.localMetadataDeletes == [l2.uuid]
    }

    void testWholeL2ContextPropagatesToChildren() {
        SdkL2NetworkInventory l2 = env.inventoryByName("cleanup-l2")
        def extension = new RecordingDeleteConfirmation()
        List<L2DeleteConfirmExtensionPoint> extensions = bean(PluginRegistry.class)
                .getExtensionList(L2DeleteConfirmExtensionPoint.class)
        extensions.add(extension)
        List<NetworkDeletionContext> contexts = []
        bean(CloudBus.class).installBeforeDeliveryMessageInterceptor(
                new AbstractBeforeDeliveryMessageInterceptor() {
                    @Override
                    void beforeDeliveryMessage(Message msg) {
                        contexts.add(msg.networkDeletionContext)
                    }
                }, L3NetworkDeletionMsg.class, IpRangeDeletionMsg.class)

        try {
            deleteL2Network {
                uuid = l2.uuid
            }
        } finally {
            extensions.remove(extension)
        }

        assert contexts.size() == 2
        assert contexts.every { it?.origin == NetworkDeletionContext.Origin.WHOLE_L2_SEGMENT_DELETE }
        assert contexts.every { it.rootIssuer == L2NetworkVO.simpleName }
        assert contexts*.operationUuid.toSet() == extension.operationUuids.toSet()
    }

    void testParentForcePreservesProviderError() {
        SdkL2NetworkInventory l2 = env.inventoryByName("force-l2")
        def extension = new RecordingDeleteConfirmation(failDelete: true)
        List<L2DeleteConfirmExtensionPoint> extensions = bean(PluginRegistry.class)
                .getExtensionList(L2DeleteConfirmExtensionPoint.class)
        extensions.add(extension)

        try {
            expectError {
                deleteL2Network {
                    uuid = l2.uuid
                    deleteMode = "Enforcing"
                }
            }
        } finally {
            extensions.remove(extension)
        }

        assert dbf.isExist(l2.uuid, L2NetworkVO.class)
        assert extension.forceDeleteFlags == [true]
    }

    void testParentForceChecksBeforeDeletingChildren() {
        SdkL2NetworkInventory l2 = env.inventoryByName("force-check-l2")
        def l3 = env.inventoryByName("force-check-l3")
        def extension = new RecordingDeleteConfirmation(failCheck: true)
        List<L2DeleteConfirmExtensionPoint> extensions = bean(PluginRegistry.class)
                .getExtensionList(L2DeleteConfirmExtensionPoint.class)
        extensions.add(extension)

        try {
            expectError {
                deleteL2Network {
                    uuid = l2.uuid
                    deleteMode = "Enforcing"
                }
            }
        } finally {
            extensions.remove(extension)
        }

        assert extension.checkCount == 1
        assert dbf.isExist(l2.uuid, L2NetworkVO.class)
        assert dbf.isExist(l3.uuid, L3NetworkVO.class)
    }

    void testUnsupportedProviderKeepsExistingDeleteBehavior() {
        SdkL2NetworkInventory l2 = env.inventoryByName("plain-l2")
        def extension = new RecordingDeleteConfirmation(supported: false, failDelete: true)
        List<L2DeleteConfirmExtensionPoint> extensions = bean(PluginRegistry.class)
                .getExtensionList(L2DeleteConfirmExtensionPoint.class)
        extensions.add(extension)

        try {
            deleteL2Network {
                uuid = l2.uuid
            }
        } finally {
            extensions.remove(extension)
        }

        assert !dbf.isExist(l2.uuid, L2NetworkVO.class)
        assert extension.begun.isEmpty()
    }

    void testL2MetadataMutationCommitsRemoteBeforeLocal() {
        SdkL2NetworkInventory l2 = env.inventoryByName("metadata-update-l2")
        def extension = new RecordingMetadataMutation(dbf: dbf, l2Uuid: l2.uuid, failRemote: true)
        bean(PluginRegistry.class).defineDynamicExtension(
                NetworkConfigMutationExtensionPoint.class, extension)

        try {
            expectError {
                updateL2Network {
                    uuid = l2.uuid
                    name = "must-not-commit"
                    description = "must-not-commit"
                }
            }
            assert dbf.findByUuid(l2.uuid, L2NetworkVO.class).name == "metadata-update-l2"

            extension.failRemote = false
            updateL2Network {
                uuid = l2.uuid
                name = "metadata-updated"
                description = "metadata-description"
            }
        } finally {
            extension.l2Uuid = null
        }

        L2NetworkVO updated = dbf.findByUuid(l2.uuid, L2NetworkVO.class)
        assert extension.nameBeforeContinuation == "metadata-update-l2"
        assert extension.mutations*.kind == [
                NetworkConfigMutation.Kind.L2_METADATA,
                NetworkConfigMutation.Kind.L2_METADATA]
        assert updated.name == "metadata-updated"
        assert updated.description == "metadata-description"
    }

    @Override
    void clean() {
        env.delete()
    }

    private static class RecordingDeleteConfirmation implements L2DeleteConfirmExtensionPoint {
        boolean supported = true
        boolean failOnSecondCheck
        boolean failCheck
        boolean failDelete
        int checkCount
        List<String> events = []
        List<String> begun = []
        List<String> cancelled = []
        List<String> localMetadataDeletes = []
        List<String> operationUuids = []
        List<Boolean> forceDeleteFlags = []

        @Override
        boolean supports(L2NetworkInventory inventory) {
            return supported
        }

        @Override
        ErrorCode begin(L2NetworkInventory inventory) {
            begun.add(inventory.uuid)
            events.add("begin:${inventory.uuid}")
            return null
        }

        @Override
        ErrorCode begin(L2NetworkInventory inventory, NetworkDeletionContext context) {
            operationUuids.add(context.operationUuid)
            forceDeleteFlags.add(context.forceDelete)
            return begin(inventory)
        }

        @Override
        ErrorCode check(L2NetworkInventory inventory) {
            checkCount++
            return failCheck || failOnSecondCheck && checkCount == 2 ?
                    new ErrorCode("TEST", "simulated confirmation failure") : null
        }

        @Override
        ErrorCode delete(L2NetworkInventory inventory) {
            return failDelete ? new ErrorCode("TEST", "simulated provider delete failure") : null
        }

        @Override
        ErrorCode cancel(L2NetworkInventory inventory) {
            cancelled.add(inventory.uuid)
            return null
        }

        @Override
        void deleteLocalMetadata(L2NetworkInventory inventory) {
            localMetadataDeletes.add(inventory.uuid)
        }
    }

    private static class RecordingL3DeleteCheck implements L3NetworkDeleteExtensionPoint {
        List<String> events
        boolean failCheck

        @Override
        String preDeleteL3Network(L3NetworkInventory inventory) throws L3NetworkException {
            events.add("child-check:${inventory.uuid}")
            if (failCheck) {
                throw new L3NetworkException("simulated child check failure")
            }
            return null
        }

        @Override
        void beforeDeleteL3Network(L3NetworkInventory inventory) {
        }

        @Override
        void afterDeleteL3Network(L3NetworkInventory inventory) {
        }
    }

    private static class RecordingMetadataMutation implements NetworkConfigMutationExtensionPoint {
        DatabaseFacade dbf
        String l2Uuid
        boolean failRemote
        String nameBeforeContinuation
        List<NetworkConfigMutation> mutations = []

        @Override
        boolean supports(NetworkConfigMutation mutation) {
            return mutation.kind == NetworkConfigMutation.Kind.L2_METADATA && mutation.l2Uuid == l2Uuid
        }

        @Override
        void mutate(NetworkConfigMutation mutation, NetworkConfigLocalContinuation continuation,
                    Completion completion) {
            mutations.add(mutation)
            nameBeforeContinuation = dbf.findByUuid(l2Uuid, L2NetworkVO.class).name
            if (failRemote) {
                completion.fail(new ErrorCode("TEST.REMOTE.FAILURE", "simulated remote failure"))
                return
            }
            continuation.run(completion)
        }
    }
}

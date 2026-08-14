package org.zstack.test.integration.network.l2network

import org.zstack.core.componentloader.PluginRegistry
import org.zstack.core.db.DatabaseFacade
import org.zstack.header.errorcode.ErrorCode
import org.zstack.header.network.l2.L2DeleteConfirmExtensionPoint
import org.zstack.header.network.l2.L2NetworkInventory
import org.zstack.header.network.l2.L2NetworkVO
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
                }
            }
        }
    }

    @Override
    void test() {
        env.create {
            dbf = bean(DatabaseFacade.class)
            testDeleteZoneCancelsEveryConfirmedDeleteOnCheckFailure()
            testDeleteL2NetworkRemovesConfirmedMetadataOnce()
        }
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
        SdkL2NetworkInventory l2 = env.inventoryByName("cleanup-l2")
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
        assert extension.localMetadataDeletes == [l2.uuid]
    }

    @Override
    void clean() {
        env.delete()
    }

    private static class RecordingDeleteConfirmation implements L2DeleteConfirmExtensionPoint {
        boolean failOnSecondCheck
        int checkCount
        List<String> begun = []
        List<String> cancelled = []
        List<String> localMetadataDeletes = []

        @Override
        boolean supports(L2NetworkInventory inventory) {
            return true
        }

        @Override
        ErrorCode begin(L2NetworkInventory inventory) {
            begun.add(inventory.uuid)
            return null
        }

        @Override
        ErrorCode check(L2NetworkInventory inventory) {
            checkCount++
            return failOnSecondCheck && checkCount == 2 ?
                    new ErrorCode("TEST", "simulated confirmation failure") : null
        }

        @Override
        ErrorCode delete(L2NetworkInventory inventory) {
            return null
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
}

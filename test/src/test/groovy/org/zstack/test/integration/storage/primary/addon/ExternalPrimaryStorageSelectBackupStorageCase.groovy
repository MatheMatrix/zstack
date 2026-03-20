package org.zstack.test.integration.storage.primary.addon

import org.zstack.core.Platform
import org.zstack.core.cloudbus.CloudBus
import org.zstack.core.db.DatabaseFacade
import org.zstack.core.db.SQL
import org.zstack.header.message.MessageReply
import org.zstack.header.storage.backup.BackupStorageEO
import org.zstack.header.storage.backup.BackupStorageState
import org.zstack.header.storage.backup.BackupStorageStatus
import org.zstack.header.storage.backup.BackupStorageZoneRefVO
import org.zstack.header.storage.backup.BackupStorageZoneRefVO_
import org.zstack.header.storage.primary.PrimaryStorageConstant
import org.zstack.header.storage.primary.SelectBackupStorageMsg
import org.zstack.header.storage.primary.SelectBackupStorageReply
import org.zstack.sdk.PrimaryStorageInventory
import org.zstack.sdk.ZoneInventory
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

/**
 * ZSTAC-71706: ExternalPrimaryStorage backup storage selection in mixed environment.
 *
 * Bug: List.indexOf() returns -1 for types not in preferBsTypes,
 * causing ascending sort to place non-preferred types (e.g. VCenterBackupStorage)
 * before preferred types in the sorted result.
 *
 * Fix: Filter out non-preferred backup storage types before sorting by preference.
 *
 * This case sets up a ZBS ExternalPrimaryStorage (preferBsTypes = [ImageStoreBackupStorage])
 * with multiple backup storage types attached to the zone, then sends SelectBackupStorageMsg
 * via CloudBus to verify the handler selects the correct preferred backup storage.
 */
class ExternalPrimaryStorageSelectBackupStorageCase extends SubCase {
    EnvSpec env
    ZoneInventory zone
    PrimaryStorageInventory ps
    DatabaseFacade dbf
    CloudBus bus
    List<String> manualBsUuids = []

    @Override
    void clean() {
        manualBsUuids.each { uuid ->
            SQL.New(BackupStorageZoneRefVO.class)
                    .eq(BackupStorageZoneRefVO_.backupStorageUuid, uuid)
                    .hardDelete()
            dbf.removeByPrimaryKey(uuid, BackupStorageEO.class)
        }
        env.delete()
    }

    @Override
    void setup() {
        useSpring(StorageTest.springSpec)
    }

    @Override
    void environment() {
        env = makeEnv {
            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "root"
                password = "password"
                hostname = "127.0.0.2"

                image {
                    name = "image"
                    url = "http://zstack.org/download/test.qcow2"
                    size = SizeUnit.GIGABYTE.toByte(1)
                    virtio = true
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

                externalPrimaryStorage {
                    name = "zbs-ps"
                    identity = "zbs"
                    defaultOutputProtocol = "CBD"
                    config = '{"mdsUrls":["root:password@127.0.1.1","root:password@127.0.1.2","root:password@127.0.1.3"],"logicalPoolName":"lpool1"}'
                    url = "zbs"
                }

                attachBackupStorage("sftp")
            }
        }
    }

    @Override
    void test() {
        env.create {
            zone = env.inventoryByName("zone") as ZoneInventory
            ps = env.inventoryByName("zbs-ps") as PrimaryStorageInventory
            dbf = bean(DatabaseFacade.class)
            bus = bean(CloudBus.class)

            testErrorWhenNoPreferredTypeAvailable()
            testSelectPreferredOverNonPreferred()
            testPreferBsTypesNotCorruptedByRetainAll()
        }
    }

    /**
     * When only non-preferred backup storage types exist in the zone,
     * the selection should return an error (no matching preferred types).
     * Zone has SftpBackupStorage (from env) and VCenterBackupStorage,
     * neither of which is in zbs's preferBsTypes [ImageStoreBackupStorage].
     */
    void testErrorWhenNoPreferredTypeAvailable() {
        createAndAttachBackupStorage("vcenter-bs", "VCenterBackupStorage")

        SelectBackupStorageMsg msg = new SelectBackupStorageMsg()
        msg.setPrimaryStorageUuid(ps.uuid)
        msg.setRequiredSize(SizeUnit.MEGABYTE.toByte(1))
        bus.makeTargetServiceIdByResourceUuid(msg, PrimaryStorageConstant.SERVICE_ID, ps.uuid)
        MessageReply reply = bus.call(msg)

        assert !reply.isSuccess() : "Should fail when no preferred BS type is available"
    }

    /**
     * Reproduces ZSTAC-71706: zone has both ImageStoreBackupStorage (preferred)
     * and VCenterBackupStorage (non-preferred, created in previous test).
     * Before the fix, indexOf() returns -1 for VCenterBackupStorage causing
     * it to sort before ImageStoreBackupStorage. After the fix, non-preferred
     * types are filtered out entirely, and ImageStoreBackupStorage is correctly selected.
     */
    void testSelectPreferredOverNonPreferred() {
        createAndAttachBackupStorage("imagestore-bs", "ImageStoreBackupStorage")

        SelectBackupStorageMsg msg = new SelectBackupStorageMsg()
        msg.setPrimaryStorageUuid(ps.uuid)
        msg.setRequiredSize(SizeUnit.MEGABYTE.toByte(1))
        bus.makeTargetServiceIdByResourceUuid(msg, PrimaryStorageConstant.SERVICE_ID, ps.uuid)
        MessageReply reply = bus.call(msg)

        assert reply.isSuccess() : "SelectBackupStorageMsg should succeed"
        SelectBackupStorageReply bsReply = reply as SelectBackupStorageReply
        assert bsReply.inventory != null
        assert bsReply.inventory.type == "ImageStoreBackupStorage" :
                "Should select preferred ImageStoreBackupStorage, but got ${bsReply.inventory.type}"
    }

    /**
     * Reproduces ZSTAC-80789: getPreferBackupStorageTypes() must return a defensive copy.
     *
     * Bug: ZbsStorageFactory/ExponStorageFactory/XInfiniStorageFactory returned a direct
     * reference to their internal preferBackupStorageTypes list. When SelectBackupStorageMsg
     * carried requiredBackupStorageTypes, the handler called retainAll() on the returned list,
     * permanently mutating the bean's internal state. Subsequent requests without
     * requiredBackupStorageTypes would then see an empty preferBsTypes and fail.
     *
     * Fix: Return new ArrayList<>(preferBackupStorageTypes) from getPreferBackupStorageTypes().
     *
     * Test: send SelectBackupStorageMsg with requiredBackupStorageTypes=["CephBackupStorage"]
     * (no intersection with ZBS's prefer types), then send again without requiredBackupStorageTypes.
     * Before the fix, the second call fails because the bean's list was emptied by retainAll().
     */
    void testPreferBsTypesNotCorruptedByRetainAll() {
        // imagestore-bs was already created in testSelectPreferredOverNonPreferred
        // ZBS prefers [ImageStoreBackupStorage]; zone has ImageStoreBackupStorage attached

        // 1st call: with requiredBackupStorageTypes=["CephBackupStorage"]
        //    retainAll(["CephBackupStorage"]) on preferBsTypes => empty intersection => error expected
        //    Before fix: this mutates the bean, emptying preferBackupStorageTypes permanently
        SelectBackupStorageMsg msg1 = new SelectBackupStorageMsg()
        msg1.setPrimaryStorageUuid(ps.uuid)
        msg1.setRequiredSize(SizeUnit.MEGABYTE.toByte(1))
        msg1.setRequiredBackupStorageTypes(["CephBackupStorage"])
        bus.makeTargetServiceIdByResourceUuid(msg1, PrimaryStorageConstant.SERVICE_ID, ps.uuid)
        MessageReply reply1 = bus.call(msg1)

        assert !reply1.isSuccess() : "Should fail: no intersection between CephBackupStorage and ZBS prefer types"

        // 2nd call: without requiredBackupStorageTypes
        //    Before fix: preferBsTypes was permanently emptied by the 1st call's retainAll => fails
        //    After fix: defensive copy means bean is intact => succeeds and selects ImageStoreBackupStorage
        SelectBackupStorageMsg msg2 = new SelectBackupStorageMsg()
        msg2.setPrimaryStorageUuid(ps.uuid)
        msg2.setRequiredSize(SizeUnit.MEGABYTE.toByte(1))
        bus.makeTargetServiceIdByResourceUuid(msg2, PrimaryStorageConstant.SERVICE_ID, ps.uuid)
        MessageReply reply2 = bus.call(msg2)

        assert reply2.isSuccess() :
                "ZSTAC-80789: second SelectBackupStorageMsg should succeed but failed - " +
                "preferBackupStorageTypes was corrupted by previous retainAll()"
        SelectBackupStorageReply bsReply2 = reply2 as SelectBackupStorageReply
        assert bsReply2.inventory != null
        assert bsReply2.inventory.type == "ImageStoreBackupStorage" :
                "Should select ImageStoreBackupStorage, but got ${bsReply2.inventory.type}"
    }

    private void createAndAttachBackupStorage(String name, String type) {
        String uuid = Platform.getUuid()

        def bsEo = new BackupStorageEO()
        bsEo.setUuid(uuid)
        bsEo.setName(name)
        bsEo.setType(type)
        bsEo.setState(BackupStorageState.Enabled)
        bsEo.setStatus(BackupStorageStatus.Connected)
        bsEo.setTotalCapacity(SizeUnit.TERABYTE.toByte(100))
        bsEo.setAvailableCapacity(SizeUnit.TERABYTE.toByte(100))
        bsEo.setUrl("http://test-" + name)
        dbf.persist(bsEo)

        def ref = new BackupStorageZoneRefVO()
        ref.setBackupStorageUuid(uuid)
        ref.setZoneUuid(zone.uuid)
        dbf.persist(ref)

        manualBsUuids.add(uuid)
    }
}

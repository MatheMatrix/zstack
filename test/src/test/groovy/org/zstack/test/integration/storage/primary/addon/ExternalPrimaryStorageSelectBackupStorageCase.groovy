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
import org.zstack.sdk.ClusterInventory
import org.zstack.sdk.ImageInventory
import org.zstack.sdk.InstanceOfferingInventory
import org.zstack.sdk.L3NetworkInventory
import org.zstack.sdk.PrimaryStorageInventory
import org.zstack.sdk.VmInstanceInventory
import org.zstack.sdk.ZoneInventory
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

/**
 * ZSTAC-80789: getPreferBackupStorageTypes() must return a defensive copy.
 *
 * Bug: ZbsStorageFactory/ExponStorageFactory/XInfiniStorageFactory returned a direct
 * reference to their internal preferBackupStorageTypes list. When SelectBackupStorageMsg
 * carried requiredBackupStorageTypes, the handler called retainAll() on the returned list,
 * permanently mutating the bean's internal state. Subsequent requests without
 * requiredBackupStorageTypes would then see an empty preferBsTypes and fail.
 *
 * Fix: Return new ArrayList<>(preferBackupStorageTypes) from getPreferBackupStorageTypes().
 *
 * This case sets up a ZBS ExternalPrimaryStorage with both ImageStoreBackupStorage and
 * CephBackupStorage attached to the zone, creates a VM on the ZBS PS, then simulates
 * two SelectBackupStorageMsg calls (as would happen during clone VM in premium):
 * 1st with requiredBackupStorageTypes=["CephBackupStorage"] (triggers retainAll),
 * 2nd without requiredBackupStorageTypes (verifies bean is not corrupted).
 *
 * Note: SelectBackupStorageMsg is sent via CloudBus because the sender (clone VM flow)
 * is in the premium module and not available in open-source integration tests.
 */
class ExternalPrimaryStorageSelectBackupStorageCase extends SubCase {
    EnvSpec env
    ZoneInventory zone
    ClusterInventory cluster
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
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(8)
                cpu = 4
            }

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
            cluster = env.inventoryByName("cluster") as ClusterInventory
            ps = env.inventoryByName("zbs-ps") as PrimaryStorageInventory
            dbf = bean(DatabaseFacade.class)
            bus = bean(CloudBus.class)

            testPreferBsTypesNotCorruptedByRetainAll()
        }
    }

    /**
     * Reproduces ZSTAC-80789: retainAll corrupts bean's preferBackupStorageTypes.
     *
     * Scenario (simulates clone VM on ZBS with mixed BS types):
     * 1. Set up zone with ImageStoreBackupStorage (ZBS preferred) and CephBackupStorage
     * 2. Create a VM on the ZBS primary storage
     * 3. Send SelectBackupStorageMsg with requiredBackupStorageTypes=["CephBackupStorage"]
     *    - This triggers retainAll(["CephBackupStorage"]) on preferBsTypes
     *    - Before fix: mutates the bean's list, emptying it permanently
     *    - Expected: fails (no intersection), but bean should remain intact
     * 4. Send SelectBackupStorageMsg without requiredBackupStorageTypes
     *    - Before fix: fails because preferBsTypes was permanently emptied
     *    - After fix: succeeds, selects ImageStoreBackupStorage
     */
    void testPreferBsTypesNotCorruptedByRetainAll() {
        // Set up: attach ImageStoreBackupStorage and CephBackupStorage to the zone
        createAndAttachBackupStorage("imagestore-bs", "ImageStoreBackupStorage")
        createAndAttachBackupStorage("ceph-bs", "CephBackupStorage")

        // Attach PS to cluster and create a VM to establish realistic context
        attachPrimaryStorageToCluster {
            primaryStorageUuid = ps.uuid
            clusterUuid = cluster.uuid
        }

        def instanceOffering = env.inventoryByName("instanceOffering") as InstanceOfferingInventory
        def image = env.inventoryByName("image") as ImageInventory
        def l3 = env.inventoryByName("l3") as L3NetworkInventory

        VmInstanceInventory vm = createVmInstance {
            name = "test-vm"
            instanceOfferingUuid = instanceOffering.uuid
            imageUuid = image.uuid
            l3NetworkUuids = [l3.uuid]
        } as VmInstanceInventory

        assert vm != null : "VM should be created successfully on ZBS primary storage"

        // 1st call: with requiredBackupStorageTypes=["CephBackupStorage"]
        //   retainAll(["CephBackupStorage"]) on preferBsTypes [ImageStoreBackupStorage]
        //   => empty intersection => error expected
        //   Before fix: this permanently empties the bean's preferBackupStorageTypes
        SelectBackupStorageMsg msg1 = new SelectBackupStorageMsg()
        msg1.setPrimaryStorageUuid(ps.uuid)
        msg1.setRequiredSize(SizeUnit.MEGABYTE.toByte(1))
        msg1.setRequiredBackupStorageTypes(["CephBackupStorage"])
        bus.makeTargetServiceIdByResourceUuid(msg1, PrimaryStorageConstant.SERVICE_ID, ps.uuid)
        MessageReply reply1 = bus.call(msg1)

        assert !reply1.isSuccess() : "Should fail: no intersection between CephBackupStorage and ZBS prefer types [ImageStoreBackupStorage]"

        // 2nd call: without requiredBackupStorageTypes
        //   Before fix: preferBsTypes was permanently emptied by the 1st call's retainAll => fails
        //   After fix: defensive copy means bean is intact => succeeds and selects ImageStoreBackupStorage
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

package org.zstack.test.integration.storage.primary.addon.zbs

import org.springframework.http.HttpEntity
import org.zstack.core.cloudbus.CloudBus
import org.zstack.core.config.GlobalConfigValidatorExtensionPoint
import org.zstack.core.db.Q
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageHostProtocolRefVO
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageHostProtocolRefVO_
import org.zstack.header.storage.backup.UploadImageToRemoteTargetMsg
import org.zstack.header.storage.backup.UploadImageToRemoteTargetReply
import org.zstack.header.storage.primary.PrimaryStorageClusterRefVO
import org.zstack.header.storage.primary.PrimaryStorageClusterRefVO_
import org.zstack.header.storage.primary.PrimaryStorageHostStatus
import org.zstack.header.storage.snapshot.VolumeSnapshotVO
import org.zstack.header.storage.snapshot.VolumeSnapshotVO_
import org.zstack.header.volume.VolumeProtocol
import org.zstack.header.volume.VolumeVO
import org.zstack.header.volume.VolumeVO_
import org.zstack.sdk.CephPrimaryStorageInventory
import org.zstack.sdk.ClusterInventory
import org.zstack.sdk.DiskOfferingInventory
import org.zstack.sdk.KVMHostInventory
import org.zstack.sdk.L3NetworkInventory
import org.zstack.sdk.PrimaryStorageInventory
import org.zstack.sdk.VmInstanceInventory
import org.zstack.sdk.VolumeSnapshotInventory
import org.zstack.storage.ceph.primary.CephPrimaryStorageBase
import org.zstack.storage.volume.VolumeGlobalConfig
import org.zstack.storage.volume.VolumeManagerImpl
import org.zstack.storage.zbs.ZbsStorageController
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit
import org.zstack.utils.gson.JSONObjectUtil

import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicInteger

class ScheduledVolumeSizeSyncCase extends SubCase {
    EnvSpec env
    ClusterInventory cluster
    PrimaryStorageInventory ps
    CephPrimaryStorageInventory cephPs
    DiskOfferingInventory diskOffering
    KVMHostInventory kvm
    String originalAutoRefreshVolumeScope
    String originalRefreshVolumeSizeInterval

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

            diskOffering {
                name = "diskOffering"
                diskSize = SizeUnit.GIGABYTE.toByte(2)
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
                        name = "kvm-1"
                        managementIp = "127.0.0.1"
                        username = "root"
                        password = "password"
                    }

                    attachPrimaryStorage("ceph-pri")
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
                    name = "zbs-1"
                    identity = "zbs"
                    defaultOutputProtocol = "CBD"
                    config = "{\"mdsUrls\":[\"root:password@127.0.1.1\"],\"logicalPoolName\":\"lpool1\"}"
                    url = "zbs"
                }

                cephPrimaryStorage {
                    name = "ceph-pri"
                    description = "Test"
                    totalCapacity = SizeUnit.GIGABYTE.toByte(200)
                    availableCapacity = SizeUnit.GIGABYTE.toByte(200)
                    url = "ceph://pri"
                    fsid = "7ff218d9-f525-435f-8a40-3618d1772a64"
                    monUrls = ["root:password@localhost/?monPort=7777"]
                }

                attachBackupStorage("sftp")
                attachBackupStorage("ceph-bk")
            }

            cephBackupStorage {
                name = "ceph-bk"
                description = "Test"
                totalCapacity = SizeUnit.GIGABYTE.toByte(100)
                availableCapacity = SizeUnit.GIGABYTE.toByte(100)
                url = "/bk"
                fsid = "7ff218d9-f525-435f-8a40-3618d1772a64"
                monUrls = ["root:password@localhost/?monPort=7777"]

                image {
                    name = "ceph-image"
                    url = "http://zstack.org/download/image.qcow2"
                }
            }
        }
    }

    @Override
    void test() {
        env.create {
            cluster = env.inventoryByName("cluster") as ClusterInventory
            ps = env.inventoryByName("zbs-1") as PrimaryStorageInventory
            cephPs = env.inventoryByName("ceph-pri") as CephPrimaryStorageInventory
            diskOffering = env.inventoryByName("diskOffering") as DiskOfferingInventory
            kvm = env.inventoryByName("kvm-1") as KVMHostInventory

            saveScheduledVolumeSizeSyncConfigForTest()
            try {
                testScheduledSyncCachesSnapshotCapability()
                testScheduledCephVolumeSyncDoesNotSyncSnapshotSize()
            } finally {
                restoreScheduledVolumeSizeSyncConfigForTest()
            }
        }
    }

    @Override
    void clean() {
        restoreScheduledVolumeSizeSyncConfigForTest()
        detachZbsPrimaryStorageFromClusterIfAttached()
        env.delete()
    }

    void testScheduledSyncCachesSnapshotCapability() {
        long volumeActualSize = SizeUnit.MEGABYTE.toByte(11)
        long snapshotInitialSize = SizeUnit.MEGABYTE.toByte(1)
        long snapshotSyncedSize = SizeUnit.MEGABYTE.toByte(5)
        AtomicInteger batchQueryWithSnapshotCount = new AtomicInteger(0)

        prepareZbsHost()

        env.afterSimulator(ZbsStorageController.CREATE_VOLUME_PATH) { rsp, HttpEntity<String> e ->
            ZbsStorageController.CreateVolumeCmd cmd = JSONObjectUtil.toObject(e.body, ZbsStorageController.CreateVolumeCmd)
            ZbsStorageController.CreateVolumeRsp createVolumeRsp = new ZbsStorageController.CreateVolumeRsp()
            createVolumeRsp.installPath = "zbs://${cmd.logicalPool}/${cmd.volume}"
            createVolumeRsp.actualSize = 0L
            createVolumeRsp.size = cmd.size
            return createVolumeRsp
        }

        env.message(UploadImageToRemoteTargetMsg.class) { UploadImageToRemoteTargetMsg msg, CloudBus bus ->
            assert msg.remoteTargetUrl.startsWith("nbd://")
            assert msg.format == "raw"
            bus.reply(msg, new UploadImageToRemoteTargetReply())
        }

        VmInstanceInventory vm = createVmInstance {
            name = "vm-for-scheduled-volume-size-sync"
            instanceOfferingUuid = env.inventoryByName("instanceOffering").uuid
            imageUuid = env.inventoryByName("image").uuid
            l3NetworkUuids = [(env.inventoryByName("l3") as L3NetworkInventory).uuid]
            primaryStorageUuidForRootVolume = ps.uuid
            dataDiskOfferingUuids = [diskOffering.uuid]
            hostUuid = kvm.uuid
            systemTags = ["primaryStorageUuidForDataVolume::${ps.uuid}".toString()]
        } as VmInstanceInventory

        String dataVolumeUuid = vm.allVolumes.find { it.uuid != vm.rootVolumeUuid }.uuid
        String zbsPath = Q.New(VolumeVO.class)
                .select(VolumeVO_.installPath)
                .eq(VolumeVO_.uuid, dataVolumeUuid)
                .findValue()

        env.afterSimulator(ZbsStorageController.CREATE_SNAPSHOT_PATH) { rsp, HttpEntity<String> e ->
            ZbsStorageController.CreateSnapshotCmd cmd = JSONObjectUtil.toObject(e.body, ZbsStorageController.CreateSnapshotCmd)
            ZbsStorageController.CreateSnapshotRsp createSnapshotRsp = new ZbsStorageController.CreateSnapshotRsp()
            createSnapshotRsp.installPath = "${cmd.path}@${cmd.snapshot}"
            createSnapshotRsp.actualSize = snapshotInitialSize
            return createSnapshotRsp
        }

        VolumeSnapshotInventory snapshot = createVolumeSnapshot {
            name = "snap-for-scheduled-volume-size-sync"
            volumeUuid = dataVolumeUuid
        } as VolumeSnapshotInventory
        String snapshotUuid = snapshot.uuid

        String snapshotPath = Q.New(VolumeSnapshotVO.class)
                .select(VolumeSnapshotVO_.primaryStorageInstallPath)
                .eq(VolumeSnapshotVO_.uuid, snapshotUuid)
                .findValue()

        env.cleanAfterSimulatorHandlers()

        env.simulator(ZbsStorageController.BATCH_QUERY_VOLUME_WITH_SNAPSHOT_PATH) { HttpEntity<String> e, EnvSpec spec ->
            batchQueryWithSnapshotCount.incrementAndGet()
            def cmd = JSONObjectUtil.toObject(e.body, ZbsStorageController.BatchQueryVolumeCmd.class)
            String volumeName = zbsPath.substring(zbsPath.lastIndexOf("/") + 1)
            String snapshotName = snapshotPath.substring(snapshotPath.lastIndexOf("@") + 1)
            String volumeAgentPath = cmd.installPaths.find { it.contains(volumeName) && !it.contains("@") }
            String snapshotAgentPath = cmd.installPaths.find { it.contains(volumeName) && it.contains(snapshotName) }
            assert volumeAgentPath != null
            assert snapshotAgentPath != null

            def batchQueryVolumeRsp = new ZbsStorageController.BatchQueryVolumeRsp()
            batchQueryVolumeRsp.setVolumes([(volumeAgentPath): ["length": SizeUnit.GIGABYTE.toByte(8), "usedSize": volumeActualSize]])
            batchQueryVolumeRsp.setSnapshots([(snapshotAgentPath): ["usedSize": snapshotSyncedSize]])
            return batchQueryVolumeRsp
        }

        VolumeGlobalConfig.AUTO_REFRESH_VOLUME_SCOPE.updateValue("AllActive")
        updateRefreshVolumeSizeIntervalForTest("2")
        updateRefreshVolumeSizeIntervalForTest("1")

        retryInSecs {
            assert Q.New(VolumeSnapshotVO.class)
                    .select(VolumeSnapshotVO_.size)
                    .eq(VolumeSnapshotVO_.uuid, snapshotUuid)
                    .findValue() == snapshotSyncedSize
            assert Q.New(VolumeVO.class)
                    .select(VolumeVO_.actualSize)
                    .eq(VolumeVO_.uuid, dataVolumeUuid)
                    .findValue() == volumeActualSize + snapshotSyncedSize
            assert batchQueryWithSnapshotCount.get() >= 1
            assert snapshotSizeSyncRequiredCache()["zbs-Data"] == true
            assert snapshotSizeSyncRequiredCache()["zbs-Root"] == true
        }

        stopVmInstance {
            uuid = vm.uuid
        }

        retryInSecs {
            assert snapshotSizeSyncRequiredCache()["zbs-Data"] == true
            assert snapshotSizeSyncRequiredCache()["zbs-Root"] == true
        }

        env.cleanSimulatorAndMessageHandlers()
    }

    void testScheduledCephVolumeSyncDoesNotSyncSnapshotSize() {
        long snapshotSizeBeforeSync = SizeUnit.MEGABYTE.toByte(7)
        long volumeActualSize = SizeUnit.MEGABYTE.toByte(17)
        AtomicInteger batchGetVolumeSizeCount = new AtomicInteger(0)

        VmInstanceInventory vm = createVmInstance {
            name = "vm-for-scheduled-ceph-volume-size-sync"
            instanceOfferingUuid = env.inventoryByName("instanceOffering").uuid
            imageUuid = env.inventoryByName("ceph-image").uuid
            l3NetworkUuids = [(env.inventoryByName("l3") as L3NetworkInventory).uuid]
            primaryStorageUuidForRootVolume = cephPs.uuid
            dataDiskOfferingUuids = [diskOffering.uuid]
            hostUuid = kvm.uuid
            systemTags = ["primaryStorageUuidForDataVolume::${cephPs.uuid}".toString()]
        } as VmInstanceInventory

        String dataVolumeUuid = vm.allVolumes.find { it.uuid != vm.rootVolumeUuid }.uuid

        env.afterSimulator(CephPrimaryStorageBase.CREATE_SNAPSHOT_PATH) { CephPrimaryStorageBase.CreateSnapshotRsp rsp, HttpEntity<String> e ->
            CephPrimaryStorageBase.CreateSnapshotCmd cmd = JSONObjectUtil.toObject(e.body, CephPrimaryStorageBase.CreateSnapshotCmd.class)
            assert cmd.volumeUuid == dataVolumeUuid
            rsp.actualSize = snapshotSizeBeforeSync
            return rsp
        }

        VolumeSnapshotInventory snapshot = createVolumeSnapshot {
            name = "ceph-snap-for-scheduled-volume-size-sync"
            volumeUuid = dataVolumeUuid
        } as VolumeSnapshotInventory
        String snapshotUuid = snapshot.uuid

        env.cleanAfterSimulatorHandlers()

        env.afterSimulator(CephPrimaryStorageBase.BATCH_GET_VOLUME_SIZE_PATH) { CephPrimaryStorageBase.GetBatchVolumeSizeRsp rsp, HttpEntity<String> e ->
            int count = batchGetVolumeSizeCount.incrementAndGet()
            CephPrimaryStorageBase.GetBatchVolumeSizeCmd cmd = JSONObjectUtil.toObject(e.body, CephPrimaryStorageBase.GetBatchVolumeSizeCmd.class)
            assert cmd.volumeUuidInstallPaths.containsKey(dataVolumeUuid)
            rsp.actualSizes = new HashMap<>()
            if (count == 1) {
                rsp.actualSizes.put(dataVolumeUuid, volumeActualSize)
            }
            return rsp
        }

        env.resetAllSimulatorSize()
        VolumeGlobalConfig.AUTO_REFRESH_VOLUME_SCOPE.updateValue("AllActive")
        updateRefreshVolumeSizeIntervalForTest("2")
        updateRefreshVolumeSizeIntervalForTest("1")

        retryInSecs {
            assert Q.New(VolumeSnapshotVO.class)
                    .select(VolumeSnapshotVO_.size)
                    .eq(VolumeSnapshotVO_.uuid, snapshotUuid)
                    .findValue() == snapshotSizeBeforeSync
            assert Q.New(VolumeVO.class)
                    .select(VolumeVO_.actualSize)
                    .eq(VolumeVO_.uuid, dataVolumeUuid)
                    .findValue() == volumeActualSize + snapshotSizeBeforeSync
            assert batchGetVolumeSizeCount.get() >= 1
            assert snapshotSizeSyncRequiredCache()["Ceph-Data"] == false
            assert snapshotSizeSyncRequiredCache()["Ceph-Root"] == false
        }

        stopVmInstance {
            uuid = vm.uuid
        }

        retryInSecs {
            assert snapshotSizeSyncRequiredCache()["zbs-Data"] == true
            assert snapshotSizeSyncRequiredCache()["zbs-Root"] == true
            assert snapshotSizeSyncRequiredCache()["Ceph-Data"] == false
            assert snapshotSizeSyncRequiredCache()["Ceph-Root"] == false
        }

        env.resetAllSimulatorSize()
        env.cleanSimulatorAndMessageHandlers()
    }

    private void prepareZbsHost() {
        if (!Q.New(PrimaryStorageClusterRefVO.class)
                .eq(PrimaryStorageClusterRefVO_.primaryStorageUuid, ps.uuid)
                .eq(PrimaryStorageClusterRefVO_.clusterUuid, cluster.uuid)
                .isExists()) {
            attachPrimaryStorageToCluster {
                primaryStorageUuid = ps.uuid
                clusterUuid = cluster.uuid
            }
        }

        env.afterSimulator(ZbsStorageController.CHECK_HOST_STORAGE_CONNECTION_PATH) { rsp, HttpEntity<String> e ->
            ZbsStorageController.CheckHostStorageConnectionRsp checkHostStorageConnectionRsp = new ZbsStorageController.CheckHostStorageConnectionRsp()
            checkHostStorageConnectionRsp.success = true
            return checkHostStorageConnectionRsp
        }

        reconnectHost {
            uuid = kvm.uuid
        }

        retryInSecs {
            assert Q.New(ExternalPrimaryStorageHostProtocolRefVO.class)
                    .eq(ExternalPrimaryStorageHostProtocolRefVO_.primaryStorageUuid, ps.uuid)
                    .eq(ExternalPrimaryStorageHostProtocolRefVO_.hostUuid, kvm.uuid)
                    .eq(ExternalPrimaryStorageHostProtocolRefVO_.protocol, VolumeProtocol.CBD.toString())
                    .eq(ExternalPrimaryStorageHostProtocolRefVO_.status, PrimaryStorageHostStatus.Connected)
                    .isExists()
        }

        env.cleanAfterSimulatorHandlers()
    }

    private void updateRefreshVolumeSizeIntervalForTest(String interval) {
        List<GlobalConfigValidatorExtensionPoint> validators = VolumeGlobalConfig.REFRESH_VOLUME_SIZE_INTERVAL.validators
        VolumeGlobalConfig.REFRESH_VOLUME_SIZE_INTERVAL.validators = new ArrayList<>()
        try {
            VolumeGlobalConfig.REFRESH_VOLUME_SIZE_INTERVAL.updateValue(interval)
        } finally {
            VolumeGlobalConfig.REFRESH_VOLUME_SIZE_INTERVAL.validators = validators
        }
    }

    private void saveScheduledVolumeSizeSyncConfigForTest() {
        originalAutoRefreshVolumeScope = VolumeGlobalConfig.AUTO_REFRESH_VOLUME_SCOPE.value(String.class)
        originalRefreshVolumeSizeInterval = VolumeGlobalConfig.REFRESH_VOLUME_SIZE_INTERVAL.value(String.class)
    }

    private void restoreScheduledVolumeSizeSyncConfigForTest() {
        if (originalAutoRefreshVolumeScope == null || originalRefreshVolumeSizeInterval == null) {
            return
        }

        VolumeGlobalConfig.AUTO_REFRESH_VOLUME_SCOPE.updateValue(originalAutoRefreshVolumeScope)
        updateRefreshVolumeSizeIntervalForTest(originalRefreshVolumeSizeInterval)
    }

    private void detachZbsPrimaryStorageFromClusterIfAttached() {
        if (ps == null || cluster == null) {
            return
        }

        if (Q.New(PrimaryStorageClusterRefVO.class)
                .eq(PrimaryStorageClusterRefVO_.primaryStorageUuid, ps.uuid)
                .eq(PrimaryStorageClusterRefVO_.clusterUuid, cluster.uuid)
                .isExists()) {
            detachPrimaryStorageFromCluster {
                primaryStorageUuid = ps.uuid
                clusterUuid = cluster.uuid
            }
        }
    }

    private Map<String, Boolean> snapshotSizeSyncRequiredCache() {
        VolumeManagerImpl volumeManager = bean(VolumeManagerImpl.class)
        Field field = VolumeManagerImpl.class.getDeclaredField("snapshotSizeSyncRequiredCache")
        field.setAccessible(true)
        return field.get(volumeManager) as Map<String, Boolean>
    }
}

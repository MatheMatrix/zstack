package org.zstack.test.integration.storage.primary.addon

import org.springframework.http.HttpEntity
import org.zstack.core.Platform
import org.zstack.core.cloudbus.CloudBus
import org.zstack.core.db.Q
import org.zstack.expon.ExponStorageController
import org.zstack.header.storage.backup.DownloadImageFromRemoteTargetMsg
import org.zstack.header.storage.backup.DownloadImageFromRemoteTargetReply
import org.zstack.header.storage.backup.ExportImageToRemoteTargetMsg
import org.zstack.header.storage.backup.ExportImageToRemoteTargetReply
import org.zstack.header.vm.VmBootDevice
import org.zstack.header.vm.devices.DeviceAddress
import org.zstack.header.vm.devices.VirtualDeviceInfo
import org.zstack.header.volume.VolumeVO
import org.zstack.header.volume.VolumeVO_
import org.zstack.kvm.KVMAgentCommands
import org.zstack.kvm.KVMConstant
import org.zstack.kvm.VolumeTO
import org.zstack.sdk.*
import org.zstack.storage.addon.primary.ExternalPrimaryStorageFactory
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit
import org.zstack.utils.gson.JSONObjectUtil

import static java.util.Arrays.asList

class ExternalPrimaryStorageCase extends SubCase {
    EnvSpec env
    ClusterInventory cluster
    InstanceOfferingInventory instanceOffering
    DiskOfferingInventory diskOffering
    ImageInventory image, iso
    L3NetworkInventory l3
    PrimaryStorageInventory ps
    BackupStorageInventory bs
    VmInstanceInventory vm
    VolumeInventory vol, vol2

    @Override
    void clean() {
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

            diskOffering {
                name = "diskOffering"
                diskSize = SizeUnit.GIGABYTE.toByte(20)
            }

            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "root"
                password = "password"
                hostname = "127.0.0.1"

                image {
                    name = "image"
                    url = "http://zstack.org/download/test.qcow2"
                    size = SizeUnit.GIGABYTE.toByte(1)
                }

                image {
                    name = "iso"
                    url = "http://zstack.org/download/test.iso"
                    size = SizeUnit.GIGABYTE.toByte(1)
                    format = "iso"
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
                        managementIp = "localhost"
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

                attachBackupStorage("sftp")
            }
        }
    }

    @Override
    void test() {
        env.create {
            cluster = env.inventoryByName("cluster") as ClusterInventory
            instanceOffering = env.inventoryByName("instanceOffering") as InstanceOfferingInventory
            diskOffering = env.inventoryByName("diskOffering") as DiskOfferingInventory
            image = env.inventoryByName("image") as ImageInventory
            iso = env.inventoryByName("iso") as ImageInventory
            l3 = env.inventoryByName("l3") as L3NetworkInventory
            bs = env.inventoryByName("sftp") as BackupStorageInventory
            simulatorEnv()
            testCreateExponStorage()
            testSessionExpired()
            testCreateVm()
            testAttachIso()
            testCreateDataVolume()
            testCreateSnapshot()
            testCreateTemplate()
            testClean()
        }
    }

    void simulatorEnv() {
        //TODO mock all
        env.afterSimulator(KVMConstant.KVM_ATTACH_VOLUME) { KVMAgentCommands.AttachDataVolumeResponse rsp, HttpEntity<String> e ->
            KVMAgentCommands.AttachDataVolumeCmd cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.AttachDataVolumeCmd.class)

            VirtualDeviceInfo info = new VirtualDeviceInfo()
            info.resourceUuid = cmd.volume.resourceUuid
            info.deviceAddress = new DeviceAddress()
            info.deviceAddress.domain = "0000"
            info.deviceAddress.bus = "00"
            info.deviceAddress.slot = Long.toHexString(Q.New(VolumeVO.class).eq(VolumeVO_.vmInstanceUuid, cmd.vmUuid).count())
            info.deviceAddress.function = "0"

            rsp.virtualDeviceInfoList = []
            rsp.virtualDeviceInfoList.addAll(info)
            return rsp
        }
    }

    void testCreateExponStorage() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        discoverExternalPrimaryStorage {
            url = "https://operator:Admin%40123@172.25.106.110:443/pool"
            identity = "expon"
        }

        ps = addExternalPrimaryStorage {
            name = "test"
            zoneUuid = zone.uuid
            url = "https://operator:Admin%40123@172.25.106.110:443/pool"
            identity = "expon"
            config = ""
            defaultOutputProtocol = "VHost"
        } as ExternalPrimaryStorageInventory

        updateExternalPrimaryStorage {
            uuid = ps.uuid
            config = '''{"pools":[{"name":"pool", "aliasName":"test"}]}'''
        }

        ps = queryPrimaryStorage {}[0] as ExternalPrimaryStorageInventory
        assert ps.getAddonInfo() != null

        attachPrimaryStorageToCluster {
            primaryStorageUuid = ps.uuid
            clusterUuid = cluster.uuid
        }
    }

    void testSessionExpired() {
        ExponStorageController svc = Platform.getComponentLoader().getComponent(ExternalPrimaryStorageFactory.class)
                .getControllerSvc(ps.uuid) as ExponStorageController
        svc.apiHelper.sessionId = "invalid"
    }

    void testCreateVm() {
        def result = getCandidatePrimaryStoragesForCreatingVm {
            l3NetworkUuids = [l3.uuid]
            imageUuid = image.uuid
        } as GetCandidatePrimaryStoragesForCreatingVmResult

        assert result.getRootVolumePrimaryStorages().size() == 1

        env.message(ExportImageToRemoteTargetMsg.class){ ExportImageToRemoteTargetMsg msg, CloudBus bus ->
            ExportImageToRemoteTargetReply r = new  ExportImageToRemoteTargetReply()
            assert msg.getRemoteTargetUrl().startsWith("iscsi://")
            bus.reply(msg, r)
        }

        env.afterSimulator(KVMConstant.KVM_START_VM_PATH) { rsp, HttpEntity<String> e ->
            def cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.StartVmCmd.class)
            assert cmd.rootVolume.deviceType == VolumeTO.VHOST
            assert cmd.rootVolume.installPath.startsWith("/var/run")
            assert cmd.rootVolume.format == "raw"
            return rsp
        }

        vm = createVmInstance {
            name = "vm"
            instanceOfferingUuid = instanceOffering.uuid
            imageUuid = image.uuid
            l3NetworkUuids = [l3.uuid]
        } as VmInstanceInventory

        stopVmInstance {
            uuid = vm.uuid
        }

        startVmInstance {
            uuid = vm.uuid
        }

        rebootVmInstance {
            uuid = vm.uuid
        }
    }

    void testAttachIso() {
        attachIsoToVmInstance {
            vmInstanceUuid = vm.uuid
            isoUuid = iso.uuid
        }

        rebootVmInstance {
            uuid = vm.uuid
        }

        setVmBootOrder {
            uuid = vm.uuid
            bootOrder = asList(VmBootDevice.CdRom.toString(), VmBootDevice.HardDisk.toString(), VmBootDevice.Network.toString())
        }

        rebootVmInstance {
            uuid = vm.uuid
        }

        setVmBootOrder {
            uuid = vm.uuid
            bootOrder = asList(VmBootDevice.HardDisk.toString(), VmBootDevice.CdRom.toString(), VmBootDevice.Network.toString())
        }

        rebootVmInstance {
            uuid = vm.uuid
        }

        detachIsoFromVmInstance {
            vmInstanceUuid = vm.uuid
        }
    }

    void testCreateDataVolume() {
        vol = createDataVolume {
            name = "test"
            diskOfferingUuid = diskOffering.uuid
            primaryStorageUuid = ps.uuid
        } as VolumeInventory

        attachDataVolumeToVm {
            vmInstanceUuid = vm.uuid
            volumeUuid = vol.uuid
        }

        vol2 = createDataVolume {
            name = "test"
            diskOfferingUuid = diskOffering.uuid
        } as VolumeInventory

        attachDataVolumeToVm {
            vmInstanceUuid = vm.uuid
            volumeUuid = vol2.uuid
        }

        detachDataVolumeFromVm {
            uuid = vol2.uuid
        }

        attachDataVolumeToVm {
            vmInstanceUuid = vm.uuid
            volumeUuid = vol2.uuid
        }
    }

    void testCreateSnapshot() {
        def snapshot = createVolumeSnapshot {
            name = "test"
            volumeUuid = vol.uuid
        } as VolumeSnapshotInventory

        stopVmInstance {
            uuid = vm.uuid
        }

        revertVolumeFromSnapshot {
            uuid = snapshot.uuid
        }

        deleteVolumeSnapshot {
            uuid = snapshot.uuid
        }

        startVmInstance {
            uuid = vm.uuid
        }

        /*
        def group = createVolumeSnapshotGroup {
            name = "test-snap"
            rootVolumeUuid = vm.rootVolumeUuid
        } as VolumeSnapshotGroupInventory

        stopVmInstance {
            uuid = vm.uuid
        }

        revertVmFromSnapshotGroup {
            uuid = group.uuid
        }

        deleteVolumeSnapshotGroup {
            uuid = group.uuid
        }

         */
    }

    void testCreateTemplate() {
        env.message(DownloadImageFromRemoteTargetMsg.class){ DownloadImageFromRemoteTargetMsg msg, CloudBus bus ->
            DownloadImageFromRemoteTargetReply r = new  DownloadImageFromRemoteTargetReply()
            assert msg.getRemoteTargetUrl().startsWith("iscsi://")
            r.setInstallPath("zstore://test/image")
            r.setSize(100L)
            bus.reply(msg, r)
        }

        def dataImage = createDataVolumeTemplateFromVolume  {
            name = "vol-image"
            volumeUuid = vol.uuid
            backupStorageUuids = [bs.uuid]
        } as ImageInventory

        stopVmInstance {
            uuid = vm.uuid
        }

        def rootImage = createRootVolumeTemplateFromRootVolume {
            name = "root-image"
            rootVolumeUuid = vm.rootVolumeUuid
            backupStorageUuids = [bs.uuid]
        } as ImageInventory


    }

    void testClean() {
        destroyVmInstance {
            uuid = vm.uuid
        }

        expungeVmInstance {
            uuid = vm.uuid
        }

        deleteDataVolume {
            uuid = vol.uuid
        }

        expungeDataVolume {
            uuid = vol.uuid
        }
    }
}

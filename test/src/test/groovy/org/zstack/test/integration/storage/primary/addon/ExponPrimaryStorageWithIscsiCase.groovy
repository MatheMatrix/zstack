package org.zstack.test.integration.storage.primary.addon

import org.springframework.http.HttpEntity
import org.zstack.core.Platform
import org.zstack.core.cloudbus.CloudBus
import org.zstack.core.db.DatabaseFacade
import org.zstack.core.db.Q
import org.zstack.core.db.SQL
import org.zstack.expon.ExponApiHelper
import org.zstack.expon.ExponNameHelper
import org.zstack.expon.ExponStorageController
import org.zstack.expon.sdk.iscsi.IscsiClientGroupModule
import org.zstack.expon.sdk.iscsi.IscsiModule
import org.zstack.expon.sdk.iscsi.IscsiSeverNode
import org.zstack.expon.sdk.iscsi.IscsiUssResource
import org.zstack.expon.sdk.vhost.VhostControllerModule
import org.zstack.expon.sdk.volume.VolumeModule
import org.zstack.header.core.ReturnValueCompletion
import org.zstack.header.host.HostConstant
import org.zstack.header.host.PingHostMsg
import org.zstack.header.message.MessageReply
import org.zstack.header.storage.addon.IscsiRemoteTarget
import org.zstack.header.storage.addon.primary.CreateVolumeSpec
import org.zstack.header.storage.backup.DownloadImageFromRemoteTargetMsg
import org.zstack.header.storage.backup.DownloadImageFromRemoteTargetReply
import org.zstack.header.storage.backup.ExportImageToRemoteTargetReply
import org.zstack.header.storage.backup.UploadImageToRemoteTargetMsg
import org.zstack.header.storage.primary.ImageCacheShadowVO
import org.zstack.header.storage.primary.ImageCacheShadowVO_
import org.zstack.header.storage.primary.ImageCacheVO
import org.zstack.header.storage.primary.ImageCacheVO_
import org.zstack.header.vm.VmBootDevice
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.header.vm.VmInstanceVO_
import org.zstack.header.vm.devices.DeviceAddress
import org.zstack.header.vm.devices.VirtualDeviceInfo
import org.zstack.header.volume.VolumeType
import org.zstack.header.volume.VolumeVO
import org.zstack.header.volume.VolumeVO_
import org.zstack.iscsi.kvm.KvmIscsiCommands
import org.zstack.kvm.KVMAgentCommands
import org.zstack.kvm.KVMConstant
import org.zstack.kvm.KVMGlobalConfig
import org.zstack.kvm.VolumeTO
import org.zstack.sdk.*
import org.zstack.storage.addon.primary.ExternalPrimaryStorageFactory
import org.zstack.storage.backup.BackupStorageSystemTags
import org.zstack.tag.SystemTagCreator
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.CollectionUtils
import org.zstack.utils.data.SizeUnit
import org.zstack.utils.gson.JSONObjectUtil

import java.util.stream.Collectors

import static java.util.Arrays.asList
import static org.zstack.expon.ExponIscsiHelper.buildVolumeIscsiTargetName
import static org.zstack.expon.ExponIscsiHelper.iscsiExportTargetName
import static org.zstack.expon.ExponIscsiHelper.iscsiTargetPrefix
import static org.zstack.expon.ExponNameHelper.getVolIdFromPath

class ExponPrimaryStorageWithIscsiCase extends SubCase {
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
    HostInventory host1, host2
    CloudBus bus
    ExponStorageController controller
    ExponApiHelper apiHelper
    ExponNameHelper nameHelper
    DatabaseFacade dbf

    String exponUrl = "https://admin:Admin123@172.25.102.64:443/pool"
    String exportProtocol = "iscsi://"

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

                image {
                    name = "iso"
                    url = "http://zstack.org/download/test.iso"
                    size = SizeUnit.GIGABYTE.toByte(1)
                    format = "iso"
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
                        managementIp = "localhost"
                        username = "root"
                        password = "password"
                    }
/*
                    kvm {
                        name = "kvm2"
                        managementIp = "127.0.0.3"
                        username = "root"
                        password = "password"
                    }
 */

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
            dbf = bean(DatabaseFacade.class)
            cluster = env.inventoryByName("cluster") as ClusterInventory
            instanceOffering = env.inventoryByName("instanceOffering") as InstanceOfferingInventory
            diskOffering = env.inventoryByName("diskOffering") as DiskOfferingInventory
            image = env.inventoryByName("image") as ImageInventory
            iso = env.inventoryByName("iso") as ImageInventory
            l3 = env.inventoryByName("l3") as L3NetworkInventory
            bs = env.inventoryByName("sftp") as BackupStorageInventory
            host1 = env.inventoryByName("kvm") as HostInventory
            // host2 = env.inventoryByName("kvm2") as HostInventory
            bus = bean(CloudBus.class)

//            KVMGlobalConfig.VM_SYNC_ON_HOST_PING.updateValue(true)
//            simulatorEnv()
            testCreateExponStorage()
//            testSessionExpired()
//            testCreateVm()
//            testExpungeActiveVolume()
            testExponIscsiAttach()
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

        SystemTagCreator creator = BackupStorageSystemTags.ISCSI_INITIATOR_NAME.newSystemTagCreator(bs.uuid);
        creator.setTagByTokens(Collections.singletonMap(BackupStorageSystemTags.ISCSI_INITIATOR_NAME_TOKEN, "iqn.1994-05.com.redhat:fc16b4d4fb3f"));
        creator.inherent = false;
        creator.recreate = true;
        creator.create();
    }

    void testCreateExponStorage() {
        def zone = env.inventoryByName("zone") as ZoneInventory

        discoverExternalPrimaryStorage {
            url = exponUrl
            identity = "expon"
        }

        ps = addExternalPrimaryStorage {
            name = "test"
            zoneUuid = zone.uuid
            url = exponUrl
            identity = "expon"
            config = ""
            defaultOutputProtocol = "iSCSI"
        } as ExternalPrimaryStorageInventory

        assert !ps.url.contains("Admin123")

        updateExternalPrimaryStorage {
            uuid = ps.uuid
            config = '''{"pools":[{"name":"pool", "aliasName":"test"}]}'''
        }

        ps = queryPrimaryStorage {}[0] as ExternalPrimaryStorageInventory
        assert ps.getAddonInfo() != null

        def psRet = zqlQuery("query primarystorage")[0]
        assert !psRet.url.contains("Admin123")

        attachPrimaryStorageToCluster {
            primaryStorageUuid = ps.uuid
            clusterUuid = cluster.uuid
        }

        ExternalPrimaryStorageFactory factory = Platform.getComponentLoader().getComponent(ExternalPrimaryStorageFactory.class)
        controller = factory.getControllerSvc(ps.uuid) as ExponStorageController
        apiHelper = controller.apiHelper
    }

    void testSessionExpired() {
        ExponStorageController svc = Platform.getComponentLoader().getComponent(ExternalPrimaryStorageFactory.class)
                .getControllerSvc(ps.uuid) as ExponStorageController
        svc.apiHelper.sessionId = "invalid"
    }

    void testCreateVm() {
        env.message(UploadImageToRemoteTargetMsg.class){ UploadImageToRemoteTargetMsg msg, CloudBus bus ->
            ExportImageToRemoteTargetReply r = new  ExportImageToRemoteTargetReply()
            bus.reply(msg, r)
        }

        env.afterSimulator(KVMConstant.KVM_START_VM_PATH) { rsp, HttpEntity<String> e ->
            def cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.StartVmCmd.class)
            assert cmd.rootVolume.deviceType == VolumeTO.iSCSI
            return rsp
        }

        vm = createVmInstance {
            name = "vm"
            instanceOfferingUuid = instanceOffering.uuid
            rootDiskOfferingUuid = diskOffering.uuid
            imageUuid = iso.uuid
            l3NetworkUuids = [l3.uuid]
            hostUuid = host1.uuid
        } as VmInstanceInventory
    }

    void testExpungeActiveVolume() {
        //创建iscsi 块存储卷
        def vol = createDataVolume {
            name = "test"
            diskSize = SizeUnit.GIGABYTE.toByte(2)
        } as VolumeInventory

        CreateVolumeSpec spec = new CreateVolumeSpec()
        spec.setUuid(vol.getUuid())
        spec.setSize(vol.getSize())
        spec.setAllocatedUrl(nameHelper.buildExponPath("pool", ""))
        ps = queryPrimaryStorage {}[0] as ExternalPrimaryStorageInventory
        String poolId = ps.getAddonInfo().get("pools").get
        VolumeModule exponVol = apiHelper.createVolume(spec.getName(), poolId, spec.getSize())
        assert exponVol != null

        attachDataVolumeToVm {
            vmInstanceUuid = vm.uuid
            volumeUuid = vol.uuid
        }

        // skip deactivate volume
        SQL.New(VolumeVO.class).eq(VolumeVO_.uuid, vol.uuid).set(VolumeVO_.vmInstanceUuid, null).update()
    }

    void testExponIscsiAttach() {
        // create iscsi volume
        def poolId = controller.addonInfo.getPools()[0].id
        def exponVol = controller.apiHelper.createVolume("test_iscsi_vol_" + Platform.uuid, poolId, SizeUnit.GIGABYTE.toByte(17))

        String tianshuId = controller.addonInfo.getClusters().get(0).getId()
        List<IscsiSeverNode> nodes = apiHelper.getIscsiTargetServer(tianshuId)
        nodes.removeIf({ it -> !it.getUssName().startsWith("iscsi_zstack") })

        // create iscsi target (iscsi target is :iscsi_zstack_active_7)
        IscsiModule iscsi = apiHelper.queryIscsiController(buildVolumeIscsiTargetName(7))
        if (iscsi == null) {
            iscsi = apiHelper.createIscsiController(buildVolumeIscsiTargetName(7),
                    tianshuId, 3260, IscsiUssResource.valueOf(nodes))
        }

        // create iscsi client group
        String iscsiClientName = "iscsi_client_test"
        // test use host ip
        String clientIqn = "172.25.15.145"
        IscsiClientGroupModule client = apiHelper.queryIscsiClient(iscsiClientName)
        if (client == null) {
            client = apiHelper.createIscsiClient(iscsiClientName, tianshuId, Collections.singletonList(clientIqn))
        } else if (!client.getHosts().contains(clientIqn)) {
            apiHelper.addHostToIscsiClient(clientIqn, client.getId())
        }

        // client attach target (one iscsi client group can only attach one iscsi target)
        if (client.getiscsiGwCount() == 0) {
            apiHelper.addIscsiClientToIscsiTarget(client.getId(), iscsi.getId())
        }

        apiHelper.addVolumeToIscsiClientGroup(exponVol.id, client.id, iscsi.id, false)

        /**
         * iscsi login
         * 需要一个url,这里的url就是activeVolume之后的installpath
         * 用这个url参数去agent进行iscsi登录即可
         *
         IscsiRemoteTarget target = new IscsiRemoteTarget()
         target.setPort(3260)
         target.setTransport("tcp")
         target.setIqn(iscsi.getIqn())
         target.setIp(nodes.stream().map({ i -> i.getGatewayIp() }).collect(Collectors.joining(",")))
         String lunId = exponVol.id
         String lunType = ExponStorageController.LunType.Volume
         target.setDiskId(controller.getDiskId(lunId, lunType))
         String url = target.getResourceURI()

        向agent发送请求，进行iscsi login
        KVM_LOGIN_ISCSI_PATH = "/iscsi/target/login";
        这里的destHostUuid就是对应的物理机uuid
        例如：
        KVMAgentCommands.LoginIscsiTargetCmd cmd = new KVMAgentCommands.LoginIscsiTargetCmd()
        cmd.setUrl(url)
        httpCall(KVMConstant.KVM_LOGIN_ISCSI_PATH, destHostUuid, cmd, KvmIscsiCommands.AgentRsp.class, new ReturnValueCompletion<KvmIscsiCommands.AgentRsp>(compl) {
        });
        **/

//        apiHelper.removeVolumeFromIscsiClientGroup(exponVol.id, client.id)
//        apiHelper.deleteVolume(exponVol.id, true)
    }
}
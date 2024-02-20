package org.zstack.test.integration.storage.primary.addon

import org.zstack.core.Platform
import org.zstack.core.cloudbus.CloudBus
import org.zstack.core.db.DatabaseFacade
import org.zstack.expon.ExponApiHelper
import org.zstack.expon.ExponNameHelper
import org.zstack.expon.ExponStorageController
import org.zstack.expon.sdk.iscsi.IscsiClientGroupModule
import org.zstack.expon.sdk.iscsi.IscsiModule
import org.zstack.expon.sdk.iscsi.IscsiSeverNode
import org.zstack.expon.sdk.iscsi.IscsiUssResource
import org.zstack.expon.sdk.volume.ExponVolumeQos
import org.zstack.expon.sdk.volume.VolumeModule
import org.zstack.expon.sdk.volume.VolumeSnapshotModule
import org.zstack.sdk.*
import org.zstack.storage.addon.primary.ExternalPrimaryStorageFactory
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

import static org.zstack.expon.ExponIscsiHelper.buildVolumeIscsiTargetName

class ExponIscsiCase extends SubCase {
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

    String exponUrl = "https://wangzhenyi:Admin123@172.25.130.128:443/pool"
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
            bus = bean(CloudBus.class)

            testCreateExponStorage()
            testExponIscsiAttach()
        }
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

    void testExponIscsiAttach() {
        /**
         * create iscsi volume
         */
        def poolId = controller.addonInfo.getPools()[0].id
        def exponVol = controller.apiHelper.createVolume("test_iscsi_vol_jin_" + Platform.uuid, poolId, SizeUnit.GIGABYTE.toByte(17))
        assert exponVol.runStatus == "normal"

        /***
         * update iscsi volume
         */
        controller.apiHelper.updateVolume(exponVol.id, "xiaojinjin")
        def res = controller.apiHelper.queryVolume("xiaojinjin")
        assert res != null
        assert res.id == exponVol.id
        /**
         * attach iscsi volume to instance
         *  (1) create iscsi target
         *  (2) create iscsi client group
         *  (3) iscsi client group attach to iscsi target
         *  (4) iscsi login
         */
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
        String clientIqn = "172.27.15.145"
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


        /**
         * set iscsi volume qos
         */
        ExponVolumeQos qos = new ExponVolumeQos()
        qos.setBpsLimit(10485760)
        qos.setIopsLimit(10000)
        apiHelper.setVolumeQos(exponVol.id, qos)
        def volume = apiHelper.queryVolume(exponVol.name)
        assert volume.isQosStatus()
//        assert volume.getQos().getBpsLimit() == 10485760
//        assert volume.getQos().getIopsLimit() == 10000



        /**
         *  expand iscsi volume
         */
        volume = apiHelper.queryVolume(exponVol.name)
        assert volume.getVolumeSize() == 17L * 1024L * 1024L * 1024L
        apiHelper.expandVolume(exponVol.id, 20L * 1024L * 1024L * 1024L)
        volume = apiHelper.queryVolume(exponVol.name)
        assert volume.getVolumeSize() == 20L * 1024L * 1024L * 1024L

        /**
         * create iscsi volume snapshot
         */
        def snapshot = apiHelper.createVolumeSnapshot(exponVol.id, "iscsi-volume-snapshot" + volume.id, "test")
        apiHelper.addSnapshotToIscsiClientGroup(snapshot.id, client.id, iscsi.id)
        def snap = apiHelper.queryVolumeSnapshot("iscsi-volume-snapshot" + volume.id)
        assert snap.getSnapSize() == 20L * 1024L * 1024L * 1024L
        assert snap.getVolumeName() == exponVol.volumeName
        assert snap.getVolumeDispName() == exponVol.name

        /**
         * update iscsi volume snapshot
         */
        apiHelper.updateVolumeSnapshot(snapshot.id, "jinjin-snap", "jin test")
        def result = apiHelper.queryVolumeSnapshot("jinjin-snap")
        assert result.id == snapshot.id

        /**
//         * recovery volume from volume snapshot
//         */
//        apiHelper.removeVolumeFromIscsiClientGroup(exponVol.id, client.id)
//        apiHelper.recoverySnapshot(exponVol.id, snap.id)
//        /**
//         * delete iscsi volume snapshot
//         */
//        apiHelper.removeSnapshotFromIscsiClientGroup(snapshot.id, client.id)
//        apiHelper.deleteVolumeSnapshot(snapshot.id)
//        snap = apiHelper.queryVolumeSnapshot("iscsi-volume-snapshot" + volume.id)
//        assert snap == null
//
//        /**
//         * detach iscsi volume
//         */
//        // recovery has been remove
//        // apiHelper.removeVolumeFromIscsiClientGroup(exponVol.id, client.id)
//        // 以及iscsiadm rescan
//
//        /**
//         * delete iscsi volume snapshot
//         */
//        apiHelper.deleteVolume(exponVol.id, true)
//        volume = apiHelper.queryVolume(exponVol.name)
//        assert volume == null
    }
}

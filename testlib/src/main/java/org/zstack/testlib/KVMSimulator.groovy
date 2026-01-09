package org.zstack.testlib

import org.springframework.http.HttpEntity
import org.zstack.core.db.Q
import org.zstack.core.db.SQL
import org.zstack.core.db.SQLBatch
import org.zstack.header.Constants
import org.zstack.header.storage.primary.PrimaryStorageVO
import org.zstack.header.storage.primary.PrimaryStorageVO_
import org.zstack.header.storage.snapshot.TakeSnapshotsOnKvmJobStruct
import org.zstack.header.storage.snapshot.TakeSnapshotsOnKvmResultStruct
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.header.vm.VmInstanceVO_
import org.zstack.header.vm.devices.DeviceAddress
import org.zstack.header.vm.devices.VirtualDeviceInfo
import org.zstack.header.volume.VolumeInventory
import org.zstack.header.volume.VolumeVO
import org.zstack.header.volume.VolumeVO_
import org.zstack.kvm.KVMAgentCommands
import org.zstack.kvm.KVMConstant
import org.zstack.kvm.VolumeTO
import org.zstack.testlib.vfs.Qcow2
import org.zstack.testlib.vfs.VFS
import org.zstack.testlib.vfs.extensions.VFSPrimaryStorageTakeSnapshotBackend
import org.zstack.testlib.vfs.extensions.VFSSnapshot
import org.zstack.utils.BeanUtils
import org.zstack.utils.data.SizeUnit
import org.zstack.utils.gson.JSONObjectUtil

import javax.persistence.Tuple
import java.util.concurrent.ConcurrentHashMap

import static org.zstack.kvm.KVMAgentCommands.*
/**
 * Created by xing5 on 2017/6/6.
 */
class KVMSimulator implements Simulator {
    static ConcurrentHashMap<String, KVMAgentCommands.ConnectCmd> connectCmdConcurrentHashMap = new ConcurrentHashMap<>()
    private static Map<String, VFSPrimaryStorageTakeSnapshotBackend> takeSnapshotBackends = [:]

    static {
        BeanUtils.reflections.getSubTypesOf(VFSPrimaryStorageTakeSnapshotBackend.class).each { clz ->
            VFSPrimaryStorageTakeSnapshotBackend backend = clz.getConstructor().newInstance()
            VFSPrimaryStorageTakeSnapshotBackend old = takeSnapshotBackends[backend.primaryStorageType]
            assert old == null : "duplicated VFSPrimaryStorageTakeSnapshotBackend[type: ${backend.primaryStorageType}]," +
                    " ${backend} and ${old}"
            takeSnapshotBackends[backend.primaryStorageType] = backend
        }
    }

    static List<TakeSnapshotsOnKvmResultStruct> takeSnapshotByPrimaryStorage(HttpEntity<String> e, EnvSpec spec, List<TakeSnapshotsOnKvmJobStruct> snapshotJobs) {
        Map<String, List<TakeSnapshotsOnKvmJobStruct>> jobMap = new HashMap<>()
        Map<String, String> primaryStorageTypeMap = new HashMap<>()
        snapshotJobs.each { job ->
            Tuple tuple = SQL.New("select pri.uuid, pri.type from PrimaryStorageVO pri, VolumeVO vol where" +
                    " pri.uuid = vol.primaryStorageUuid and vol.uuid = :volUuid", Tuple.class).param("volUuid", job.volumeUuid)
                    .find()
            assert tuple.get(1) : "cannot find primary storage of volume[uuid: ${job.volumeUuid}]"

            jobMap.putIfAbsent((String) tuple.get(0), new ArrayList<>())
            jobMap.get((String) tuple.get(0)).add(job)
            primaryStorageTypeMap.putIfAbsent((String) tuple.get(0), (String) tuple.get(1))
        }

        List<TakeSnapshotsOnKvmResultStruct> results = new ArrayList<>()
        jobMap.entrySet().each { entry ->
            String psUuid = entry.key
            List<TakeSnapshotsOnKvmJobStruct> jobs = entry.value
            VFSPrimaryStorageTakeSnapshotBackend bkd = getVFSPrimaryStorageTakeSnapshotBackend(primaryStorageTypeMap.get(psUuid))
            results.addAll(bkd.takeSnapshotsOnVolumes(psUuid, e, spec, jobs))
        }


        return results
    }

    static VFSPrimaryStorageTakeSnapshotBackend getVFSPrimaryStorageTakeSnapshotBackend(String primaryStorageType) {
        VFSPrimaryStorageTakeSnapshotBackend bkd = takeSnapshotBackends[primaryStorageType]
        assert bkd != null : "cannot find VFSPrimaryStorageTakeSnapshotBackend[type: ${primaryStorageType}]"
        return bkd
    }

    @Override
    void registerSimulators(EnvSpec spec) {
        spec.simulator(KVMConstant.KVM_HOST_CAPACITY_PATH) { HttpEntity<String> e, EnvSpec espec ->
            def rsp = new KVMAgentCommands.HostCapacityResponse()

            KVMHostSpec kspec = espec.specByUuid(e.getHeaders().getFirst(Constants.AGENT_HTTP_HEADER_RESOURCE_UUID))
            rsp.success = true

            if (kspec == null) {
                rsp.usedCpu = 0
                rsp.cpuNum = 8
                rsp.totalMemory = SizeUnit.GIGABYTE.toByte(32)
                rsp.usedMemory = 0
                rsp.cpuSpeed = 1
                rsp.cpuSockets = 2
                rsp.cpuCoreNum = 4
            } else {
                rsp.usedCpu = kspec.usedCpu
                rsp.cpuNum = kspec.totalCpu
                rsp.totalMemory = kspec.totalMem
                rsp.usedMemory = kspec.usedMem
                rsp.cpuSpeed = 1
                rsp.cpuSockets = kspec.cpuSockets
                rsp.cpuCoreNum = kspec.cpuCores
            }

            return rsp
        }

        spec.simulator(KVMConstant.KVM_HARDEN_CONSOLE_PATH) {
            return new KVMAgentCommands.AgentResponse()
        }

        spec.simulator(KVMConstant.KVM_DELETE_CONSOLE_FIREWALL_PATH) {
            return new KVMAgentCommands.AgentResponse()
        }

        spec.simulator(KVMConstant.GET_VM_DEVICE_ADDRESS_PATH) { HttpEntity<String> e ->
            def cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.GetVmDeviceAddressCmd.class)
            def rsp = new KVMAgentCommands.GetVmDeviceAddressRsp()
            if (cmd.deviceTOs.keySet().contains(VolumeVO.class.simpleName)) {
                rsp.addresses = ["VolumeVO": []]
                for (Object o : cmd.deviceTOs.get(VolumeVO.class.simpleName)) {
                    VolumeTO to = JSONObjectUtil.rehashObject(o, VolumeTO.class)
                    rsp.addresses[VolumeVO.class.simpleName].add(new KVMAgentCommands.VmDeviceAddressTO(
                            addressType: "pci",
                            address: String.format("0000:%02d:00:0", to.deviceId),
                            deviceType: "disk",
                            uuid: to.volumeUuid
                    ))
                }
            }

            return rsp
        }

        spec.simulator(KVMConstant.GET_VIRTUALIZER_INFO_PATH) { HttpEntity<String> e ->
            def rsp = new GetVirtualizerInfoRsp()
            rsp.hostInfo = new VirtualizerInfoTO()
            rsp.hostInfo.version = "4.2.0-627.g36ee592.el7"
            rsp.hostInfo.virtualizer = "qemu-kvm"
            String hostUuid = e.getHeaders().getFirst(Constants.AGENT_HTTP_HEADER_RESOURCE_UUID)
            rsp.hostInfo.uuid = hostUuid

            def cmd = JSONObjectUtil.toObject(e.body, GetVirtualizerInfoCmd.class)
            rsp.vmInfoList = cmd.vmUuids.collect { vmUuid ->
                def to = new VirtualizerInfoTO()
                to.uuid = vmUuid
                to.version = "4.2.0-627.g36ee592.el7"
                to.virtualizer = "qemu-kvm"
                return to
            }

            return rsp
        }

        spec.simulator(KVMConstant.KVM_HOST_FACT_PATH) { HttpEntity<String> e ->
            def hostUuid = e.getHeaders().getFirst(Constants.AGENT_HTTP_HEADER_RESOURCE_UUID)
            def rsp = new HostFactResponse()

            rsp.osDistribution = "zstack"
            rsp.osRelease = "kvmSimulator"
            rsp.osVersion = "0.1"
            rsp.qemuImgVersion = "2.0.0"
            rsp.libvirtVersion = "1.2.9"
            rsp.cpuModelName = "Broadwell"
            rsp.cpuProcessorNum = 10
            rsp.cpuThreadsPerCore = 2
            rsp.cpuCoresPerSocket = 5
            rsp.cpuSockets = 1
            rsp.cpuGHz = "2.10"
            rsp.hostCpuModelName = "Broadwell @ 2.10GHz"
            rsp.ipmiAddress = "None"
            rsp.eptFlag = "ept"
            rsp.libvirtCapabilities = ["incrementaldrivemirror", "blockcopynetworktarget"]
            rsp.powerSupplyModelName = ""
            rsp.powerSupplyManufacturer = ""
            rsp.hvmCpuFlag = ""
            rsp.cpuCache = "64.0,4096.0,16384.0"
            rsp.nqn = "nqn.2014-08.org.nvmexpress:uuid:748d0363-8366-44db-803b-146effb96988"
            rsp.hostname = "hostname"
            rsp.iscsiInitiatorName = "iqn.1994-05.com.redhat:" + hostUuid.substring(0, 12)
            rsp.virtualizerInfo = new VirtualizerInfoTO()
            rsp.virtualizerInfo.version = "4.2.0-627.g36ee592.el7"
            rsp.virtualizerInfo.virtualizer = "qemu-kvm"
            return rsp
        }

        spec.simulator(KVMConstant.KVM_VM_UPDATE_CPU_QUOTA_PATH) {
            return new KVMAgentCommands.UpdateVmCpuQuotaRsp()
        }

        spec.simulator(KVMConstant.KVM_VM_UPDATE_PRIORITY_PATH) {
            return new KVMAgentCommands.UpdateVmPriorityRsp()
        }

        spec.simulator(KVMConstant.KVM_VM_CHECK_STATE) { HttpEntity<String> e ->
            KVMAgentCommands.CheckVmStateCmd cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.CheckVmStateCmd.class)
            List<VmInstanceVO> vms = Q.New(VmInstanceVO.class).in(VmInstanceVO_.uuid, cmd.vmUuids).list()
            KVMAgentCommands.CheckVmStateRsp rsp = new KVMAgentCommands.CheckVmStateRsp()
            rsp.states = [:]
            vms.each {
                def kstate = KVMConstant.KvmVmState.fromVmInstanceState(it.state)
                if (kstate != null) {
                    rsp.states[(it.uuid)] = kstate.toString()
                } else {
                    rsp.states[(it.uuid)] = KVMConstant.KvmVmState.Shutdown.toString()
                }
            }

            return rsp
        }

        spec.simulator(KVMConstant.KVM_ATTACH_NIC_PATH) { HttpEntity<String> e ->
            KVMAgentCommands.AttachNicCommand cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.AttachNicCommand.class)
            KVMAgentCommands.AttachNicResponse rsp = new KVMAgentCommands.AttachNicResponse()
            VirtualDeviceInfo deviceInfo = new VirtualDeviceInfo()
            deviceInfo.setResourceUuid(cmd.nic.getUuid())
            rsp.setVirtualDeviceInfoList(Arrays.asList(deviceInfo))
            return rsp
        }

        spec.simulator(KVMConstant.KVM_DETACH_NIC_PATH) {
            return new KVMAgentCommands.DetachNicRsp()
        }

        spec.simulator(KVMConstant.KVM_UPDATE_NIC_PATH) {
            return new KVMAgentCommands.UpdateNicRsp()
        }

        spec.simulator(KVMConstant.KVM_ATTACH_ISO_PATH) {
            return new KVMAgentCommands.AttachIsoRsp()
        }

        spec.simulator(KVMConstant.KVM_DETACH_ISO_PATH) {
            return new KVMAgentCommands.DetachIsoRsp()
        }

        spec.simulator(KVMConstant.KVM_MERGE_SNAPSHOT_PATH) { HttpEntity<String> e, EnvSpec espec ->
            return new KVMAgentCommands.MergeSnapshotRsp()
        }

        VFS.vfsHook(KVMConstant.KVM_MERGE_SNAPSHOT_PATH, spec) { rsp, HttpEntity<String> e, EnvSpec espec ->
            KVMAgentCommands.MergeSnapshotCmd cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.MergeSnapshotCmd.class)
            VolumeInventory volume = null
            String primaryStorageType = null

            new SQLBatch() {
                @Override
                protected void scripts() {
                    VolumeVO vol = sql("select vol from VolumeVO vol where vol.vmInstanceUuid = :vmUuid " +
                            " and vol.deviceId = :deviceId", VolumeVO.class)
                            .param("vmUuid", cmd.vmUuid)
                            .param("deviceId", cmd.volume.getDeviceId())
                            .find()

                    assert vol: "cannot find dest volume[path: ${cmd.destPath}, deviceId: ${cmd.volume.getDeviceId()}] of VM[uuid: ${cmd.vmUuid}] in database"
                    volume = vol.toInventory()

                    primaryStorageType = q(PrimaryStorageVO.class).select(PrimaryStorageVO_.type).eq(PrimaryStorageVO_.uuid, vol.primaryStorageUuid).findValue()
                    assert primaryStorageType: "cannot find primary storage[uuid: ${vol.primaryStorageUuid}]"
                }
            }.execute()

            VFSPrimaryStorageTakeSnapshotBackend bkd = getVFSPrimaryStorageTakeSnapshotBackend(primaryStorageType)
            bkd.mergeSnapshots(e, espec, cmd, volume)
        }

        spec.simulator(KVMConstant.KVM_TAKE_VOLUME_SNAPSHOT_PATH) { HttpEntity<String> e, EnvSpec espec ->
            return new KVMAgentCommands.TakeSnapshotResponse()
        }

        VFS.vfsHook(KVMConstant.KVM_TAKE_VOLUME_SNAPSHOT_PATH, spec) { rsp, HttpEntity<String> e, EnvSpec espec ->
            KVMAgentCommands.TakeSnapshotCmd cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.TakeSnapshotCmd.class)

            VolumeVO volume = Q.New(VolumeVO.class).eq(VolumeVO_.uuid, cmd.volumeUuid).find()
            assert volume: "cannot find volume[uuid: ${cmd.volumeUuid}]"
            String primaryStorageType = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.type)
                    .eq(PrimaryStorageVO_.uuid, volume.primaryStorageUuid).findValue()
            assert primaryStorageType: "cannot find primary storage[uuid: ${volume.primaryStorageUuid}] from volume[uuid: ${volume.uuid}, name: ${volume.name}]"
            VFSPrimaryStorageTakeSnapshotBackend bkd = getVFSPrimaryStorageTakeSnapshotBackend(primaryStorageType)

            VFSSnapshot snapshot = bkd.takeSnapshot(e, espec, cmd, volume.toInventory() as VolumeInventory)
            rsp.newVolumeInstallPath = snapshot.installPath
            rsp.snapshotInstallPath = cmd.volumeInstallPath
            // size required greater than 0, if no mock value set keep size return 1
            rsp.size = snapshot.size == null || snapshot.size == 0 ? 1 : snapshot.size
            return rsp
        }

        spec.simulator(KVMConstant.KVM_PING_PATH) { HttpEntity<String> e, EnvSpec espec ->
            KVMAgentCommands.PingCmd cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.PingCmd.class)
            assert null != cmd
            assert null != cmd.hostUuid

            def rsp = new KVMAgentCommands.PingResponse()
            rsp.hostUuid = cmd.hostUuid
            rsp.version = connectCmdConcurrentHashMap.get(rsp.hostUuid).version
            rsp.sendCommandUrl = connectCmdConcurrentHashMap.get(rsp.hostUuid).sendCommandUrl
            return rsp
        }

        spec.simulator(KVMConstant.KVM_UPDATE_HOST_CONFIGURATION_PATH) { HttpEntity<String> e, EnvSpec espec ->
            def rsp = new KVMAgentCommands.UpdateHostConfigurationResponse()
            return rsp
        }

        spec.simulator(KVMConstant.KVM_CONNECT_PATH) { HttpEntity<String> e ->
            Spec.checkHttpCallType(e, true)
            KVMAgentCommands.ConnectCmd cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.ConnectCmd.class)

            connectCmdConcurrentHashMap.put(cmd.hostUuid, cmd)

            def rsp = new KVMAgentCommands.ConnectResponse()
            rsp.success = true
            rsp.libvirtVersion = "1.0.0"
            rsp.qemuVersion = "1.3.0"
            return rsp
        }

        spec.simulator(KVMConstant.KVM_ECHO_PATH) { HttpEntity<String> e ->
            Spec.checkHttpCallType(e, true)
            return [:]
        }

        spec.simulator(KVMConstant.KVM_DETACH_VOLUME) {
            return new KVMAgentCommands.DetachDataVolumeResponse()
        }

        spec.simulator(KVMConstant.KVM_VM_SYNC_PATH) { HttpEntity<String> e ->
            def hostUuid = e.getHeaders().getFirst(Constants.AGENT_HTTP_HEADER_RESOURCE_UUID)

            List<Tuple> states = Q.New(VmInstanceVO.class)
                    .select(VmInstanceVO_.uuid, VmInstanceVO_.state)
                    .in(VmInstanceVO_.state, [VmInstanceState.Running, VmInstanceState.Unknown])
                    .eq(VmInstanceVO_.hostUuid, hostUuid).listTuple()

            def rsp = new KVMAgentCommands.VmSyncResponse()
            rsp.states = [:]
            states.each {
                String vmUuid = it.get(0, String.class)
                VmInstanceState state = it.get(1, VmInstanceState.class)
                if (state == VmInstanceState.Unknown) {
                    // host reconnecting will set VMs to Unknown in DB
                    // the spec.simulator treat them as Running by default
                    rsp.states[(vmUuid)] = KVMConstant.KvmVmState.Running.toString()
                } else {
                    rsp.states[(vmUuid)] = KVMConstant.KvmVmState.fromVmInstanceState(state).toString()
                }
            }
            rsp.setVmInShutdowns(new ArrayList<String>())
            return rsp
        }

        spec.simulator(KVMConstant.KVM_VOLUME_SYNC_PATH) {
            return new VolumeSyncRsp()
        }

        spec.simulator(KVMConstant.KVM_ATTACH_VOLUME) { HttpEntity<String> e ->
            def cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.AttachDataVolumeCmd.class)
            // assume all data volumes has same deviceType.
            if (Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.type).listValues().stream().distinct().count() == 1) {
                assert (cmd.addons["attachedDataVolumes"] as List<VolumeTO>).stream()
                        .allMatch({ vol -> vol.deviceType == cmd.volume.deviceType })
            }
            return new KVMAgentCommands.AttachDataVolumeResponse()
        }

        spec.simulator(KVMConstant.KVM_CHECK_PHYSICAL_NETWORK_INTERFACE_PATH) { HttpEntity<String> e ->
            Spec.checkHttpCallType(e, true)
            return new KVMAgentCommands.CheckPhysicalNetworkInterfaceResponse()
        }

        spec.simulator(KVMConstant.KVM_ADD_INTERFACE_TO_BRIDGE_PATH) {
            return new KVMAgentCommands.AddInterfaceToBridgeResponse()
        }

        spec.simulator(KVMConstant.KVM_REALIZE_L2NOVLAN_NETWORK_PATH) {
            return new KVMAgentCommands.CreateBridgeResponse()
        }

        spec.simulator(KVMConstant.KVM_MIGRATE_VM_PATH) {
            return new KVMAgentCommands.MigrateVmResponse()
        }

        spec.simulator(KVMConstant.KVM_GET_CPU_XML_PATH) {
            return new KVMAgentCommands.VmGetCpuXmlResponse()
        }

        spec.simulator(KVMConstant.KVM_COMPARE_CPU_FUNCTION_PATH) {
            return new KVMAgentCommands.VmCompareCpuFunctionResponse()
        }

        spec.simulator(KVMConstant.KVM_UPDATE_L2VLAN_NETWORK_PATH) {
            return new KVMAgentCommands.UpdateL2NetworkResponse()
        }

        spec.simulator(KVMConstant.KVM_UPDATE_L2VXLAN_NETWORK_PATH) {
            return new KVMAgentCommands.UpdateL2NetworkResponse()
        }

        spec.simulator(KVMConstant.KVM_CHECK_L2NOVLAN_NETWORK_PATH) {
            return new KVMAgentCommands.CheckBridgeResponse()
        }

        spec.simulator(KVMConstant.KVM_CHECK_L2VLAN_NETWORK_PATH) {
            return new KVMAgentCommands.CheckVlanBridgeResponse()
        }

        spec.simulator(KVMConstant.KVM_REALIZE_L2VLAN_NETWORK_PATH) {
            return new KVMAgentCommands.CreateVlanBridgeResponse()
        }

        spec.simulator(KVMConstant.KVM_CHECK_OVSDPDK_NETWORK_PATH) {
            return new KVMAgentCommands.AgentResponse()
        }

        spec.simulator(KVMConstant.KVM_REALIZE_OVSDPDK_NETWORK_PATH) {
            return new KVMAgentCommands.CreateBridgeResponse()
        }

        spec.simulator(KVMConstant.KVM_GENERATE_VDPA_PATH) { HttpEntity<String> e ->
            KVMAgentCommands.GenerateVdpaCmd cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.GenerateVdpaCmd.class)
            def rsp = new KVMAgentCommands.GenerateVdpaResponse()

            ArrayList<KVMAgentCommands.NicTO> nics = cmd.getNics()
            def vdpaPaths = new ArrayList<String>()

            nics.each { KVMAgentCommands.NicTO it ->
                vdpaPaths.add("/var/run/zstack-vdpa/" + it.bridgeName + "/" + it.nicInternalName)
            }
            rsp.setVdpaPaths(vdpaPaths)

            return rsp
        }

        spec.simulator(KVMConstant.KVM_DELETE_VDPA_PATH) {
            return new KVMAgentCommands.AgentResponse()
        }

        spec.simulator(KVMConstant.KVM_GENERATE_VHOST_USER_CLIENT_PATH) {
            return new KVMAgentCommands.AgentResponse()
        }

        spec.simulator(KVMConstant.KVM_DELETE_VHOST_USER_CLIENT_PATH) {
            return new KVMAgentCommands.AgentResponse()
        }

        spec.simulator(KVMConstant.KVM_SYNC_VM_DEVICEINFO_PATH) { HttpEntity<String> e ->
            SyncVmDeviceInfoCmd cmd = JSONObjectUtil.toObject(e.body, SyncVmDeviceInfoCmd.class)
            def rsp = new SyncVmDeviceInfoResponse()

            rsp.virtualizerInfo = new VirtualizerInfoTO()
            rsp.virtualizerInfo.uuid = cmd.vmInstanceUuid
            rsp.virtualizerInfo.virtualizer = "qemu-kvm"
            rsp.virtualizerInfo.version = "4.2.0-632.g6a6222b.el7"

            return rsp
        }

        spec.simulator(KVMConstant.KVM_START_VM_PATH) { HttpEntity<String> e ->
            StartVmCmd cmd = JSONObjectUtil.toObject(e.body, StartVmCmd.class)
            assert new HashSet<>(cmd.dataVolumes.deviceId).size() == cmd.dataVolumes.size()
            StartVmResponse rsp = new StartVmResponse()
            rsp.virtualDeviceInfoList = []
            List<VolumeTO> pciInfo = new ArrayList<VolumeTO>()
            pciInfo.add(cmd.rootVolume)
            pciInfo.addAll(cmd.dataVolumes)

            Integer counter = 0
            pciInfo.each { to ->
                VirtualDeviceInfo info = new VirtualDeviceInfo()
                info.resourceUuid = to.volumeUuid
                info.deviceAddress = new DeviceAddress()
                info.deviceAddress.domain = "0000"
                info.deviceAddress.bus = "00"
                info.deviceAddress.slot = Integer.toHexString(counter)
                info.deviceAddress.function = "0"

                counter++

                rsp.virtualDeviceInfoList.add(info)
            }

            rsp.virtualizerInfo = new VirtualizerInfoTO()
            rsp.virtualizerInfo.uuid = cmd.vmInstanceUuid
            rsp.virtualizerInfo.virtualizer = "qemu-kvm"
            rsp.virtualizerInfo.version = "4.2.0-632.g6a6222b.el7"

            return rsp
        }

        spec.simulator(KVMConstant.KVM_STOP_VM_PATH) {
            return new KVMAgentCommands.StopVmResponse()
        }

        spec.simulator(KVMConstant.KVM_PAUSE_VM_PATH) {
            return new KVMAgentCommands.PauseVmResponse()
        }

        spec.simulator(KVMConstant.KVM_RESUME_VM_PATH) {
            return new KVMAgentCommands.ResumeVmResponse()
        }

        spec.simulator(KVMConstant.KVM_REBOOT_VM_PATH) {
            return new KVMAgentCommands.RebootVmResponse()
        }

        spec.simulator(KVMConstant.KVM_DESTROY_VM_PATH) {
            return new KVMAgentCommands.DestroyVmResponse()
        }

        spec.simulator(KVMConstant.KVM_GET_VNC_PORT_PATH) {
            def rsp = new KVMAgentCommands.GetVncPortResponse()
            rsp.port = 5900
            return rsp
        }

        spec.simulator(KVMConstant.KVM_LOGOUT_ISCSI_PATH) {
            return new KVMAgentCommands.LogoutIscsiTargetRsp()
        }

        spec.simulator(KVMConstant.KVM_LOGIN_ISCSI_PATH) {
            return new KVMAgentCommands.LoginIscsiTargetRsp()
        }

        spec.simulator(KVMConstant.KVM_VM_ONLINE_INCREASE_CPU) {
            return new KVMAgentCommands.IncreaseCpuResponse()
        }

        spec.simulator(KVMConstant.KVM_VM_ONLINE_INCREASE_MEMORY) {
            return new KVMAgentCommands.IncreaseMemoryResponse()
        }

        spec.simulator(KVMConstant.KVM_UPDATE_HOST_OS_PATH) {
            return new KVMAgentCommands.UpdateHostOSRsp()
        }

        spec.simulator(KVMConstant.KVM_HOST_UPDATE_DEPENDENCY_PATH) {
            return new KVMAgentCommands.UpdateDependencyRsp()
        }

        spec.simulator(KVMConstant.HOST_UPDATE_SPICE_CHANNEL_CONFIG_PATH) {
            return new KVMAgentCommands.UpdateSpiceChannelConfigResponse()
        }

        spec.simulator(KVMConstant.KVM_SCAN_VM_PORT_STATUS) {
            return new KVMAgentCommands.ScanVmPortResponse()
        }

        spec.simulator(KVMConstant.GET_DEV_CAPACITY) {
            KVMAgentCommands.GetDevCapacityResponse rsp = new KVMAgentCommands.GetDevCapacityResponse()
            rsp.totalSize = SizeUnit.GIGABYTE.toByte(100)
            rsp.availableSize = SizeUnit.GIGABYTE.toByte(80)
            return rsp
        }

        spec.simulator(KVMConstant.KVM_CONFIG_PRIMARY_VM_PATH) {
            return new KVMAgentCommands.AgentResponse()
        }

        spec.simulator(KVMConstant.KVM_CONFIG_SECONDARY_VM_PATH) {
            return new KVMAgentCommands.AgentResponse()
        }

        spec.simulator(KVMConstant.KVM_START_COLO_SYNC_PATH) {
            return new KVMAgentCommands.AgentResponse()
        }

        spec.simulator(KVMConstant.KVM_REGISTER_PRIMARY_VM_HEARTBEAT) {
            return new KVMAgentCommands.AgentResponse()
        }

        spec.simulator(KVMConstant.KVM_HOST_CHECK_FILE_PATH) { HttpEntity<String> e ->
            KVMAgentCommands.CheckFileOnHostCmd cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.CheckFileOnHostCmd.class)
            KVMAgentCommands.CheckFileOnHostResponse response = new KVMAgentCommands.CheckFileOnHostResponse()
            response.existPaths = new HashMap<>()
            cmd.paths.forEach({ path -> response.existPaths.put(path, "") })
            return response
        }

        spec.simulator(KVMConstant.KVM_HOST_NUMA_PATH) {
            def rsp = new KVMAgentCommands.GetHostNUMATopologyResponse()
            return rsp
        }

        spec.simulator(KVMConstant.KVM_HOST_ATTACH_VOLUME_PATH) {
            def rsp = new KVMAgentCommands.AttachVolumeRsp()
            rsp.device = "/dev/nbd0"
            return rsp
        }

        spec.simulator(KVMConstant.KVM_HOST_DETACH_VOLUME_PATH) {
            def rsp = new KVMAgentCommands.DetachVolumeRsp()
            return rsp
        }

        spec.simulator(KVMConstant.KVM_BLOCK_COMMIT_VOLUME_PATH) { HttpEntity<String> e ->
            def rsp = new BlockCommitResponse()
            rsp.size = 1
            return rsp
        }

        VFS.vfsHook(KVMConstant.KVM_BLOCK_COMMIT_VOLUME_PATH, spec) { rsp, HttpEntity<String> e, EnvSpec espec ->
            BlockCommitCmd cmd = JSONObjectUtil.toObject(e.body, BlockCommitCmd.class)

            VolumeVO volume = Q.New(VolumeVO.class).eq(VolumeVO_.uuid, cmd.volume.getVolumeUuid()).find()
            assert volume: "cannot find volume[uuid: ${cmd.volume.getVolumeUuid()}]"

            String primaryStorageType = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.type)
                    .eq(PrimaryStorageVO_.uuid, volume.primaryStorageUuid).findValue()
            assert primaryStorageType: "cannot find primary storage[uuid: ${volume.primaryStorageUuid}] " +
                    "from volume[uuid: ${volume.uuid}, name: ${volume.name}]"

            VFSPrimaryStorageTakeSnapshotBackend bkd = getVFSPrimaryStorageTakeSnapshotBackend(primaryStorageType)
            bkd.blockCommit(e, espec, cmd, volume.toInventory() as VolumeInventory)
            return rsp
        }

        spec.simulator(KVMConstant.KVM_BLOCK_PULL_VOLUME_PATH) { HttpEntity<String> e ->
            def rsp = new BlockPullResponse()
            rsp.size = 1
            return rsp
        }

        VFS.vfsHook(KVMConstant.KVM_BLOCK_PULL_VOLUME_PATH, spec) { BlockPullResponse rsp, HttpEntity<String> e, EnvSpec espec ->
            BlockPullCmd cmd = JSONObjectUtil.toObject(e.body, BlockPullCmd.class)

            VolumeVO volume = Q.New(VolumeVO.class).eq(VolumeVO_.uuid, cmd.volume.getVolumeUuid()).find()
            assert volume: "cannot find volume[uuid: ${cmd.getVolume().getVolumeUuid()}]"

            String primaryStorageType = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.type)
                    .eq(PrimaryStorageVO_.uuid, volume.primaryStorageUuid).findValue()
            assert primaryStorageType: "cannot find primary storage[uuid: ${volume.primaryStorageUuid}] " +
                    "from volume[uuid: ${volume.uuid}, name: ${volume.name}]"

            VFSPrimaryStorageTakeSnapshotBackend bkd = getVFSPrimaryStorageTakeSnapshotBackend(primaryStorageType)

            Qcow2 newInstallPath = bkd.blockPull(e, espec, cmd, volume.toInventory() as VolumeInventory)
            rsp.size = newInstallPath.actualSize == 0 ? 1 : newInstallPath.actualSize
            return rsp
        }

        spec.simulator(KVMConstant.TAKE_VM_CONSOLE_SCREENSHOT_PATH) {
            def rsp = new KVMAgentCommands.TakeVmConsoleScreenshotRsp()
            rsp.imageData = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAHgAAABECAIAAADFvmZTAAACA0lEQVR4nO3VMYviQBQH8Hkvk5hE2ZUEU+juFlbKauH3/wYWNhYWKiiIRBDcZDckJJmZK6Zb7q44uJW7/f+qCcOQzP89XoSAL0FPT09VVTFzEARhGKZp2rat67qO4xhjlFJ5nidJcr1eHx8fPc+r65qZ27Zt2zbP83t//z9DRlFUVRURGWN83/c8z/O8Xq/HzFEUvb29aa211sxsgxZCKKWIKMuyPM9tSWypHMdRSmmtP72DmTudTlmWRCSl1Forpe5x2XuSSZJcLpeiKPr9flEUdV0LIZqm+fj4GI/H+/1+Npsdj8fX11chRFVV5/N5MBgIIZg5juP5fN7tdpfL5WAwiKJovV4Ph8MgCDabzWQyKcvycDgsFoskSVarlZTy+fl5t9udz2db2jvf/gsRETGz1tp13bZtbT8yszHGdqiNw66Z2W4ZY7TWUkqbl5TSGGOPSymFEHb+2Oa1u47jEFHTNFrrbxWxRQ8PD1LKuq7Lsux2u8zcNE1d10EQFEXR6XRc1yWiPM9937cxua6rlPrplIBfkS8vL2EYZlmWpul0OvV9/3Q6vb+/j0aj4/EYx3EYhkqp7XY7HA6TJDHG3G43IjocDnbsfsP2/OuIyP4P4U8Q0W8eAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADgf/QDviYImvcayGAAAAAASUVORK5CYII="
            return rsp
        }

        spec.simulator(KVMConstant.KVM_UPDATE_HOST_NQN_PATH) {
            def rsp = new KVMAgentCommands.UpdateHostNqnRsp()
            return rsp
        }

        spec.simulator(KVMConstant.KVM_HOST_FILE_DOWNLOAD_PATH) {
            DownloadFileResponse rsp = new DownloadFileResponse()
            rsp.md5sum = "00df1327d49e4631a21f4467aa729c11"
            rsp.size = 1024
            return rsp
        }

        spec.simulator(KVMConstant.KVM_HOST_FILE_UPLOAD_PATH) {
            UploadFileResponse rsp = new UploadFileResponse()
            rsp.directUploadPath = "http://172.1.1.1:7070/host/file/direct-upload"
            return rsp
        }

        spec.simulator(KVMConstant.KVM_HOST_FILE_DOWNLOAD_PROGRESS_PATH) {
            GetDownloadFileProgressResponse rsp = new GetDownloadFileProgressResponse()
            rsp.completed = false
            rsp.downloadSize = 1
            rsp.size = 1024
            rsp.lastOpTime = System.currentTimeMillis()
            return rsp
        }

        spec.simulator(KVMConstant.KVM_UPDATE_HOSTNAME_PATH) {
            return new UpdateHostnameRsp()
        }

        spec.simulator(KVMConstant.WRITE_VM_INSTANCE_METADATA_PATH) { HttpEntity<String> e ->
            return new WriteVmInstanceMetadataRsp()
        }

        spec.simulator(KVMConstant.READ_VM_INSTANCE_METADATA_PATH) { HttpEntity<String> e ->
            def rsp = new ReadVmInstanceMetadataRsp()
            rsp.metadata = "{\"vmInstanceVO\":\"{\\\"vmNics\\\":[{\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"l3NetworkUuid\\\":\\\"28d3a9c8e54c48f290ab4f9e52bbb006\\\",\\\"mac\\\":\\\"fa:81:16:b2:32:00\\\",\\\"hypervisorType\\\":\\\"KVM\\\",\\\"deviceId\\\":0,\\\"internalName\\\":\\\"vnic1.0\\\",\\\"driverType\\\":\\\"virtio\\\",\\\"type\\\":\\\"VNIC\\\",\\\"state\\\":\\\"enable\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"usedIps\\\":[],\\\"uuid\\\":\\\"a77234a5a45a4a7caca46d01d746f41f\\\",\\\"resourceType\\\":\\\"VmNicVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.vm.VmNicVO\\\"}],\\\"allVolumes\\\":[{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":2,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":2,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"resourceName\\\":\\\"volumeName1null\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"},{\\\"name\\\":\\\"ROOT-for-vmName\\\",\\\"description\\\":\\\"Root volume for VM[uuid:77bc3074f5f4438c836ce6c56bc5a4aa]\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"rootImageUuid\\\":\\\"575591e021b446e4b465e981da3a8d1b\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"type\\\":\\\"Root\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":0,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"ROOT-for-vmName\\\",\\\"description\\\":\\\"Root volume for VM[uuid:77bc3074f5f4438c836ce6c56bc5a4aa]\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"rootImageUuid\\\":\\\"575591e021b446e4b465e981da3a8d1b\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"type\\\":\\\"Root\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":0,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceName\\\":\\\"ROOT-for-vmName\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"},{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/43436624dc714282913e0a141246629e\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":1,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/43436624dc714282913e0a141246629e\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":1,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"resourceName\\\":\\\"volumeName1null\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"},{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":3,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":3,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"resourceName\\\":\\\"volumeName1null\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"}],\\\"vmCdRoms\\\":[{\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"deviceId\\\":0,\\\"name\\\":\\\"vm-77bc3074f5f4438c836ce6c56bc5a4aa-cdRom\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"uuid\\\":\\\"e8a57f5b8c834573b4da822b672740e4\\\",\\\"resourceName\\\":\\\"vm-77bc3074f5f4438c836ce6c56bc5a4aa-cdRom\\\",\\\"resourceType\\\":\\\"VmCdRomVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.vm.cdrom.VmCdRomVO\\\"}],\\\"name\\\":\\\"vmName\\\",\\\"zoneUuid\\\":\\\"d71de3f6981d46c9a2be43e5fcf31021\\\",\\\"clusterUuid\\\":\\\"29f13acb820d4f7f8cd3593b79b742e5\\\",\\\"imageUuid\\\":\\\"575591e021b446e4b465e981da3a8d1b\\\",\\\"hostUuid\\\":\\\"e99debc09c5845fb8ed682320117f4ce\\\",\\\"internalId\\\":1,\\\"lastHostUuid\\\":\\\"e99debc09c5845fb8ed682320117f4ce\\\",\\\"rootVolumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"defaultL3NetworkUuid\\\":\\\"28d3a9c8e54c48f290ab4f9e52bbb006\\\",\\\"type\\\":\\\"UserVm\\\",\\\"hypervisorType\\\":\\\"KVM\\\",\\\"cpuNum\\\":1,\\\"cpuSpeed\\\":0,\\\"memorySize\\\":1073741824,\\\"reservedMemorySize\\\":0,\\\"platform\\\":\\\"Linux\\\",\\\"architecture\\\":\\\"x86_64\\\",\\\"guestOsType\\\":\\\"CentOS\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:45 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"state\\\":\\\"Running\\\",\\\"uuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceName\\\":\\\"vmName\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.vm.VmInstanceVO\\\"}\",\"vmSystemTags\":[\"{\\\"inherent\\\":false,\\\"uuid\\\":\\\"38a9b4bd1b8b3dfa829d582aafb2ec25\\\",\\\"resourceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"tag\\\":\\\"syncPorts::77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:45 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:45 AM\\\"}\",\"{\\\"inherent\\\":true,\\\"uuid\\\":\\\"3e984cdb5edb47559a3f907e1d49bfcc\\\",\\\"resourceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"tag\\\":\\\"additionalQmp\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\"}\",\"{\\\"inherent\\\":false,\\\"uuid\\\":\\\"85237d3a06133523bd84669349040ec5\\\",\\\"resourceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"tag\\\":\\\"vmPriority::Normal\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\"}\",\"{\\\"inherent\\\":true,\\\"uuid\\\":\\\"b7c5d5e94ba13159ab2c8c65c1d7bc29\\\",\\\"resourceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"tag\\\":\\\"vmSystemSerialNumber::8ed14f00-50bb-4e9e-9448-e92c0f67e1e1\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\"}\",\"{\\\"inherent\\\":false,\\\"uuid\\\":\\\"d5019730aeba3e57b2f1a3e8d74d0cbc\\\",\\\"resourceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"tag\\\":\\\"ha::None\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\"}\"],\"vmResourceConfigs\":[\"{\\\"uuid\\\":\\\"8d2f9937a28846aba03fded826c10c73\\\",\\\"resourceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"name\\\":\\\"nicMultiQueueNum\\\",\\\"description\\\":\\\"default num of queues on virtio nic\\\",\\\"category\\\":\\\"vm\\\",\\\"value\\\":\\\"1\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\"}\"],\"volumeVOs\":[\"{\\\"name\\\":\\\"ROOT-for-vmName\\\",\\\"description\\\":\\\"Root volume for VM[uuid:77bc3074f5f4438c836ce6c56bc5a4aa]\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"rootImageUuid\\\":\\\"575591e021b446e4b465e981da3a8d1b\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"type\\\":\\\"Root\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":0,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"ROOT-for-vmName\\\",\\\"description\\\":\\\"Root volume for VM[uuid:77bc3074f5f4438c836ce6c56bc5a4aa]\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"rootImageUuid\\\":\\\"575591e021b446e4b465e981da3a8d1b\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"type\\\":\\\"Root\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":0,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceName\\\":\\\"ROOT-for-vmName\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"}\",\"{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":3,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":3,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"resourceName\\\":\\\"volumeName1null\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"}\",\"{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":2,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":2,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"resourceName\\\":\\\"volumeName1null\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"}\",\"{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/43436624dc714282913e0a141246629e\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":1,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/43436624dc714282913e0a141246629e\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":1,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"resourceName\\\":\\\"volumeName1null\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"}\"],\"volumeSystemTags\":{\"b7290c15276b4700af2c1b108b2b62e1\":[\"{\\\"inherent\\\":true,\\\"uuid\\\":\\\"b9874ec02b583538a5603e7eec8c5b69\\\",\\\"resourceUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"tag\\\":\\\"kvm::volume::0x000f59f934d14a68\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\"],\"8d1e76eca52647f5a4544b9ff2d370de\":[\"{\\\"inherent\\\":true,\\\"uuid\\\":\\\"96cb4b006708387b8318f0fd6ae6ab8b\\\",\\\"resourceUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"tag\\\":\\\"kvm::volume::0x000faad0c9ca4231\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\"}\"],\"ae9f28cb5055498e8661793d204208ba\":[\"{\\\"inherent\\\":true,\\\"uuid\\\":\\\"5ceacd06bf753b0c8abe5bcef9b5a894\\\",\\\"resourceUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"tag\\\":\\\"kvm::volume::0x000fc4ffeaab6e71\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\"],\"db8251e870b14d60ace863a7598cce8b\":[\"{\\\"inherent\\\":true,\\\"uuid\\\":\\\"d53865baa675373a9bf07a6f501eab41\\\",\\\"resourceUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"tag\\\":\\\"kvm::volume::0x000fad154165d205\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"}\"]},\"volumeResourceConfigs\":{\"b7290c15276b4700af2c1b108b2b62e1\":[],\"8d1e76eca52647f5a4544b9ff2d370de\":[],\"ae9f28cb5055498e8661793d204208ba\":[],\"db8251e870b14d60ace863a7598cce8b\":[]},\"vmNicVOs\":[\"{\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"l3NetworkUuid\\\":\\\"28d3a9c8e54c48f290ab4f9e52bbb006\\\",\\\"mac\\\":\\\"fa:81:16:b2:32:00\\\",\\\"hypervisorType\\\":\\\"KVM\\\",\\\"deviceId\\\":0,\\\"internalName\\\":\\\"vnic1.0\\\",\\\"driverType\\\":\\\"virtio\\\",\\\"type\\\":\\\"VNIC\\\",\\\"state\\\":\\\"enable\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"usedIps\\\":[],\\\"uuid\\\":\\\"a77234a5a45a4a7caca46d01d746f41f\\\",\\\"resourceType\\\":\\\"VmNicVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.vm.VmNicVO\\\"}\"],\"vmNicSystemTags\":{\"a77234a5a45a4a7caca46d01d746f41f\":[]},\"vmNicResourceConfigs\":{\"a77234a5a45a4a7caca46d01d746f41f\":[]},\"volumeSnapshots\":{\"b7290c15276b4700af2c1b108b2b62e1\":[\"{\\\"uuid\\\":\\\"7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"treeUuid\\\":\\\"f8042fb57bb04ebcb0f01bab2abeb5dd\\\",\\\"parentUuid\\\":\\\"a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":true,\\\"size\\\":1,\\\"distance\\\":4,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\"}\",\"{\\\"uuid\\\":\\\"a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"treeUuid\\\":\\\"f8042fb57bb04ebcb0f01bab2abeb5dd\\\",\\\"parentUuid\\\":\\\"b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":3,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\"}\",\"{\\\"uuid\\\":\\\"b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"treeUuid\\\":\\\"f8042fb57bb04ebcb0f01bab2abeb5dd\\\",\\\"parentUuid\\\":\\\"bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":2,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\"}\",\"{\\\"uuid\\\":\\\"bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"treeUuid\\\":\\\"f8042fb57bb04ebcb0f01bab2abeb5dd\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":1,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:51 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\"}\"],\"8d1e76eca52647f5a4544b9ff2d370de\":[\"{\\\"uuid\\\":\\\"04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"treeUuid\\\":\\\"d4a030087ed3407894c393ee81f0bc3b\\\",\\\"parentUuid\\\":\\\"79caace79a1048d58ea7c0b38815bbd0\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/79caace79a1048d58ea7c0b38815bbd0\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":0,\\\"distance\\\":3,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\"}\",\"{\\\"uuid\\\":\\\"61e2ada0170142bb8b303910a27690aa\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"treeUuid\\\":\\\"d4a030087ed3407894c393ee81f0bc3b\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":0,\\\"distance\\\":1,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:51 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\"}\",\"{\\\"uuid\\\":\\\"79caace79a1048d58ea7c0b38815bbd0\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"treeUuid\\\":\\\"d4a030087ed3407894c393ee81f0bc3b\\\",\\\"parentUuid\\\":\\\"61e2ada0170142bb8b303910a27690aa\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/61e2ada0170142bb8b303910a27690aa\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":0,\\\"distance\\\":2,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\"}\",\"{\\\"uuid\\\":\\\"bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"treeUuid\\\":\\\"d4a030087ed3407894c393ee81f0bc3b\\\",\\\"parentUuid\\\":\\\"04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":true,\\\"size\\\":0,\\\"distance\\\":4,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\"}\"],\"ae9f28cb5055498e8661793d204208ba\":[\"{\\\"uuid\\\":\\\"1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"treeUuid\\\":\\\"055b80b0727e4117b246b1b29f2d58b6\\\",\\\"parentUuid\\\":\\\"aefbe47465c047d1b118321c34425869\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/aefbe47465c047d1b118321c34425869\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":3,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\"}\",\"{\\\"uuid\\\":\\\"69e85ac72fea4263a55cbcd21785006e\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"treeUuid\\\":\\\"055b80b0727e4117b246b1b29f2d58b6\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":1,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:51 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\"}\",\"{\\\"uuid\\\":\\\"aefbe47465c047d1b118321c34425869\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"treeUuid\\\":\\\"055b80b0727e4117b246b1b29f2d58b6\\\",\\\"parentUuid\\\":\\\"69e85ac72fea4263a55cbcd21785006e\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/69e85ac72fea4263a55cbcd21785006e\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":2,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\"}\",\"{\\\"uuid\\\":\\\"b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"treeUuid\\\":\\\"055b80b0727e4117b246b1b29f2d58b6\\\",\\\"parentUuid\\\":\\\"1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":true,\\\"size\\\":1,\\\"distance\\\":4,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\"}\"],\"db8251e870b14d60ace863a7598cce8b\":[\"{\\\"uuid\\\":\\\"43436624dc714282913e0a141246629e\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"treeUuid\\\":\\\"1c0773fa98f4465b8e535ba3c00dc039\\\",\\\"parentUuid\\\":\\\"a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":true,\\\"size\\\":1,\\\"distance\\\":4,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\"}\",\"{\\\"uuid\\\":\\\"791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"treeUuid\\\":\\\"1c0773fa98f4465b8e535ba3c00dc039\\\",\\\"parentUuid\\\":\\\"a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":2,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\"}\",\"{\\\"uuid\\\":\\\"a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"treeUuid\\\":\\\"1c0773fa98f4465b8e535ba3c00dc039\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":1,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:51 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\"}\",\"{\\\"uuid\\\":\\\"a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"treeUuid\\\":\\\"1c0773fa98f4465b8e535ba3c00dc039\\\",\\\"parentUuid\\\":\\\"791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":3,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\"}\"]},\"volumeSnapshotGroupVO\":[\"{\\\"snapshotCount\\\":4,\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeSnapshotRefs\\\":[{\\\"volumeSnapshotUuid\\\":\\\"a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"69e85ac72fea4263a55cbcd21785006e\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"61e2ada0170142bb8b303910a27690aa\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}],\\\"uuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"resourceName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceType\\\":\\\"VolumeSnapshotGroupVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO\\\"}\",\"{\\\"snapshotCount\\\":4,\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeSnapshotRefs\\\":[{\\\"volumeSnapshotUuid\\\":\\\"b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"79caace79a1048d58ea7c0b38815bbd0\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/61e2ada0170142bb8b303910a27690aa\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"aefbe47465c047d1b118321c34425869\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/69e85ac72fea4263a55cbcd21785006e\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"}],\\\"uuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"resourceName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceType\\\":\\\"VolumeSnapshotGroupVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO\\\"}\",\"{\\\"snapshotCount\\\":4,\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeSnapshotRefs\\\":[{\\\"volumeSnapshotUuid\\\":\\\"1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/aefbe47465c047d1b118321c34425869\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/79caace79a1048d58ea7c0b38815bbd0\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}],\\\"uuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"resourceName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceType\\\":\\\"VolumeSnapshotGroupVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO\\\"}\",\"{\\\"snapshotCount\\\":4,\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeSnapshotRefs\\\":[{\\\"volumeSnapshotUuid\\\":\\\"43436624dc714282913e0a141246629e\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}],\\\"uuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"resourceName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceType\\\":\\\"VolumeSnapshotGroupVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO\\\"}\"],\"volumeSnapshotGroupRefVO\":[\"{\\\"volumeSnapshotUuid\\\":\\\"04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/79caace79a1048d58ea7c0b38815bbd0\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/aefbe47465c047d1b118321c34425869\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"43436624dc714282913e0a141246629e\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"61e2ada0170142bb8b303910a27690aa\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"69e85ac72fea4263a55cbcd21785006e\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"79caace79a1048d58ea7c0b38815bbd0\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/61e2ada0170142bb8b303910a27690aa\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"aefbe47465c047d1b118321c34425869\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/69e85ac72fea4263a55cbcd21785006e\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\"],\"volumeSnapshotReferenceVO\":{},\"volumeSnapshotReferenceTreeVO\":{},\"EncryptedResourceKeyRefVO\":{}}"
            return rsp
        }
    }
}

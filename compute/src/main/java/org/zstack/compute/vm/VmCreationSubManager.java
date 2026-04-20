package org.zstack.compute.vm;

import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.compute.allocator.HostAllocatorManager;
import org.zstack.configuration.DiskOfferingSystemTags;
import org.zstack.configuration.InstanceOfferingSystemTags;
import org.zstack.configuration.OfferingUserConfigUtils;
import org.zstack.core.Platform;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.*;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.core.jsonlabel.JsonLabel;
import org.zstack.core.thread.SingleFlightTask;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.core.workflow.ShareFlow;
import org.zstack.header.allocator.AllocateHostDryRunReply;
import org.zstack.header.allocator.DesignatedAllocateHostMsg;
import org.zstack.header.allocator.HostAllocatorConstant;
import org.zstack.header.cluster.ClusterInventory;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.configuration.*;
import org.zstack.header.configuration.userconfig.DiskOfferingUserConfig;
import org.zstack.header.configuration.userconfig.InstanceOfferingUserConfig;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.host.HostInventory;
import org.zstack.header.image.*;
import org.zstack.header.image.ImageConstant.ImageMediaType;
import org.zstack.header.message.APICreateMessage;
import org.zstack.header.message.MessageReply;
import org.zstack.header.network.l3.*;
import org.zstack.header.storage.backup.BackupStorageInventory;
import org.zstack.header.storage.backup.BackupStorageType;
import org.zstack.header.storage.backup.BackupStorageVO;
import org.zstack.header.storage.backup.BackupStorageVO_;
import org.zstack.header.storage.primary.*;
import org.zstack.header.tag.SystemTagCreator;
import org.zstack.header.vm.*;
import org.zstack.header.vm.VmInstanceConstant.VmOperation;
import org.zstack.header.vm.VmInstanceDeletionPolicyManager.VmInstanceDeletionPolicy;
import org.zstack.header.volume.VolumeType;
import org.zstack.header.zone.ZoneInventory;
import org.zstack.header.zone.ZoneVO;
import org.zstack.tag.TagManager;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.identity.AccountManager;
import org.zstack.utils.logging.CLogger;

import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.zstack.core.Platform.*;
import static org.zstack.utils.CollectionDSL.*;
import static org.zstack.utils.CollectionUtils.merge;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.*;

/**
 * Handles VM creation and candidate resource query API messages.
 * Extracted from VmInstanceManagerImpl to reduce God Class complexity.
 */
public class VmCreationSubManager {
    private static final CLogger logger = Utils.getLogger(VmCreationSubManager.class);

    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private TagManager tagMgr;
    @Autowired
    private HostAllocatorManager hostAllocatorMgr;
    @Autowired
    protected VmInstanceExtensionPointEmitter extEmitter;
    @Autowired
    private ThreadFacade thdf;
    @Autowired
    private VmFactoryManager vmFactoryManager;
    @Autowired
    private AccountManager acntMgr;

    void handle(final CreateVmInstanceMsg msg) {
        if (msg.getZoneUuid() == null && !CollectionUtils.isEmpty(msg.getL3NetworkSpecs())) {
            String l3Uuid = VmNicSpec.getL3UuidsOfSpec(msg.getL3NetworkSpecs()).get(0);
            String zoneUuid = Q.New(L3NetworkVO.class)
                    .select(L3NetworkVO_.zoneUuid)
                    .eq(L3NetworkVO_.uuid, l3Uuid)
                    .findValue();
            msg.setZoneUuid(zoneUuid);
        }

        doCreateVmInstance(msg, null, new ReturnValueCompletion<VmInstanceInventory>(msg) {
            @Override
            public void success(VmInstanceInventory inv) {
                CreateVmInstanceReply reply = new CreateVmInstanceReply();
                reply.setInventory(inv);
                bus.reply(msg, reply);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                CreateVmInstanceReply r = new CreateVmInstanceReply();
                r.setError(errorCode);
                bus.reply(msg, r);
            }
        });
    }

    void handle(final APICreateVmInstanceMsg msg) {
        doCreateVmInstance(VmInstanceUtils.fromAPICreateVmInstanceMsg(msg), msg, new ReturnValueCompletion<VmInstanceInventory>(msg) {
            APICreateVmInstanceEvent evt = new APICreateVmInstanceEvent(msg.getId());

            @Override
            public void success(VmInstanceInventory inv) {
                evt.setInventory(inv);
                bus.publish(evt);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                evt.setError(errorCode);
                bus.publish(evt);
            }
        });
    }

    void handle(final APIGetVmsCapabilitiesMsg msg) {
        APIGetVmsCapabilitiesReply reply = new APIGetVmsCapabilitiesReply();
        Map<String, VmCapabilities> vmsCaps = Maps.newConcurrentMap();
        msg.getVmUuids()
                .parallelStream()
                .forEach(v -> {
                    vmsCaps.put(v, new VmCapabilitiesJudger().judge(v));
                });

        reply.setVmsCaps(vmsCaps);
        bus.reply(msg, reply);
    }

    void handle(final APIUpdatePriorityConfigMsg msg) {
        final APIUpdatePriorityConfigEvent evt = new APIUpdatePriorityConfigEvent(msg.getId());

        VmPriorityOperator.PriorityStruct struct = new VmPriorityOperator.PriorityStruct();
        struct.setCpuShares(msg.getCpuShares());
        struct.setOomScoreAdj(msg.getOomScoreAdj());
        VmPriorityConfigVO vmPriorityConfigVO = new VmPriorityOperator().updatePriorityConfig(msg.getUuid(), struct);
        if (vmPriorityConfigVO != null) {
            for (UpdatePriorityConfigExtensionPoint exp : pluginRgty.getExtensionList(UpdatePriorityConfigExtensionPoint.class)) {
                exp.afterUpdatePriorityConfig(vmPriorityConfigVO);
            }
        }
        bus.publish(evt);
    }

    void handle(APIGetSpiceCertificatesMsg msg) {
        APIGetSpiceCertificatesReply reply = new APIGetSpiceCertificatesReply();
        String certificateStr = new JsonLabel().get("spiceCA", String.class);
        if (StringUtils.isNotEmpty(certificateStr)) {
            reply.setCertificateStr(certificateStr);
        } else {
            reply.setError(operr(ORG_ZSTACK_COMPUTE_VM_10232, "Spice certificate does not exist, Please check if spice tls is enabled"));
        }
        bus.reply(msg, reply);
    }

    @Transactional(readOnly = true)
    void handle(APIGetCandidateVmForAttachingIsoMsg msg) {
        APIGetCandidateVmForAttachingIsoReply reply = new APIGetCandidateVmForAttachingIsoReply();

        String sql = "select bs" +
                " from BackupStorageVO bs, ImageBackupStorageRefVO ref" +
                " where ref.imageUuid = :isoUuid" +
                " and bs.uuid = ref.backupStorageUuid";
        TypedQuery<BackupStorageVO> q = dbf.getEntityManager().createQuery(sql, BackupStorageVO.class);
        q.setParameter("isoUuid", msg.getIsoUuid());
        List<BackupStorageVO> bss = q.getResultList();
        if (bss.isEmpty()) {
            reply.setInventories(new ArrayList<>());
            bus.reply(msg, reply);
            return;
        }

        List<String> psUuids = new ArrayList<>();
        List<String> psTypes = new ArrayList<>();
        for (BackupStorageVO bs : bss) {
            BackupStorageType bsType = BackupStorageType.valueOf(bs.getType());
            List<String> lst = bsType.findRelatedPrimaryStorage(bs.getUuid());
            if (lst != null) {
                psUuids.addAll(lst);
            } else {
                psTypes.addAll(hostAllocatorMgr.getPrimaryStorageTypesByBackupStorageTypeFromMetrics(bs.getType()));
            }
        }

        List<VmInstanceVO> vms = new ArrayList<>();
        if (!psUuids.isEmpty()) {
            sql = "select vm" +
                    " from VmInstanceVO vm, VolumeVO vol" +
                    " where vol.type = :volType" +
                    " and vol.vmInstanceUuid = vm.uuid" +
                    " and vm.state in (:vmStates)" +
                    " and vol.primaryStorageUuid in (:psUuids)";
            TypedQuery<VmInstanceVO> vmq = dbf.getEntityManager().createQuery(sql, VmInstanceVO.class);
            vmq.setParameter("volType", VolumeType.Root);
            vmq.setParameter("vmStates", asList(VmInstanceState.Running, VmInstanceState.Stopped));
            vmq.setParameter("psUuids", psUuids);
            vms.addAll(vmq.getResultList());
        }

        if (!psTypes.isEmpty()) {
            sql = "select vm" +
                    " from VmInstanceVO vm, VolumeVO vol, PrimaryStorageVO ps" +
                    " where vol.type = :volType" +
                    " and vol.vmInstanceUuid = vm.uuid" +
                    " and vm.state in (:vmStates)" +
                    " and vol.primaryStorageUuid = ps.uuid" +
                    " and ps.type in (:psTypes)";
            TypedQuery<VmInstanceVO> vmq = dbf.getEntityManager().createQuery(sql, VmInstanceVO.class);
            vmq.setParameter("volType", VolumeType.Root);
            vmq.setParameter("vmStates", asList(VmInstanceState.Running, VmInstanceState.Stopped));
            vmq.setParameter("psTypes", psTypes);
            vms.addAll(vmq.getResultList());
        }

        List<VmInstanceInventory> result = VmInstanceInventory.valueOf(vms);

        for (VmAttachIsoExtensionPoint ext : pluginRgty.getExtensionList(VmAttachIsoExtensionPoint.class)) {
            ext.filtCandidateVms(msg.getIsoUuid(), result);
        }
        reply.setInventories(result);
        bus.reply(msg, reply);
    }

    void handle(APIGetInterdependentL3NetworksImagesMsg msg) {
        final String accountUuid = msg.getSession().getAccountUuid();
        if (msg.getImageUuid() != null) {
            thdf.singleFlightSubmit(new SingleFlightTask(msg)
                    .setSyncSignature(String.format("get-interdependent-l3-by-image-%s-in-zone-%s",
                            msg.getImageUuid(),
                            msg.getZoneUuid()))
                    .run((completion) -> completion.success(getInterdependentL3NetworksByImageUuid(msg, accountUuid)))
                    .done(((result) -> {
                        APIGetInterdependentL3NetworkImageReply reply = new APIGetInterdependentL3NetworkImageReply();
                        if (!result.isSuccess()) {
                            reply.setError(result.getErrorCode());
                        } else {
                            reply.setInventories((List<L3NetworkInventory>) result.getResult());
                        }

                        bus.reply(msg, reply);
                    })));
        } else if (msg.getL3NetworkUuids() != null) {
            getInterdependentImagesByL3NetworkUuids(msg);
        } else {
            thdf.singleFlightSubmit(new SingleFlightTask(msg)
                    .setSyncSignature(String.format("get-interdependent-l3-by-zone-%s",
                            msg.getZoneUuid()))
                    .run((completion) -> completion.success(getInterdependentL3NetworksByImageUuid(msg, accountUuid)))
                    .done(((result) -> {
                        APIGetInterdependentL3NetworkImageReply reply = new APIGetInterdependentL3NetworkImageReply();
                        if (!result.isSuccess()) {
                            reply.setError(result.getErrorCode());
                        } else {
                            reply.setInventories((List<L3NetworkInventory>) result.getResult());
                        }

                        bus.reply(msg, reply);
                    })));
        }
    }

    void handle(APIGetInterdependentL3NetworksBackupStoragesMsg msg) {
        final String accountUuid = msg.getSession().getAccountUuid();
        APIGetInterdependentL3NetworksBackupStoragesReply reply =
                new APIGetInterdependentL3NetworksBackupStoragesReply();
        if (msg.getBackupStorageUuid() != null) {
            BackupStorageVO bsvo = Q.New(BackupStorageVO.class)
                    .eq(BackupStorageVO_.uuid, msg.getBackupStorageUuid())
                    .find();
            if (bsvo == null) {
                reply.setInventories(new ArrayList<>());
            } else {
                reply.setInventories(getInterdependentL3NetworksByBackupStorageUuids(Collections.singletonList(bsvo),
                        msg.getZoneUuid(), accountUuid, false));
            }
        } else {
            reply.setInventories(getInterdependentBackupStoragesByL3NetworkUuids(msg.getL3NetworkUuids()));
        }

        bus.reply(msg, reply);
    }

    void handle(APIGetCandidateZonesClustersHostsForCreatingVmMsg msg) {
        DesignatedAllocateHostMsg amsg = new DesignatedAllocateHostMsg();

        ImageVO image = dbf.findByUuid(msg.getImageUuid(), ImageVO.class);
        amsg.setImage(ImageInventory.valueOf(image));
        amsg.setZoneUuid(msg.getZoneUuid());
        amsg.setClusterUuid(msg.getClusterUuid());

        InstanceOfferingVO insvo = null;
        if (msg.getInstanceOfferingUuid() == null) {
            amsg.setCpuCapacity(msg.getCpuNum());
            amsg.setMemoryCapacity(msg.getMemorySize());
        } else {
            insvo = dbf.findByUuid(msg.getInstanceOfferingUuid(), InstanceOfferingVO.class);
            amsg.setCpuCapacity(insvo.getCpuNum());
            amsg.setMemoryCapacity(insvo.getMemorySize());
        }

        long diskSize = 0;
        List<DiskOfferingInventory> diskOfferings = new ArrayList<>();
        if (msg.getDataDiskOfferingUuids() != null) {
            SimpleQuery<DiskOfferingVO> dq = dbf.createQuery(DiskOfferingVO.class);
            dq.add(DiskOfferingVO_.uuid, Op.IN, msg.getDataDiskOfferingUuids());
            List<DiskOfferingVO> dvos = dq.list();
            diskOfferings.addAll(DiskOfferingInventory.valueOf(dvos));
        }

        if (image.getMediaType() == ImageMediaType.ISO) {
            if (msg.getRootDiskOfferingUuid() == null) {
                diskSize = msg.getRootDiskSize();
            } else {
                DiskOfferingVO rootDiskOffering = dbf.findByUuid(msg.getRootDiskOfferingUuid(), DiskOfferingVO.class);
                diskOfferings.add(DiskOfferingInventory.valueOf(rootDiskOffering));
            }
        } else {
            diskSize = image.getSize();
        }

        diskSize += diskOfferings.stream().mapToLong(DiskOfferingInventory::getDiskSize).sum();
        amsg.setDiskSize(diskSize);
        amsg.setL3NetworkUuids(msg.getL3NetworkUuids());
        amsg.setVmOperation(VmOperation.NewCreate.toString());
        amsg.setDryRun(true);
        amsg.setListAllHosts(true);
        amsg.setAllocatorStrategy(HostAllocatorConstant.DESIGNATED_HOST_ALLOCATOR_STRATEGY_TYPE);

        if (image.getBackupStorageRefs().size() == 1) {
            amsg.setRequiredBackupStorageUuid(image.getBackupStorageRefs().iterator().next().getBackupStorageUuid());
        } else {
            if (msg.getZoneUuid() == null) {
                throw new OperationFailureException(argerr(ORG_ZSTACK_COMPUTE_VM_10237, "zoneUuid must be set because the image[name:%s, uuid:%s] is on multiple backup storage",
                        image.getName(), image.getUuid()));
            }

            ImageBackupStorageSelector selector = new ImageBackupStorageSelector();
            selector.setZoneUuid(msg.getZoneUuid());
            selector.setImageUuid(image.getUuid());
            amsg.setRequiredBackupStorageUuid(selector.select());
        }

        VmInstanceInventory vm = new VmInstanceInventory();
        vm.setUuid(Platform.FAKE_UUID);
        vm.setImageUuid(image.getUuid());
        if (insvo == null) {
            vm.setCpuNum(msg.getCpuNum());
            vm.setMemorySize(msg.getMemorySize());
        } else {
            vm.setInstanceOfferingUuid(insvo.getUuid());
            vm.setCpuNum(insvo.getCpuNum());
            vm.setMemorySize(insvo.getMemorySize());
        }
        vm.setDefaultL3NetworkUuid(msg.getDefaultL3NetworkUuid() == null ? msg.getL3NetworkUuids().get(0) : msg.getDefaultL3NetworkUuid());
        vm.setName("for-getting-candidates-zones-clusters-hosts");
        amsg.setVmInstance(vm);
        if (msg.getSystemTags() != null && !msg.getSystemTags().isEmpty()) {
            amsg.setSystemTags(new ArrayList<String>(msg.getSystemTags()));
        }

        APIGetCandidateZonesClustersHostsForCreatingVmReply areply = new APIGetCandidateZonesClustersHostsForCreatingVmReply();
        bus.makeLocalServiceId(amsg, HostAllocatorConstant.SERVICE_ID);
        bus.send(amsg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    areply.setError(reply.getError());
                } else {
                    AllocateHostDryRunReply re = reply.castReply();

                    if (!re.getHosts().isEmpty()) {
                        areply.setHosts(re.getHosts());

                        Set<String> clusterUuids = re.getHosts().stream().
                                map(HostInventory::getClusterUuid).collect(Collectors.toSet());
                        List<ClusterInventory> clusters = ClusterInventory.valueOf(dbf.listByPrimaryKeys(clusterUuids, ClusterVO.class));
                        areply.setClusters(clusters);

                        Set<String> zoneUuids = clusters.stream().
                                map(ClusterInventory::getZoneUuid).collect(Collectors.toSet());
                        areply.setZones(ZoneInventory.valueOf(dbf.listByPrimaryKeys(zoneUuids, ZoneVO.class)));
                    } else {
                        areply.setHosts(new ArrayList<>());
                        areply.setClusters(new ArrayList<>());
                        areply.setZones(new ArrayList<>());
                    }
                }

                bus.reply(msg, areply);
            }
        });
    }

    void handle(APIGetCandidatePrimaryStoragesForCreatingVmMsg msg) {
        APIGetCandidatePrimaryStoragesForCreatingVmReply reply = new APIGetCandidatePrimaryStoragesForCreatingVmReply();
        List<AllocatePrimaryStorageMsg> msgs = new ArrayList<>();

        Set<String> psTypes = new HashSet<>();
        List<String> clusterUuids = new ArrayList<>();
        List<DiskOfferingInventory> dataOfferings = new ArrayList<>();
        ImageInventory imageInv = new SQLBatchWithReturn<ImageInventory>() {

            @Override
            protected ImageInventory scripts() {
                List<String> dataOfferingUuids = msg.getDataDiskOfferingUuids() == null ? new ArrayList<>() :
                        msg.getDataDiskOfferingUuids();

                sql("select bs.type from BackupStorageVO bs, ImageBackupStorageRefVO ref" +
                        " where ref.imageUuid =:imageUuid" +
                        " and bs.uuid = ref.backupStorageUuid", String.class)
                        .param("imageUuid", msg.getImageUuid())
                        .list().forEach(it ->
                                psTypes.addAll(hostAllocatorMgr.getPrimaryStorageTypesByBackupStorageTypeFromMetrics((String) it)
                                ));

                clusterUuids.addAll(sql("select distinct ref.clusterUuid" +
                        " from L2NetworkClusterRefVO ref, L3NetworkVO l3" +
                        " where l3.uuid in (:l3Uuids)" +
                        " and ref.l2NetworkUuid = l3.l2NetworkUuid", String.class)
                        .param("l3Uuids", msg.getL3NetworkUuids())
                        .list());

                for (String diskUuid : dataOfferingUuids) {
                    dataOfferings.add(DiskOfferingInventory.valueOf(
                            (DiskOfferingVO) q(DiskOfferingVO.class)
                                    .eq(DiskOfferingVO_.uuid, diskUuid)
                                    .find()
                    ));
                }

                ImageVO imageVO = q(ImageVO.class).eq(ImageVO_.uuid, msg.getImageUuid()).find();
                return ImageInventory.valueOf(imageVO);
            }
        }.execute();

        // allocate ps for root volume
        AllocatePrimaryStorageMsg rmsg = new AllocatePrimaryStorageMsg();
        rmsg.setDryRun(true);
        rmsg.setImageUuid(msg.getImageUuid());
        rmsg.setRequiredClusterUuids(clusterUuids);
        if (ImageMediaType.ISO.toString().equals(imageInv.getMediaType())) {
            if (msg.getRootDiskOfferingUuid() == null) {
                rmsg.setSize(msg.getRootDiskSize());
            } else {
                Tuple t = Q.New(DiskOfferingVO.class).eq(DiskOfferingVO_.uuid, msg.getRootDiskOfferingUuid())
                        .select(DiskOfferingVO_.diskSize, DiskOfferingVO_.allocatorStrategy).findTuple();

                rmsg.setSize((long) t.get(0));
                rmsg.setAllocationStrategy((String) t.get(1));
                rmsg.setDiskOfferingUuid(msg.getRootDiskOfferingUuid());
            }
        } else {
            rmsg.setSize(imageInv.getSize());
        }

        if (msg.getRootDiskOfferingUuid() != null && DiskOfferingSystemTags.DISK_OFFERING_USER_CONFIG.hasTag(msg.getRootDiskOfferingUuid())) {
            DiskOfferingUserConfig config = OfferingUserConfigUtils.getDiskOfferingConfig(msg.getRootDiskOfferingUuid(), DiskOfferingUserConfig.class);
            if (config.getAllocate() != null && config.getAllocate().getPrimaryStorage() != null) {
                String psUuid = config.getAllocate().getPrimaryStorage().getUuid();
                rmsg.setRequiredPrimaryStorageUuid(psUuid);
            }
        }

        if (msg.getInstanceOfferingUuid() != null && InstanceOfferingSystemTags.INSTANCE_OFFERING_USER_CONFIG.hasTag(msg.getInstanceOfferingUuid())) {
            InstanceOfferingUserConfig config = OfferingUserConfigUtils.getInstanceOfferingConfig(msg.getInstanceOfferingUuid(), InstanceOfferingUserConfig.class);
            if (config.getAllocate() != null && config.getAllocate().getPrimaryStorage() != null) {
                String psUuid = config.getAllocate().getPrimaryStorage().getUuid();
                rmsg.setRequiredPrimaryStorageUuid(psUuid);
            }
        }

        rmsg.setPurpose(PrimaryStorageAllocationPurpose.CreateNewVm.toString());
        rmsg.setPossiblePrimaryStorageTypes(new ArrayList<>(psTypes));
        bus.makeLocalServiceId(rmsg, PrimaryStorageConstant.SERVICE_ID);
        msgs.add(rmsg);

        // allocate ps for data volumes
        for (DiskOfferingInventory dinv : dataOfferings) {
            AllocatePrimaryStorageMsg amsg = new AllocatePrimaryStorageMsg();
            amsg.setDryRun(true);
            amsg.setSize(dinv.getDiskSize());
            amsg.setRequiredClusterUuids(clusterUuids);
            amsg.setAllocationStrategy(dinv.getAllocatorStrategy());
            amsg.setDiskOfferingUuid(dinv.getUuid());
            if (DiskOfferingSystemTags.DISK_OFFERING_USER_CONFIG.hasTag(dinv.getUuid())) {
                DiskOfferingUserConfig config = OfferingUserConfigUtils.getDiskOfferingConfig(dinv.getUuid(), DiskOfferingUserConfig.class);
                if (config.getAllocate() != null && config.getAllocate().getPrimaryStorage() != null) {
                    String psUuid = config.getAllocate().getPrimaryStorage().getUuid();
                    amsg.setRequiredPrimaryStorageUuid(psUuid);
                }
            }

            bus.makeLocalServiceId(amsg, PrimaryStorageConstant.SERVICE_ID);
            msgs.add(amsg);
        }

        if (msg.getDataDiskSizes() != null) {
            for (Long size : msg.getDataDiskSizes()) {
                AllocatePrimaryStorageMsg amsg = new AllocatePrimaryStorageMsg();
                amsg.setDryRun(true);
                amsg.setSize(size);
                amsg.setRequiredClusterUuids(clusterUuids);
                bus.makeLocalServiceId(amsg, PrimaryStorageConstant.SERVICE_ID);
                msgs.add(amsg);
            }
        }

        new While<>(msgs).all((amsg, completion) -> {
            bus.send(amsg, new CloudBusCallBack(completion) {
                @Override
                public void run(MessageReply r) {
                    if (r.isSuccess()) {
                        AllocatePrimaryStorageDryRunReply re = r.castReply();
                        if (amsg.getImageUuid() != null) {
                            reply.setRootVolumePrimaryStorages(re.getPrimaryStorageInventories());
                        } else if (amsg.getDiskOfferingUuid() != null) {
                            reply.getDataVolumePrimaryStorages().put(amsg.getDiskOfferingUuid(), re.getPrimaryStorageInventories());
                        } else {
                            reply.getDataVolumePrimaryStorages().put(String.valueOf(amsg.getSize()), re.getPrimaryStorageInventories());
                        }
                    }
                    completion.done();
                }
            });

        }).run(new WhileDoneCompletion(msg) {
            @Override
            public void done(ErrorCodeList errorCodeList) {
                bus.reply(msg, reply);
            }
        });
    }

    // --- Private helper methods ---

    private List<BackupStorageVO> listIntersection(List<BackupStorageVO> a, List<BackupStorageVO> b) {
        List<BackupStorageVO> ret = new ArrayList<>();
        for (BackupStorageVO s : a) {
            if (b.stream().filter(it -> it.getUuid().equals(s.getUuid())).findAny().isPresent()) {
                ret.add(s);
            }
        }

        return ret;
    }

    @Transactional(readOnly = true)
    private List<BackupStorageInventory> getInterdependentBackupStoragesByL3NetworkUuids(List<String> l3s) {
        List<List<BackupStorageVO>> bss = new ArrayList<>();
        for (String l3uuid : l3s) {
            String sql = "select ps" +
                    " from PrimaryStorageVO ps, L2NetworkClusterRefVO l2ref," +
                    " L3NetworkVO l3, PrimaryStorageClusterRefVO psref" +
                    " where ps.uuid = psref.primaryStorageUuid" +
                    " and psref.clusterUuid = l2ref.clusterUuid" +
                    " and l2ref.l2NetworkUuid = l3.l2NetworkUuid" +
                    " and l3.uuid = :l3uuid";
            TypedQuery<PrimaryStorageVO> psq = dbf.getEntityManager().createQuery(sql, PrimaryStorageVO.class);
            psq.setParameter("l3uuid", l3uuid);
            List<PrimaryStorageVO> pss = psq.getResultList();

            List<BackupStorageVO> lst = new ArrayList<>();
            for (PrimaryStorageVO ps : pss) {
                PrimaryStorageType psType = PrimaryStorageType.valueOf(ps.getType());
                List<String> bsUuids = psType.findBackupStorage(ps.getUuid());

                if (!bsUuids.isEmpty()) {
                    // the primary storage has bound backup storage, e.g. ceph
                    sql = "select bs from BackupStorageVO bs where bs.uuid in (:uuids)";
                    TypedQuery<BackupStorageVO> bq = dbf.getEntityManager().createQuery(sql, BackupStorageVO.class);
                    bq.setParameter("uuids", bsUuids);
                    lst.addAll(bq.getResultList());
                } else {
                    logger.warn(String.format("the primary storage[uuid:%s, type:%s] needs a bound backup storage," +
                            " but seems it's not added", ps.getUuid(), ps.getType()));
                }
            }

            bss.add(lst);
        }

        List<BackupStorageVO> selectedBss = new ArrayList<>();
        for (List<BackupStorageVO> lst : bss) {
            selectedBss.addAll(lst);
        }

        for (List<BackupStorageVO> l : bss) {
            selectedBss = listIntersection(selectedBss, l);
        }

        return BackupStorageInventory.valueOf(selectedBss);
    }

    @Transactional(readOnly = true)
    private void getInterdependentImagesByL3NetworkUuids(APIGetInterdependentL3NetworksImagesMsg msg) {
        APIGetInterdependentL3NetworkImageReply reply = new APIGetInterdependentL3NetworkImageReply();

        List<BackupStorageInventory> bss = getInterdependentBackupStoragesByL3NetworkUuids(msg.getL3NetworkUuids());

        if (bss.isEmpty()) {
            reply.setInventories(new ArrayList<>());
            bus.reply(msg, reply);
            return;
        }

        List<String> bsUuids = bss.stream().map(BackupStorageInventory::getUuid).collect(Collectors.toList());
        String sql = "select img" +
                " from ImageVO img, ImageBackupStorageRefVO iref, BackupStorageZoneRefVO zref, BackupStorageVO bs" +
                " where img.uuid = iref.imageUuid" +
                " and iref.backupStorageUuid = zref.backupStorageUuid" +
                " and bs.uuid = zref.backupStorageUuid" +
                " and bs.uuid in (:uuids)" +
                " and zref.zoneUuid = :zoneUuid" +
                " group by img.uuid";
        TypedQuery<ImageVO> iq = dbf.getEntityManager().createQuery(sql, ImageVO.class);
        iq.setParameter("uuids", bsUuids);
        iq.setParameter("zoneUuid", msg.getZoneUuid());
        List<ImageVO> vos = iq.getResultList();
        reply.setInventories(ImageInventory.valueOf(vos));
        bus.reply(msg, reply);
    }

    @Transactional(readOnly = true)
    private List<L3NetworkInventory> getInterdependentL3NetworksByImageUuid(APIGetInterdependentL3NetworksImagesMsg msg, String accountUuid) {
        List<BackupStorageVO> bss = null;
        if (msg.getImageUuid() != null) {
            String sql = "select bs" +
                    " from BackupStorageVO bs, ImageBackupStorageRefVO ref, BackupStorageZoneRefVO zref" +
                    " where bs.uuid = ref.backupStorageUuid" +
                    " and ref.imageUuid = :imgUuid" +
                    " and ref.backupStorageUuid = zref.backupStorageUuid" +
                    " and zref.zoneUuid = :zoneUuid";
            TypedQuery<BackupStorageVO> bsq = dbf.getEntityManager().createQuery(sql, BackupStorageVO.class);
            bsq.setParameter("imgUuid", msg.getImageUuid());
            bsq.setParameter("zoneUuid", msg.getZoneUuid());
            bss = bsq.getResultList();
        } else {
            String sql = "select bs" +
                    " from BackupStorageVO bs, BackupStorageZoneRefVO zref" +
                    " where bs.uuid = zref.backupStorageUuid" +
                    " and zref.zoneUuid = :zoneUuid";
            TypedQuery<BackupStorageVO> bsq = dbf.getEntityManager().createQuery(sql, BackupStorageVO.class);
            bsq.setParameter("zoneUuid", msg.getZoneUuid());
            bss = bsq.getResultList();
        }

        if (bss.isEmpty()) {
            throw new OperationFailureException(argerr(ORG_ZSTACK_COMPUTE_VM_10233, "the image[uuid:%s] is not on any backup storage that has been attached to the zone[uuid:%s]",
                    msg.getImageUuid(), msg.getZoneUuid()));
        }

        List<L3NetworkInventory> l3s = getInterdependentL3NetworksByBackupStorageUuids(bss, msg.getZoneUuid(), accountUuid, msg.getRaiseException());

        List<String> bsUuids = bss.stream().map(BackupStorageVO::getUuid).collect(Collectors.toList());
        for (GetInterdependentL3NetworksExtensionPoint ext : pluginRgty.getExtensionList(GetInterdependentL3NetworksExtensionPoint.class)) {
            l3s = ext.afterFilterByImage(l3s, bsUuids, msg.getImageUuid());
        }

        return l3s;
    }

    @Transactional(readOnly = true)
    private List<L3NetworkInventory> getInterdependentL3NetworksByBackupStorageUuids(List<BackupStorageVO> bss, String zoneUuid, String accountUuid, boolean raiseException) {
        List<String> psUuids = new ArrayList<>();
        List<L3NetworkVO> l3s = new ArrayList<>();
        for (BackupStorageVO bs : bss) {
            BackupStorageType bsType = BackupStorageType.valueOf(bs.getType());
            List<String> relatedPrimaryStorageUuids = bsType.findRelatedPrimaryStorage(bs.getUuid());
            if (relatedPrimaryStorageUuids == null) {
                List<String> psTypes = hostAllocatorMgr.getPrimaryStorageTypesByBackupStorageTypeFromMetrics(bs.getType());
                psUuids.addAll(Q.New(PrimaryStorageVO.class)
                        .select(PrimaryStorageVO_.uuid)
                        .in(PrimaryStorageVO_.type, psTypes)
                        .eq(PrimaryStorageVO_.zoneUuid, zoneUuid)
                        .listValues());
                l3s.addAll(SQL.New("select l3" +
                                " from L3NetworkVO l3, L2NetworkClusterRefVO l2ref," +
                                " PrimaryStorageClusterRefVO psref, PrimaryStorageVO ps" +
                                " where l3.l2NetworkUuid = l2ref.l2NetworkUuid" +
                                " and l2ref.clusterUuid = psref.clusterUuid" +
                                " and psref.primaryStorageUuid = ps.uuid" +
                                " and ps.type in (:psTypes)" +
                                " and ps.zoneUuid = l3.zoneUuid" +
                                " and l3.zoneUuid = :zoneUuid" +
                                " group by l3.uuid")
                        .param("psTypes", psTypes)
                        .param("zoneUuid", zoneUuid)
                        .list());
            } else if (!relatedPrimaryStorageUuids.isEmpty()) {
                psUuids.addAll(Q.New(PrimaryStorageVO.class)
                        .select(PrimaryStorageVO_.uuid)
                        .in(PrimaryStorageVO_.uuid, relatedPrimaryStorageUuids)
                        .eq(PrimaryStorageVO_.zoneUuid, zoneUuid)
                        .listValues());
                l3s.addAll(SQL.New("select l3" +
                                " from L3NetworkVO l3, L2NetworkClusterRefVO l2ref," +
                                " PrimaryStorageClusterRefVO psref, PrimaryStorageVO ps" +
                                " where l3.l2NetworkUuid = l2ref.l2NetworkUuid" +
                                " and l2ref.clusterUuid = psref.clusterUuid" +
                                " and psref.primaryStorageUuid = ps.uuid" +
                                " and ps.uuid in (:psUuids)" +
                                " and ps.zoneUuid = l3.zoneUuid" +
                                " and l3.zoneUuid = :zoneUuid" +
                                " group by l3.uuid")
                        .param("psUuids", relatedPrimaryStorageUuids)
                        .param("zoneUuid", zoneUuid)
                        .list());
            } else {
                logger.warn(String.format("the backup storage[uuid:%s, type: %s] needs a strongly-bound primary storage," +
                        " but seems the primary storage is not added", bs.getUuid(), bs.getType()));
            }
        }

        if (l3s.isEmpty()) {
            if (psUuids.isEmpty()) {
                if (raiseException) {
                    throw new OperationFailureException(argerr(ORG_ZSTACK_COMPUTE_VM_10234, "no primary storage accessible to the backup storage[uuid:%s, type:%s] is found",
                            bss.get(0).getUuid(), bss.get(0).getType()));
                }
                logger.warn(String.format("no primary storage accessible to the backup storage[uuid:%s, type:%s] is found",
                        bss.get(0).getUuid(), bss.get(0).getType()));
                return new ArrayList<>();
            }

            Long clusterNum = SQL.New("select count(distinct cl)" +
                            " from ClusterVO cl, PrimaryStorageClusterRefVO psref, PrimaryStorageVO ps" +
                            " where cl.uuid = psref.clusterUuid" +
                            " and psref.primaryStorageUuid in (:psUuids)" +
                            " and ps.zoneUuid = cl.zoneUuid" +
                            " and cl.zoneUuid = :zoneUuid" +
                            " group by cl.uuid", Long.class)
                    .param("psUuids", psUuids)
                    .param("zoneUuid", zoneUuid)
                    .find();

            if (clusterNum == null || clusterNum == 0) {
                if (raiseException) {
                    throw new OperationFailureException(argerr(ORG_ZSTACK_COMPUTE_VM_10235, "the primary storages[uuids:%s] has not attached any cluster on the zone[uuid:%s]",
                            psUuids, zoneUuid));
                }
                logger.warn(String.format("the primary storages[uuids:%s] has not attached any cluster on the zone[uuid:%s]", psUuids, zoneUuid));
                return new ArrayList<>();
            }

            Long l2Num = SQL.New("select count(distinct l2)" +
                            " from L2NetworkVO l2, L2NetworkClusterRefVO l2ref, PrimaryStorageClusterRefVO psref, PrimaryStorageVO ps" +
                            " where l2.uuid = l2ref.l2NetworkUuid" +
                            " and psref.primaryStorageUuid in (:psUuids)" +
                            " and l2ref.clusterUuid = psref.clusterUuid" +
                            " and ps.zoneUuid = l2.zoneUuid" +
                            " and l2.zoneUuid = :zoneUuid", Long.class)
                    .param("psUuids", psUuids)
                    .param("zoneUuid", zoneUuid)
                    .find();
            if (l2Num == null || l2Num == 0) {
                if (raiseException) {
                    throw new OperationFailureException(argerr(ORG_ZSTACK_COMPUTE_VM_10236, "no l2Networks found in clusters that have attached to primary storages[uuids:%s]",
                            psUuids));
                }
                logger.warn(String.format("no l2Networks found in clusters that have attached to primary storages[uuids:%s]", psUuids));
                return new ArrayList<>();
            }
        }

        List<String> l3UuidListOfCurrentAccount;
        if (!org.zstack.header.identity.AccountConstant.isAdminPermission(accountUuid)) {
            l3UuidListOfCurrentAccount = acntMgr.getResourceUuidsCanAccessByAccount(accountUuid, L3NetworkVO.class);
        } else {
            l3UuidListOfCurrentAccount = null;
        }

        if (l3UuidListOfCurrentAccount == null) {
            return L3NetworkInventory.valueOf(l3s);
        }
        return L3NetworkInventory.valueOf(l3s.stream()
                .filter(vo -> l3UuidListOfCurrentAccount.contains(vo.getUuid()))
                .collect(Collectors.toList()));
    }

    protected void doCreateVmInstance(final CreateVmInstanceMsg msg, final APICreateMessage cmsg, ReturnValueCompletion<VmInstanceInventory> completion) {
        pluginRgty.getExtensionList(VmInstanceCreateExtensionPoint.class).forEach(extensionPoint -> {
            extensionPoint.preCreateVmInstance(msg);
        });

        final ImageVO image = Q.New(ImageVO.class).eq(ImageVO_.uuid, msg.getImageUuid()).find();
        VmInstanceVO vo = new VmInstanceVO();
        if (msg.getResourceUuid() != null) {
            vo.setUuid(msg.getResourceUuid());
        } else {
            vo.setUuid(Platform.getUuid());
        }
        vo.setName(msg.getName());
        vo.setClusterUuid(msg.getClusterUuid());
        vo.setDescription(msg.getDescription());
        vo.setImageUuid(msg.getImageUuid());
        vo.setInstanceOfferingUuid(msg.getInstanceOfferingUuid());
        vo.setState(VmInstanceState.Created);
        vo.setZoneUuid(msg.getZoneUuid());
        vo.setInternalId(dbf.generateSequenceNumber(VmInstanceSequenceNumberVO.class));
        vo.setDefaultL3NetworkUuid(msg.getDefaultL3NetworkUuid());
        vo.setCpuNum(msg.getCpuNum());
        vo.setCpuSpeed(msg.getCpuSpeed());
        vo.setMemorySize(msg.getMemorySize());
        vo.setReservedMemorySize(msg.getReservedMemorySize());
        vo.setAllocatorStrategy(msg.getAllocatorStrategy());
        vo.setPlatform(msg.getPlatform() != null ? msg.getPlatform() : image.getPlatform().toString());
        vo.setGuestOsType(msg.getGuestOsType() != null ? msg.getGuestOsType() : image.getGuestOsType());
        vo.setArchitecture(msg.getArchitecture() != null ? msg.getArchitecture() : image.getArchitecture());
        String vmType = msg.getType() == null ? VmInstanceConstant.USER_VM_TYPE : msg.getType();
        VmInstanceType type = VmInstanceType.valueOf(vmType);
        VmInstanceFactory factory = vmFactoryManager.getVmInstanceFactory(type.toString());

        VmInstanceVO finalVo = vo;
        vo = new SQLBatchWithReturn<VmInstanceVO>() {
            @Override
            protected VmInstanceVO scripts() {
                finalVo.setAccountUuid(msg.getAccountUuid());
                factory.createVmInstance(finalVo, msg);

                return reload(finalVo);
            }
        }.execute();

        FlowChain chain = FlowChainBuilder.newShareFlowChain();
        chain.setName(String.format("do-create-vmInstance-%s", vo.getUuid()));
        chain.then(new ShareFlow() {
            VmInstanceInventory instantiateVm;
            List<APICreateVmInstanceMsg.DiskAO> otherDisks = new ArrayList<>();
            boolean attachOtherDisk = false;

            @Override
            public void setup() {
                if (!CollectionUtils.isEmpty(msg.getDiskAOs())) {
                    otherDisks = msg.getDiskAOs().stream().filter(diskAO -> !diskAO.isBoot()).collect(Collectors.toList());
                    setDiskAOsName(otherDisks);
                    attachOtherDisk = !otherDisks.isEmpty();
                }

                flow(new Flow() {
                    List<ErrorCode> errorCodes = new ArrayList<>();
                    String __name__ = String.format("instantiate-systemTag-for-vm-%s", finalVo.getUuid());

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        try {
                            instantiateTagsForCreateMessage(msg, cmsg, finalVo);
                        } catch (Exception e) {
                            errorCodes.add(operr(ORG_ZSTACK_COMPUTE_VM_10240, "instantiate system tag for vm failed because %s", e.getMessage()));
                        }
                        if (!errorCodes.isEmpty()) {
                            trigger.fail(errorCodes.get(0));
                            return;
                        }

                        errorCodes = extEmitterHandleSystemTag(msg, cmsg, finalVo);
                        if (!errorCodes.isEmpty()) {
                            trigger.fail(operr(ORG_ZSTACK_COMPUTE_VM_10241, "handle system tag fail when creating vm because [%s]",
                                    StringUtils.join(errorCodes.stream().map(ErrorCode::getDescription).collect(Collectors.toList()),
                                            ", ")));
                            return;
                        }
                        trigger.next();
                    }

                    @Override
                    public void rollback(FlowRollback trigger, Map data) {
                        if (Q.New(VmInstanceVO.class).eq(VmInstanceVO_.uuid, finalVo.getUuid()).isExists()) {
                            dbf.removeByPrimaryKey(finalVo.getUuid(), VmInstanceVO.class);
                        }
                        trigger.rollback();
                    }
                });

                flow(new Flow() {
                    List<ErrorCode> errorCodes = Collections.emptyList();
                    String __name__ = String.format("instantiate-ssh-key-pair-for-vm-%s", finalVo.getUuid());

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        errorCodes = extEmitterHandleSshKeyPair(msg, cmsg, finalVo);
                        if (!errorCodes.isEmpty()) {
                            trigger.fail(operr(ORG_ZSTACK_COMPUTE_VM_10242, "handle sshkeypair fail when creating vm because [%s]",
                                    StringUtils.join(errorCodes.stream().map(ErrorCode::getDetails).collect(Collectors.toList()),
                                            ", ")));
                            return;
                        }
                        trigger.next();
                    }

                    @Override
                    public void rollback(FlowRollback trigger, Map data) {
                        if (Q.New(VmInstanceVO.class).eq(VmInstanceVO_.uuid, finalVo.getUuid()).isExists()) {
                            dbf.removeByPrimaryKey(finalVo.getUuid(), VmInstanceVO.class);
                        }
                        trigger.rollback();
                    }
                });

                flow(new Flow() {
                    String __name__ = "instantiate-new-created-vmInstance";

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        InstantiateNewCreatedVmInstanceMsg smsg = new InstantiateNewCreatedVmInstanceMsg();
                        smsg.setDisableL3Networks(msg.getDisableL3Networks());
                        smsg.setHostUuid(msg.getHostUuid());
                        List<String> temporaryDiskOfferingUuids = createDiskOfferingUuidsFromDataDiskSizes(msg, finalVo.getUuid());
                        smsg.setDataDiskOfferingUuids(merge(msg.getDataDiskOfferingUuids(), temporaryDiskOfferingUuids));
                        smsg.setDataVolumeTemplateUuids(msg.getDataVolumeTemplateUuids());
                        smsg.setDataVolumeFromTemplateSystemTags(msg.getDataVolumeFromTemplateSystemTags());
                        smsg.setL3NetworkUuids(msg.getL3NetworkSpecs());

                        if (msg.getRootDiskOfferingUuid() != null) {
                            smsg.setRootDiskOfferingUuid(msg.getRootDiskOfferingUuid());
                        } else if (msg.getRootDiskSize() > 0) {
                            DiskOfferingVO dvo = getDiskOfferingVO();
                            dbf.persist(dvo);
                            smsg.setRootDiskOfferingUuid(dvo.getUuid());
                            temporaryDiskOfferingUuids.add(dvo.getUuid());
                        }

                        smsg.setVmInstanceInventory(VmInstanceInventory.valueOf(finalVo));
                        smsg.setCandidatePrimaryStorageUuidsForDataVolume(msg.getCandidatePrimaryStorageUuidsForDataVolume());
                        smsg.setCandidatePrimaryStorageUuidsForRootVolume(msg.getCandidatePrimaryStorageUuidsForRootVolume());
                        if (Objects.equals(msg.getStrategy(), VmCreationStrategy.InstantStart.toString()) && attachOtherDisk) {
                            smsg.setStrategy(VmCreationStrategy.CreateStopped.toString());
                        } else {
                            smsg.setStrategy(msg.getStrategy());
                        }

                        smsg.setTimeout(msg.getTimeout());
                        smsg.setRootVolumeSystemTags(msg.getRootVolumeSystemTags());
                        smsg.setDataVolumeSystemTags(msg.getDataVolumeSystemTags());
                        smsg.setDataVolumeSystemTagsOnIndex(msg.getDataVolumeSystemTagsOnIndex());
                        smsg.setDiskAOs(msg.getDiskAOs());
                        bus.makeTargetServiceIdByResourceUuid(smsg, VmInstanceConstant.SERVICE_ID, finalVo.getUuid());
                        bus.send(smsg, new CloudBusCallBack(smsg) {
                            @Override
                            public void run(MessageReply reply) {
                                if (!temporaryDiskOfferingUuids.isEmpty()) {
                                    dbf.removeByPrimaryKeys(temporaryDiskOfferingUuids, DiskOfferingVO.class);
                                }

                                if (reply.isSuccess()) {
                                    InstantiateNewCreatedVmInstanceReply r = (InstantiateNewCreatedVmInstanceReply) reply;
                                    instantiateVm = r.getVmInventory();
                                    data.put(VmInstanceInventory.class.getSimpleName(), instantiateVm);
                                    trigger.next();
                                    return;
                                }
                                trigger.fail(reply.getError());
                            }
                        });
                    }

                    @NotNull
                    private DiskOfferingVO getDiskOfferingVO() {
                        DiskOfferingVO dvo = new DiskOfferingVO();
                        dvo.setUuid(Platform.getUuid());
                        dvo.setAccountUuid(msg.getAccountUuid());
                        dvo.setDiskSize(msg.getRootDiskSize());
                        dvo.setName("for-create-vm-" + finalVo.getUuid());
                        dvo.setType("TemporaryDiskOfferingType");
                        dvo.setState(DiskOfferingState.Enabled);
                        dvo.setAllocatorStrategy(PrimaryStorageConstant.DEFAULT_PRIMARY_STORAGE_ALLOCATION_STRATEGY_TYPE);
                        return dvo;
                    }

                    @Override
                    public void rollback(FlowRollback chain, Map data) {
                        if (instantiateVm == null) {
                            chain.rollback();
                            return;
                        }
                        DestroyVmInstanceMsg dmsg = new DestroyVmInstanceMsg();
                        dmsg.setVmInstanceUuid(finalVo.getUuid());
                        dmsg.setDeletionPolicy(VmInstanceDeletionPolicy.Direct);
                        bus.makeTargetServiceIdByResourceUuid(dmsg, VmInstanceConstant.SERVICE_ID, finalVo.getUuid());
                        bus.send(dmsg, new CloudBusCallBack(null) {
                            @Override
                            public void run(MessageReply reply) {
                                if (!reply.isSuccess()) {
                                    logger.warn(String.format("failed to delete vm [%s]", instantiateVm.getUuid()));
                                }
                                chain.rollback();
                            }
                        });
                    }
                });


                if (!CollectionUtils.isEmpty(otherDisks)) {
                    otherDisks.forEach(diskAO -> flow(new VmInstantiateOtherDiskFlow(diskAO)));
                }

                if (Objects.equals(msg.getStrategy(), VmCreationStrategy.InstantStart.toString()) && attachOtherDisk) {
                    // DEBT: NoRollbackFlow -- start VM after attaching other disks during creation. Tracked: TODO
                    flow(new NoRollbackFlow() {
                        String __name__ = "start-vm";

                        @Override
                        public void run(FlowTrigger trigger, Map data) {
                            StartVmInstanceMsg smsg = new StartVmInstanceMsg();
                            smsg.setVmInstanceUuid(instantiateVm.getUuid());
                            smsg.setHostUuid(instantiateVm.getLastHostUuid());
                            bus.makeTargetServiceIdByResourceUuid(smsg, VmInstanceConstant.SERVICE_ID, finalVo.getUuid());
                            bus.send(smsg, new CloudBusCallBack(trigger) {
                                @Override
                                public void run(MessageReply reply) {
                                    if (!reply.isSuccess()) {
                                        trigger.fail(reply.getError());
                                        return;
                                    }
                                    trigger.next();
                                }
                            });
                        }
                    });
                }

                done(new FlowDoneHandler(completion) {
                    @Override
                    public void handle(Map data) {
                        completion.success(instantiateVm);
                    }
                });

                error(new FlowErrorHandler(completion) {
                    @Override
                    public void handle(ErrorCode errCode, Map data) {
                        completion.fail(errCode);
                    }
                });
            }

            private void setDiskAOsName(List<APICreateVmInstanceMsg.DiskAO> diskAOs) {
                AtomicInteger count = new AtomicInteger(1);
                diskAOs.stream().filter(diskAO -> diskAO.getSourceUuid() == null).filter(diskAO -> diskAO.getName() == null)
                        .forEach(diskAO -> {
                            diskAO.setName(String.format("DATA-for-%s-%d", finalVo.getName(), count.get()));
                            count.getAndIncrement();
                        });
            }
        }).start();
    }

    private List<String> createDiskOfferingUuidsFromDataDiskSizes(final CreateVmInstanceMsg msg, String vmUuid) {
        if (CollectionUtils.isEmpty(msg.getDataDiskSizes())) {
            return new ArrayList<String>();
        }
        List<String> diskOfferingUuids = new ArrayList<>();
        List<DiskOfferingVO> diskOfferingVos = new ArrayList<>();
        List<Long> volumeSizes = msg.getDataDiskSizes().stream().distinct().collect(Collectors.toList());
        Map<Long, String> sizeDiskOfferingMap = new HashMap<>();
        for (Long size : volumeSizes) {
            DiskOfferingVO dvo = new DiskOfferingVO();
            dvo.setUuid(Platform.getUuid());
            dvo.setAccountUuid(msg.getAccountUuid());
            dvo.setDiskSize(size);
            dvo.setName(String.format("create-data-volume-for-vm-%s", vmUuid));
            dvo.setType("TemporaryDiskOfferingType");
            dvo.setState(DiskOfferingState.Enabled);
            dvo.setAllocatorStrategy(PrimaryStorageConstant.DEFAULT_PRIMARY_STORAGE_ALLOCATION_STRATEGY_TYPE);
            diskOfferingVos.add(dvo);
            sizeDiskOfferingMap.put(size, dvo.getUuid());
        }
        msg.getDataDiskSizes().forEach(size -> diskOfferingUuids.add(sizeDiskOfferingMap.get(size)));
        dbf.persistCollection(diskOfferingVos);
        return diskOfferingUuids;
    }

    private void instantiateTagsForCreateMessage(final CreateVmInstanceMsg msg, final APICreateMessage cmsg, VmInstanceVO finalVo) {
        if (cmsg != null) {
            tagMgr.createTagsFromAPICreateMessage(cmsg, finalVo.getUuid(), VmInstanceVO.class.getSimpleName());
        } else {
            tagMgr.createTags(msg.getSystemTags(), msg.getUserTags(), finalVo.getUuid(), VmInstanceVO.class.getSimpleName());
        }

        boolean isVirtio = false;
        if (!CollectionUtils.isEmpty(msg.getDiskAOs())) {
            isVirtio = msg.getVirtio();
        } else {
            if (Q.New(ImageVO.class).eq(ImageVO_.uuid, msg.getImageUuid()).eq(ImageVO_.virtio, true).isExists()) {
                isVirtio = true;
            }
        }
        if (isVirtio) {
            SystemTagCreator creator = VmSystemTags.VIRTIO.newSystemTagCreator(finalVo.getUuid());
            creator.recreate = true;
            creator.inherent = false;
            creator.tag = VmSystemTags.VIRTIO.getTagFormat();
            creator.create();
        }

        if (finalVo.getInstanceOfferingUuid() != null) {
            tagMgr.copySystemTag(
                    finalVo.getInstanceOfferingUuid(),
                    InstanceOfferingVO.class.getSimpleName(),
                    finalVo.getUuid(),
                    VmInstanceVO.class.getSimpleName(), false);
        }

        if (msg.getImageUuid() != null) {
            tagMgr.copySystemTag(
                    msg.getImageUuid(),
                    ImageVO.class.getSimpleName(),
                    finalVo.getUuid(),
                    VmInstanceVO.class.getSimpleName(), false);
        }

        if (ImageArchitecture.aarch64.toString().equals(finalVo.getArchitecture())) {
            SystemTagCreator creator = VmSystemTags.MACHINE_TYPE.newSystemTagCreator(finalVo.getUuid());
            creator.setTagByTokens(map(e(VmSystemTags.MACHINE_TYPE_TOKEN, VmMachineType.virt.toString())));
            creator.recreate = true;
            creator.create();
        }

        SystemTagCreator creator = VmSystemTags.SYNC_PORTS.newSystemTagCreator(finalVo.getUuid());
        creator.recreate = true;
        creator.setTagByTokens(map(e(VmSystemTags.SYNC_PORTS_TOKEN, finalVo.getUuid())));
        creator.create();
    }

    private List<ErrorCode> extEmitterHandleSystemTag(final CreateVmInstanceMsg msg, final APICreateMessage cmsg, VmInstanceVO finalVo) {
        List<ErrorCode> result = Collections.emptyList();
        if (msg == null) {
            result.add(operr(ORG_ZSTACK_COMPUTE_VM_10238, "CreateVmInstanceMsg cannot be null"));
            return result;
        } else if (cmsg != null && cmsg.getSystemTags() != null && !cmsg.getSystemTags().isEmpty()) {
            return extEmitter.handleSystemTag(finalVo.getUuid(), cmsg.getSystemTags());
        } else if (cmsg == null && msg.getSystemTags() != null && !msg.getSystemTags().isEmpty()) {
            return extEmitter.handleSystemTag(finalVo.getUuid(), msg.getSystemTags());
        }
        return result;
    }

    private List<ErrorCode> extEmitterHandleSshKeyPair(final CreateVmInstanceMsg msg, final APICreateMessage cmsg, VmInstanceVO finalVo) {
        List<ErrorCode> result = Collections.emptyList();
        if (msg == null) {
            result.add(operr(ORG_ZSTACK_COMPUTE_VM_10239, "CreateVmInstanceMsg cannot be null"));
            return result;
        } else if (msg.getSshKeyPairUuids() != null && !msg.getSshKeyPairUuids().isEmpty()) {
            return extEmitter.associateSshKeyPair(finalVo.getUuid(), msg.getSshKeyPairUuids());
        }
        return result;
    }
}

package org.zstack.compute.vm;

import org.apache.commons.validator.routines.DomainValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.cloudbus.*;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.config.GlobalConfig;
import org.zstack.core.config.GlobalConfigBeforeUpdateExtensionPoint;
import org.zstack.core.db.*;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.core.thread.*;
import org.zstack.header.AbstractService;
import org.zstack.header.apimediator.ApiMessageInterceptionException;
import org.zstack.header.apimediator.GlobalApiMessageInterceptor;
import org.zstack.header.core.workflow.FlowChain;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.exception.CloudConfigureFailException;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.identity.*;
import org.zstack.header.identity.Quota.QuotaPair;
import org.zstack.header.image.ImageBootMode;
import org.zstack.header.message.*;
import org.zstack.header.tag.SystemTagCreateMessageValidator;
import org.zstack.header.tag.SystemTagVO;
import org.zstack.header.tag.SystemTagValidator;
import org.zstack.header.vm.*;
import org.zstack.header.volume.VolumeType;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;
import org.zstack.identity.AccountManager;
import org.zstack.identity.QuotaUtil;
import org.zstack.tag.PatternedSystemTag;
import org.zstack.tag.SystemTagUtils;
import org.zstack.tag.TagManager;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.function.Function;

import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import java.util.*;

import static java.util.Arrays.asList;
import static org.zstack.core.Platform.argerr;
import static org.zstack.core.Platform.operr;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.*;

/**
 * Thin facade for VmInstance service.
 * Delegates to sub-managers extracted during God Class refactoring:
 *   - VmFlowChainRegistry: FlowChain builders and element lists
 *   - VmCreationSubManager: VM creation and candidate query handlers
 *   - VmNicApiSubManager: NIC create/delete/query handlers
 *   - VmQuotaSubManager: Quota definitions (registered as separate bean)
 *   - VmExpungeSubManager: Expunge periodic task (registered as separate bean)
 *   - VmExtensionPointAdapter: Extension point implementations (registered as separate bean)
 */
public class VmInstanceManagerImpl extends AbstractService implements
        VmInstanceManager,
        ManagementNodeReadyExtensionPoint,
        GlobalApiMessageInterceptor {
    private static final CLogger logger = Utils.getLogger(VmInstanceManagerImpl.class);

    private static final Set<Class> allowedMessageAfterSoftDeletion = new HashSet<>();

    static {
        allowedMessageAfterSoftDeletion.add(VmInstanceDeletionMsg.class);
    }

    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private AccountManager acntMgr;
    @Autowired
    private TagManager tagMgr;
    @Autowired
    private ThreadFacade thdf;
    @Autowired
    private VmFactoryManager vmFactoryManager;
    @Autowired
    protected EventFacade evtf;

    // --- Sub-managers injected via Spring XML ---
    @Autowired
    private VmFlowChainRegistry flowChainRegistry;
    @Autowired
    private VmCreationSubManager creationSubManager;
    @Autowired
    private VmNicApiSubManager nicApiSubManager;

    private List<VmInstanceExtensionManager> vmExtensionManagers = new ArrayList<>();

    // ==================== Message Routing ====================

    @Override
    public void handleMessage(Message msg) {
        VmInstanceExtensionManager extensionManager = vmExtensionManagers.stream().filter(it -> it.getMessageClasses()
                .stream().anyMatch(clz -> clz.isAssignableFrom(msg.getClass()))).findFirst().orElse(null);
        if (extensionManager != null) {
            extensionManager.handleMessage(msg);
        } else if (msg instanceof APIMessage) {
            handleApiMessage((APIMessage) msg);
        } else {
            handleLocalMessage(msg);
        }
    }

    void passThrough(VmInstanceMessage msg) {
        VmInstanceVO vo = dbf.findByUuid(msg.getVmInstanceUuid(), VmInstanceVO.class);
        if (vo == null && allowedMessageAfterSoftDeletion.contains(msg.getClass())) {
            VmInstanceEO eo = dbf.findByUuid(msg.getVmInstanceUuid(), VmInstanceEO.class);
            vo = ObjectUtils.newAndCopy(eo, VmInstanceVO.class);
        }

        if (vo == null) {
            String err = String.format("Cannot find VmInstance[uuid:%s], it may have been deleted", msg.getVmInstanceUuid());
            bus.replyErrorByMessageType((Message) msg, err);
            return;
        }

        VmInstanceFactory factory = getVmInstanceFactory(VmInstanceType.valueOf(vo.getType()));
        VmInstance vm = factory.getVmInstance(vo);
        vm.handleMessage((Message) msg);
    }

    private void handleLocalMessage(Message msg) {
        if (msg instanceof CreateVmInstanceMsg) {
            creationSubManager.handle((CreateVmInstanceMsg) msg);
        } else if (msg instanceof VmInstanceMessage) {
            passThrough((VmInstanceMessage) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    private void handleApiMessage(APIMessage msg) {
        if (msg instanceof APICreateVmInstanceMsg) {
            creationSubManager.handle((APICreateVmInstanceMsg) msg);
        } else if (msg instanceof APICreateVmNicMsg) {
            nicApiSubManager.handle((APICreateVmNicMsg) msg);
        } else if (msg instanceof APIGetVmNicAttachedNetworkServiceMsg) {
            nicApiSubManager.handle((APIGetVmNicAttachedNetworkServiceMsg) msg);
        } else if (msg instanceof APIDeleteVmNicMsg) {
            nicApiSubManager.handle((APIDeleteVmNicMsg) msg);
        } else if (msg instanceof APIGetCandidateZonesClustersHostsForCreatingVmMsg) {
            creationSubManager.handle((APIGetCandidateZonesClustersHostsForCreatingVmMsg) msg);
        } else if (msg instanceof APIGetCandidatePrimaryStoragesForCreatingVmMsg) {
            creationSubManager.handle((APIGetCandidatePrimaryStoragesForCreatingVmMsg) msg);
        } else if (msg instanceof APIGetInterdependentL3NetworksImagesMsg) {
            creationSubManager.handle((APIGetInterdependentL3NetworksImagesMsg) msg);
        } else if (msg instanceof APIGetInterdependentL3NetworksBackupStoragesMsg) {
            creationSubManager.handle((APIGetInterdependentL3NetworksBackupStoragesMsg) msg);
        } else if (msg instanceof APIGetCandidateVmForAttachingIsoMsg) {
            creationSubManager.handle((APIGetCandidateVmForAttachingIsoMsg) msg);
        } else if (msg instanceof APIUpdatePriorityConfigMsg) {
            creationSubManager.handle((APIUpdatePriorityConfigMsg) msg);
        } else if (msg instanceof APIGetSpiceCertificatesMsg) {
            creationSubManager.handle((APIGetSpiceCertificatesMsg) msg);
        } else if (msg instanceof APIGetVmsCapabilitiesMsg) {
            creationSubManager.handle((APIGetVmsCapabilitiesMsg) msg);
        } else if (msg instanceof VmInstanceMessage) {
            passThrough((VmInstanceMessage) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    // ==================== Service Lifecycle ====================

    @Override
    public String getId() {
        return bus.makeLocalServiceId(VmInstanceConstant.SERVICE_ID);
    }

    @Override
    public boolean start() {
        try {
            flowChainRegistry.buildFlowChains();
            installSystemTagValidator();
            installGlobalConfigUpdater();
            vmExtensionManagers.addAll(pluginRgty.getExtensionList(VmInstanceExtensionManager.class));

            bus.installBeforeDeliveryMessageInterceptor(new AbstractBeforeDeliveryMessageInterceptor() {
                @Override
                public void beforeDeliveryMessage(Message msg) {
                    if (msg instanceof NeedQuotaCheckMessage) {
                        if (((NeedQuotaCheckMessage) msg).getAccountUuid() == null ||
                                ((NeedQuotaCheckMessage) msg).getAccountUuid().equals("")) {
                            // skip admin scheduler
                            return;
                        }
                        List<Quota> quotas = acntMgr.getMessageQuotaMap().get(msg.getClass());
                        if (quotas == null || quotas.size() == 0) {
                            return;
                        }
                        Map<String, QuotaPair> pairs = new QuotaUtil().
                                makeQuotaPairs(((NeedQuotaCheckMessage) msg).getAccountUuid());
                        for (Quota quota : quotas) {
                            quota.getOperator().checkQuota((NeedQuotaCheckMessage) msg, pairs);
                        }
                    }
                }
            }, StartVmInstanceMsg.class);

            deleteMigrateSystemTagWhenVmStateChangedToRunning();
            pluginRgty.saveExtensionAsMap(VmAttachOtherDiskExtensionPoint.class, new Function<Object, VmAttachOtherDiskExtensionPoint>() {
                @Override
                public Object call(VmAttachOtherDiskExtensionPoint arg) {
                    return arg.getDiskType();
                }
            });

            return true;
        } catch (Exception e) {
            throw new CloudConfigureFailException(VmInstanceManagerImpl.class, e.getMessage(), e);
        }
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    @AsyncThread
    public void managementNodeReady() {
        // Expunge task now handled by VmExpungeSubManager
    }

    // ==================== GlobalApiMessageInterceptor ====================

    @Override
    public List<Class> getMessageClassToIntercept() {
        return asList(APIChangeResourceOwnerMsg.class);
    }

    @Override
    public InterceptorPosition getPosition() {
        return InterceptorPosition.END;
    }

    @Override
    public APIMessage intercept(APIMessage msg) throws ApiMessageInterceptionException {
        if (msg instanceof APIChangeResourceOwnerMsg) {
            validateAPIChangeResourceOwnerMsg((APIChangeResourceOwnerMsg) msg);
        }

        return msg;
    }

    private void validateAPIChangeResourceOwnerMsg(APIChangeResourceOwnerMsg msg) {
        SimpleQuery<AccountResourceRefVO> q = dbf.createQuery(AccountResourceRefVO.class);
        q.add(AccountResourceRefVO_.resourceUuid, Op.EQ, msg.getResourceUuid());
        AccountResourceRefVO ref = q.find();

        if (ref == null || !VolumeVO.class.getSimpleName().equals(ref.getResourceType())) {
            return;
        }

        SimpleQuery<VolumeVO> vq = dbf.createQuery(VolumeVO.class);
        vq.add(VolumeVO_.uuid, Op.EQ, ref.getResourceUuid());
        vq.add(VolumeVO_.type, Op.EQ, VolumeType.Root);
        if (vq.isExists()) {
            throw new OperationFailureException(operr(ORG_ZSTACK_COMPUTE_VM_10263, "the resource[uuid:%s] is a ROOT volume, you cannot change its owner, instead," +
                            "change the owner of the VM the root volume belongs to", ref.getResourceUuid()));
        }
    }

    // ==================== VmInstanceManager Interface (delegates to registries/factories) ====================

    @Override
    public VmInstanceFactory getVmInstanceFactory(VmInstanceType type) {
        VmInstanceFactory factory = vmFactoryManager.getVmInstanceFactory(type.toString());
        if (factory == null) {
            throw new CloudRuntimeException(String.format("No VmInstanceFactory of type[%s] found", type));
        }
        return factory;
    }

    @Override
    public VmInstanceBaseExtensionFactory getVmInstanceBaseExtensionFactory(Message msg) {
        return vmFactoryManager.getVmInstanceBaseExtensionFactory(msg.getClass());
    }

    @Override
    public VmInstanceNicFactory getVmInstanceNicFactory(VmNicType type) {
        VmInstanceNicFactory factory = vmFactoryManager.getVmInstanceNicFactory(type.toString());
        if (factory == null) {
            throw new CloudRuntimeException(String.format("No VmInstanceNicFactory of type[%s] found", type));
        }
        return factory;
    }

    @Override
    public VmNicQosConfigBackend getVmNicQosConfigBackend(String type) {
        return vmFactoryManager.getVmNicQosConfigBackend(type);
    }

    // --- FlowChain getters delegate to VmFlowChainRegistry ---

    @Override
    public FlowChain getCreateVmWorkFlowChain(VmInstanceInventory inv) {
        return flowChainRegistry.getCreateVmWorkFlowChain(inv);
    }

    @Override
    public FlowChain getStopVmWorkFlowChain(VmInstanceInventory inv) {
        return flowChainRegistry.getStopVmWorkFlowChain(inv);
    }

    @Override
    public FlowChain getRebootVmWorkFlowChain(VmInstanceInventory inv) {
        return flowChainRegistry.getRebootVmWorkFlowChain(inv);
    }

    @Override
    public FlowChain getStartVmWorkFlowChain(VmInstanceInventory inv) {
        return flowChainRegistry.getStartVmWorkFlowChain(inv);
    }

    @Override
    public FlowChain getDestroyVmWorkFlowChain(VmInstanceInventory inv) {
        return flowChainRegistry.getDestroyVmWorkFlowChain(inv);
    }

    @Override
    public FlowChain getMigrateVmWorkFlowChain(VmInstanceInventory inv) {
        return flowChainRegistry.getMigrateVmWorkFlowChain(inv);
    }

    @Override
    public FlowChain getAttachUninstantiatedVolumeWorkFlowChain(VmInstanceInventory inv) {
        return flowChainRegistry.getAttachUninstantiatedVolumeWorkFlowChain(inv);
    }

    @Override
    public FlowChain getAttachIsoWorkFlowChain(VmInstanceInventory inv) {
        return flowChainRegistry.getAttachIsoWorkFlowChain(inv);
    }

    @Override
    public FlowChain getDetachIsoWorkFlowChain(VmInstanceInventory inv) {
        return flowChainRegistry.getDetachIsoWorkFlowChain(inv);
    }

    @Override
    public FlowChain getExpungeVmWorkFlowChain(VmInstanceInventory inv) {
        return flowChainRegistry.getExpungeVmWorkFlowChain(inv);
    }

    public FlowChain getPauseWorkFlowChain(VmInstanceInventory inv) {
        return flowChainRegistry.getPauseWorkFlowChain(inv);
    }

    public FlowChain getResumeVmWorkFlowChain(VmInstanceInventory inv) {
        return flowChainRegistry.getResumeVmWorkFlowChain(inv);
    }

    // ==================== GlobalConfig Updaters ====================

    private void installGlobalConfigUpdater() {
        VmGlobalConfig.MULTI_VNIC_SUPPORT.installBeforeUpdateExtension(new GlobalConfigBeforeUpdateExtensionPoint() {
            @Override
            public void beforeUpdateExtensionPoint(GlobalConfig oldConfig, String newValue) {
                if (!oldConfig.value(Boolean.class) || "true".equalsIgnoreCase(newValue)) {
                    return;
                }

                List<Tuple> tuples;
                String sql = "select vmInstanceUuid, l3NetworkUuid, count(*) from VmNicVO group by vmInstanceUuid, l3NetworkUuid";
                TypedQuery<Tuple> q = dbf.getEntityManager().createQuery(sql, Tuple.class);
                tuples = q.getResultList();
                if (tuples == null || tuples.isEmpty()) {
                    return;
                }
                for (Tuple tuple: tuples) {
                    if (tuple.get(2, Long.class) > 1) {
                        throw new ApiMessageInterceptionException(operr(ORG_ZSTACK_COMPUTE_VM_10243, "unable to enable this function. There are multi nics of L3 network[uuid:%s] in the vm[uuid: %s]",
                                    tuple.get(0, String.class), tuple.get(1, String.class)));
                    }
                }
            }
        });

        // Note: VM_RESOURCE_BINGDING tag is silently deprecated (ZSTAC-75428)
        // The tag data is preserved but no longer read or written by the new ResourceBindingAllocatorFlow
    }

    // ==================== System Tag Validators ====================

    private void installSystemTagValidator() {
        installHostnameValidator();
        installUserdataValidator();
        installBootModeValidator();
        installCleanTrafficValidator();
        installMachineTypeValidator();
        installUsbRedirectValidator();
        installL3NetworkSecurityGroupValidator();
        installSeDeviceValidator();
        installHygonSeDeviceValidator();
        new StaticIpOperator().installStaticIpValidator();
    }

    private void installHostnameValidator() {
        class HostNameValidator implements SystemTagCreateMessageValidator, SystemTagValidator {
            private void validateHostname(String tag, String hostname) {
                DomainValidator domainValidator = DomainValidator.getInstance(true);
                if (!domainValidator.isValid(hostname)) {
                    throw new ApiMessageInterceptionException(argerr(ORG_ZSTACK_COMPUTE_VM_10244, "hostname[%s] specified in system tag[%s] is not a valid domain name", hostname, tag));
                }
            }

            @Override
            public void validateSystemTagInCreateMessage(APICreateMessage cmsg) {
                final NewVmInstanceMessage msg = (NewVmInstanceMessage) cmsg;

                int hostnameCount = 0;
                for (String sysTag : msg.getSystemTags()) {
                    if (VmSystemTags.HOSTNAME.isMatch(sysTag)) {
                        if (++hostnameCount > 1) {
                            throw new ApiMessageInterceptionException(argerr(ORG_ZSTACK_COMPUTE_VM_10245, "only one hostname system tag is allowed, but %s got", hostnameCount));
                        }

                        String hostname = VmSystemTags.HOSTNAME.getTokenByTag(sysTag, VmSystemTags.HOSTNAME_TOKEN);

                        validateHostname(sysTag, hostname);
                    }
                }
            }

            @Transactional(readOnly = true)
            private List<SystemTagVO> querySystemTagsByL3(String tag, String l3Uuid) {
                String sql = "select t" +
                        " from SystemTagVO t, VmInstanceVO vm, VmNicVO nic" +
                        " where t.resourceUuid = vm.uuid" +
                        " and vm.uuid = nic.vmInstanceUuid" +
                        " and nic.l3NetworkUuid = :l3Uuid" +
                        " and t.tag = :sysTag";
                TypedQuery<SystemTagVO> q = dbf.getEntityManager().createQuery(sql, SystemTagVO.class);
                q.setParameter("l3Uuid", l3Uuid);
                q.setParameter("sysTag", tag);
                return q.getResultList();
            }

            private void validateHostNameOnDefaultL3Network(String tag, String hostname, String l3Uuid) {
                List<SystemTagVO> vos = querySystemTagsByL3(tag, l3Uuid);

                if (!vos.isEmpty()) {
                    SystemTagVO sameTag = vos.get(0);
                    throw new ApiMessageInterceptionException(argerr(ORG_ZSTACK_COMPUTE_VM_10246, "conflict hostname in system tag[%s];" +
                                    " there has been a VM[uuid:%s] having hostname[%s] on L3 network[uuid:%s]",
                            tag, sameTag.getResourceUuid(), hostname, l3Uuid));
                }
            }

            @Override
            public void validateSystemTag(String resourceUuid, Class resourceType, String systemTag) {
                if (VmSystemTags.HOSTNAME.isMatch(systemTag)) {
                    String hostname = VmSystemTags.HOSTNAME.getTokenByTag(systemTag, VmSystemTags.HOSTNAME_TOKEN);
                    validateHostname(systemTag, hostname);

                    SimpleQuery<VmInstanceVO> q = dbf.createQuery(VmInstanceVO.class);
                    q.select(VmInstanceVO_.defaultL3NetworkUuid);
                    q.add(VmInstanceVO_.uuid, Op.EQ, resourceUuid);
                    String defaultL3Uuid = q.findValue();
                } else if (VmSystemTags.BOOT_ORDER.isMatch(systemTag)) {
                    validateBootOrder(systemTag);
                }
            }

            private void validateBootOrder(String systemTag) {
                String order = VmSystemTags.BOOT_ORDER.getTokenByTag(systemTag, VmSystemTags.BOOT_ORDER_TOKEN);
                for (String o : order.split(",")) {
                    try {
                        VmBootDevice.valueOf(o);
                    } catch (IllegalArgumentException e) {
                        throw new OperationFailureException(argerr(ORG_ZSTACK_COMPUTE_VM_10247, "invalid boot device[%s] in boot order[%s]", o, order));
                    }
                }
            }
        }

        HostNameValidator hostnameValidator = new HostNameValidator();
        tagMgr.installCreateMessageValidator(VmInstanceVO.class.getSimpleName(), hostnameValidator);
        VmSystemTags.HOSTNAME.installValidator(hostnameValidator);

        // TODO: system tags should support token format validation
        VmHardwareSystemTags.CPU_SOCKETS.installValidator((resourceUuid, resourceType, systemTag) -> {
            String sockets = VmHardwareSystemTags.CPU_SOCKETS.getTokenByTag(systemTag, VmHardwareSystemTags.CPU_SOCKETS_TOKEN);
            try {
                Integer.valueOf(sockets);
            } catch (NumberFormatException e) {
                throw new ApiMessageInterceptionException(argerr(ORG_ZSTACK_COMPUTE_VM_10248, "cpuSockets must be an integer"));
            }
        });

        VmHardwareSystemTags.CPU_CORES.installValidator((resourceUuid, resourceType, systemTag) -> {
            String cores = VmHardwareSystemTags.CPU_CORES.getTokenByTag(systemTag, VmHardwareSystemTags.CPU_CORES_TOKEN);
            try {
                Integer.valueOf(cores);
            } catch (NumberFormatException e) {
                throw new ApiMessageInterceptionException(argerr(ORG_ZSTACK_COMPUTE_VM_10249, "cpuCores must be an integer"));
            }
        });

        VmHardwareSystemTags.CPU_THREADS.installValidator((resourceUuid, resourceType, systemTag) -> {
            String threads = VmHardwareSystemTags.CPU_THREADS.getTokenByTag(systemTag, VmHardwareSystemTags.CPU_THREADS_TOKEN);
            try {
                Integer.valueOf(threads);
            } catch (NumberFormatException e) {
                throw new ApiMessageInterceptionException(argerr(ORG_ZSTACK_COMPUTE_VM_10250, "cpuThreads must be an integer"));
            }
        });
    }

    private void installUserdataValidator() {
        class UserDataValidator implements SystemTagCreateMessageValidator, SystemTagValidator {

            private void check(String resourceUuid, Class resourceType) {
                int existUserdataTagCount = VmSystemTags.USERDATA.getTags(resourceUuid, resourceType).size();
                if (existUserdataTagCount > 0) {
                    throw new OperationFailureException(argerr(
                    ORG_ZSTACK_COMPUTE_VM_10251,         "Already have one userdata systemTag for vm[uuid: %s].",
                            resourceUuid));
                }
            }

            private void checkUserdataDecode(String systemTag) {
                String userdata = VmSystemTags.USERDATA.getTokenByTag(systemTag, VmSystemTags.USERDATA_TOKEN);
                Base64.getDecoder().decode(userdata.getBytes());
            }

            private void validUserdataFormat(String systemTag) {
                VmSystemTags.UserdataTagOutputHandler handler = new VmSystemTags.UserdataTagOutputHandler();
                handler.desensitizeTag(VmSystemTags.USERDATA, systemTag);
            }


            @Override
            public void validateSystemTag(String resourceUuid, Class resourceType, String systemTag) {
                if (!VmSystemTags.USERDATA.isMatch(systemTag)) {
                    return;
                }
                check(resourceUuid, resourceType);
                checkUserdataDecode(systemTag);
                validUserdataFormat(systemTag);
            }

            @Override
            public void validateSystemTagInCreateMessage(APICreateMessage msg) {
                int userdataTagCount = 0;
                for (String sysTag : msg.getSystemTags()) {
                    if (VmSystemTags.USERDATA.isMatch(sysTag)) {
                        if (userdataTagCount > 0) {
                            throw new OperationFailureException(argerr(
                            ORG_ZSTACK_COMPUTE_VM_10252,         "Shouldn't be more than one userdata systemTag for one vm."));
                        }
                        userdataTagCount++;

                        check(msg.getResourceUuid(), VmInstanceVO.class);
                        checkUserdataDecode(sysTag);
                        String tagValue = SystemTagUtils.findTagValue(msg.getSystemTags(), VmSystemTags.USERDATA);
                        if (tagValue == null) {
                            return;
                        }

                        validUserdataFormat(tagValue);

                    }
                }
            }
        }

        UserDataValidator userDataValidator = new UserDataValidator();
        tagMgr.installCreateMessageValidator(VmInstanceVO.class.getSimpleName(), userDataValidator);
        VmSystemTags.USERDATA.installValidator(userDataValidator);
    }

    private void installBootModeValidator() {
        class BootModeValidator implements SystemTagCreateMessageValidator, SystemTagValidator {
            @Override
            public void validateSystemTag(String resourceUuid, Class resourceType, String systemTag) {
                if (!VmSystemTags.BOOT_MODE.isMatch(systemTag)) {
                    return;
                }

                String bootMode = VmSystemTags.BOOT_MODE.getTokenByTag(systemTag, VmSystemTags.BOOT_MODE_TOKEN);
                validateBootMode(systemTag, bootMode);
            }

            @Override
            public void validateSystemTagInCreateMessage(APICreateMessage msg) {
                if (msg.getSystemTags() == null || msg.getSystemTags().isEmpty()) {
                    return;
                }

                int bootModeCount = 0;
                for (String systemTag : msg.getSystemTags()) {
                    if (VmSystemTags.BOOT_MODE.isMatch(systemTag)) {
                        if (++bootModeCount > 1) {
                            throw new ApiMessageInterceptionException(argerr(ORG_ZSTACK_COMPUTE_VM_10253, "only one bootMode system tag is allowed, but %d got", bootModeCount));
                        }

                        String bootMode = VmSystemTags.BOOT_MODE.getTokenByTag(systemTag, VmSystemTags.BOOT_MODE_TOKEN);
                        validateBootMode(systemTag, bootMode);
                    }
                }
            }

            private void validateBootMode(String systemTag, String bootMode) {
                boolean valid = false;
                for (ImageBootMode bm : ImageBootMode.values()) {
                    if (bm.name().equalsIgnoreCase(bootMode)) {
                        valid = true;
                        break;
                    }
                }
                if (!valid) {
                    throw new ApiMessageInterceptionException(argerr(
                    ORG_ZSTACK_COMPUTE_VM_10254,         "[%s] specified in system tag [%s] is not a valid boot mode", bootMode, systemTag)
                    );
                }
            }
        }

        BootModeValidator validator = new BootModeValidator();
        tagMgr.installCreateMessageValidator(VmInstanceVO.class.getSimpleName(), validator);
        VmSystemTags.BOOT_MODE.installValidator(validator);
    }

    private void installCleanTrafficValidator() {
        class CleanTrafficValidator implements SystemTagCreateMessageValidator, SystemTagValidator {
            @Override
            public void validateSystemTagInCreateMessage(APICreateMessage msg) {
                if (msg instanceof APICreateVmInstanceMsg) {
                    Optional.ofNullable(msg.getSystemTags()).ifPresent(it -> {
                        if (it.stream().anyMatch(tag -> VmSystemTags.CLEAN_TRAFFIC.isMatch(tag))) {
                            validateVmType(null, ((APICreateVmInstanceMsg) msg).getType());
                        }
                    });
                }
            }

            @Override
            public void validateSystemTag(String resourceUuid, Class resourceType, String systemTag) {
                validateVmType(resourceUuid, null);
            }

            private void validateVmType(String vmUuid, String vmType) {
                if (vmType == null) {
                    vmType = Q.New(VmInstanceVO.class).eq(VmInstanceVO_.uuid, vmUuid).select(VmInstanceVO_.type).findValue();
                }

                if (!VmInstanceConstant.USER_VM_TYPE.equals(vmType)) {
                    throw new ApiMessageInterceptionException(argerr(
                    ORG_ZSTACK_COMPUTE_VM_10255,         "clean traffic is not supported for vm type [%s]", vmType)
                    );
                }
            }
        }

        CleanTrafficValidator validator = new CleanTrafficValidator();
        tagMgr.installCreateMessageValidator(VmInstanceVO.class.getSimpleName(), validator);
        VmSystemTags.CLEAN_TRAFFIC.installValidator(validator);
    }

    private void installMachineTypeValidator() {
        class MachineTypeValidator implements SystemTagCreateMessageValidator, SystemTagValidator {
            @Override
            public void validateSystemTagInCreateMessage(APICreateMessage msg) {
                Optional.ofNullable(msg.getSystemTags()).ifPresent(it -> it.forEach(this::validateMachineType));
            }

            @Override
            public void validateSystemTag(String resourceUuid, Class resourceType, String systemTag) {
                validateMachineType(systemTag);
            }

            private void validateMachineType(String systemTag) {
                if (!VmSystemTags.MACHINE_TYPE.isMatch(systemTag)) {
                    return;
                }

                String type = VmSystemTags.MACHINE_TYPE.getTokenByTag(systemTag, VmSystemTags.MACHINE_TYPE_TOKEN);
                if (VmMachineType.get(type) == null) {
                    throw new ApiMessageInterceptionException(argerr(ORG_ZSTACK_COMPUTE_VM_10256, "vm machine type requires [q35, pc, virt], but get [%s]", type));
                }
            }
        }

        MachineTypeValidator validator = new MachineTypeValidator();
        tagMgr.installCreateMessageValidator(VmInstanceVO.class.getSimpleName(), validator);
        VmSystemTags.MACHINE_TYPE.installValidator(validator);
    }

    private void installL3NetworkSecurityGroupValidator() {
        class L3NetworkSecurityGroupValidator implements SystemTagCreateMessageValidator, SystemTagValidator {

            @Override
            public void validateSystemTagInCreateMessage(APICreateMessage msg) {
                if (msg.getSystemTags() == null || msg.getSystemTags().isEmpty()) {
                    return;
                }

                for (String systemTag : msg.getSystemTags()) {
                    validateL3NetworkSecurityGroup(systemTag);
                }
            }

            @Override
            public void validateSystemTag(String resourceUuid, Class resourceType, String systemTag) {
                validateL3NetworkSecurityGroup(systemTag);
            }

            private void validateL3NetworkSecurityGroup(String systemTag) {
                if (!VmSystemTags.L3_NETWORK_SECURITY_GROUP_UUIDS_REF.isMatch(systemTag)) {
                    return;
                }

                String l3Uuid = VmSystemTags.L3_NETWORK_SECURITY_GROUP_UUIDS_REF.getTokenByTag(systemTag, VmSystemTags.L3_UUID_TOKEN);
                List<String> securityGroupUuids = asList(VmSystemTags.L3_NETWORK_SECURITY_GROUP_UUIDS_REF
                        .getTokenByTag(systemTag, VmSystemTags.SECURITY_GROUP_UUIDS_TOKEN).split(","));

                validateL3NetworkAttachSecurityGroup(l3Uuid, securityGroupUuids);
            }

            private void validateL3NetworkAttachSecurityGroup(String l3Uuid, List<String> securityGroupUuids) {
                pluginRgty.getExtensionList(ValidateL3SecurityGroupExtensionPoint.class)
                        .forEach(ext -> ext.validateSystemtagL3SecurityGroup(l3Uuid, securityGroupUuids));
            }
        }

        L3NetworkSecurityGroupValidator validator = new L3NetworkSecurityGroupValidator();
        tagMgr.installCreateMessageValidator(VmInstanceVO.class.getSimpleName(), validator);
        VmSystemTags.L3_NETWORK_SECURITY_GROUP_UUIDS_REF.installValidator(validator);
    }

    private void installBooleanTagValidator(PatternedSystemTag tag, String tokenName, String tagDescription) {
        tag.installValidator(new SystemTagValidator() {
            @Override
            public void validateSystemTag(String resourceUuid, Class resourceType, String systemTag) {
                String tokenValue = null;
                if (tag.isMatch(systemTag)) {
                    tokenValue = tag.getTokenByTag(systemTag, tokenName);
                } else {
                    throw new OperationFailureException(argerr(ORG_ZSTACK_COMPUTE_VM_10257, "invalid %s tag[%s]", tagDescription, systemTag));
                }
                if (!isBoolean(tokenValue)) {
                    throw new OperationFailureException(argerr(ORG_ZSTACK_COMPUTE_VM_10258, "invalid %s[%s], value [%s] is not boolean", tagDescription, systemTag, tokenValue));
                }
            }
            private boolean isBoolean(String param) {
                return "true".equalsIgnoreCase(param) || "false".equalsIgnoreCase(param);
            }
        });
    }

    private void installSeDeviceValidator() {
        installBooleanTagValidator(VmSystemTags.SECURITY_ELEMENT_ENABLE,
                VmSystemTags.SECURITY_ELEMENT_ENABLE_TOKEN,
                "securityElementEnable");
    }

    private void installHygonSeDeviceValidator() {
        installBooleanTagValidator(VmSystemTags.HYGON_SECURITY_ELEMENT_ENABLE,
                VmSystemTags.HYGON_SECURITY_ELEMENT_ENABLE_TOKEN,
                "hygonSecurityElementEnable");
    }

    private void installUsbRedirectValidator() {
        VmSystemTags.USB_REDIRECT.installValidator(new SystemTagValidator() {
            @Override
            public void validateSystemTag(String resourceUuid, Class resourceType, String systemTag) {
                String usbRedirectTokenByTag = null;
                if (VmSystemTags.USB_REDIRECT.isMatch(systemTag)) {
                    usbRedirectTokenByTag = VmSystemTags.USB_REDIRECT.getTokenByTag(systemTag, VmSystemTags.USB_REDIRECT_TOKEN);
                } else {
                    throw new OperationFailureException(argerr(ORG_ZSTACK_COMPUTE_VM_10259, "invalid usbRedirect[%s], %s is not usbRedirect tag", systemTag, usbRedirectTokenByTag));
                }
                if (!isBoolean(usbRedirectTokenByTag)) {
                    throw new OperationFailureException(argerr(ORG_ZSTACK_COMPUTE_VM_10260, "invalid usbRedirect[%s], %s is not boolean class", systemTag, usbRedirectTokenByTag));
                }
            }
            private boolean isBoolean(String param) {
                return "true".equalsIgnoreCase(param) || "false".equalsIgnoreCase(param);
            }
        });
    }

    // ==================== Event Listeners ====================

    public void deleteMigrateSystemTagWhenVmStateChangedToRunning() {
        evtf.onLocal(VmCanonicalEvents.VM_FULL_STATE_CHANGED_PATH, new EventCallback() {
            @Override
            protected void run(Map tokens, Object data) {
                VmCanonicalEvents.VmStateChangedData d = (VmCanonicalEvents.VmStateChangedData) data;
                if (!Objects.equals(d.getOldState(), VmInstanceState.Migrating.toString()) && Objects.equals(d.getNewState(), VmInstanceState.Running.toString())) {
                    VmSystemTags.VM_STATE_PAUSED_AFTER_MIGRATE.delete(d.getVmUuid());
                }
            }
        });
    }
}

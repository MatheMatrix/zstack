package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.db.*;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.directory.ResourceDirectoryRefVO;
import org.zstack.header.core.NopeWhileDoneCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.host.*;
import org.zstack.header.identity.AccountResourceRefInventory;
import org.zstack.header.identity.ResourceOwnerAfterChangeExtensionPoint;
import org.zstack.header.message.MessageReply;
import org.zstack.header.network.l3.*;
import org.zstack.header.tag.PatternedSystemTag;
import org.zstack.header.vm.*;
import org.zstack.header.vm.cdrom.VmCdRomVO;
import org.zstack.header.vm.cdrom.VmCdRomVO_;
import org.zstack.identity.AccountManager;
import org.zstack.resourceconfig.ResourceConfig;
import org.zstack.resourceconfig.ResourceConfigFacade;
import org.zstack.tag.SystemTagUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.Tuple;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;

/**
 * Adapter for extension point implementations previously embedded in VmInstanceManagerImpl.
 * Handles: L3NetworkDelete, ResourceOwnerAfterChange, AfterChangeHostStatus,
 * VmInstanceMigrate, VmInstanceBeforeStart extension points.
 * Extracted from VmInstanceManagerImpl to reduce God Class complexity.
 */
public class VmExtensionPointAdapter implements
        L3NetworkDeleteExtensionPoint,
        ResourceOwnerAfterChangeExtensionPoint,
        AfterChangeHostStatusExtensionPoint,
        VmInstanceMigrateExtensionPoint,
        VmInstanceBeforeStartExtensionPoint {

    private static final CLogger logger = Utils.getLogger(VmExtensionPointAdapter.class);

    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private AccountManager acntMgr;
    @Autowired
    private ResourceConfigFacade rcf;

    // --- L3NetworkDeleteExtensionPoint ---

    @Override
    public String preDeleteL3Network(L3NetworkInventory inventory) throws L3NetworkException {
        return null;
    }

    @Override
    public void beforeDeleteL3Network(L3NetworkInventory inventory) {
    }

    @Override
    public void afterDeleteL3Network(L3NetworkInventory inventory) {
        new StaticIpOperator().deleteStaticIpByL3NetworkUuid(inventory.getUuid());
    }

    // --- ResourceOwnerAfterChangeExtensionPoint ---

    @Override
    public void resourceOwnerAfterChange(AccountResourceRefInventory ref, String newOwnerUuid) {
        if (!VmInstanceVO.class.getSimpleName().equals(ref.getResourceType())) {
            return;
        }

        // change root volume
        SimpleQuery<VmInstanceVO> q = dbf.createQuery(VmInstanceVO.class);
        q.select(VmInstanceVO_.rootVolumeUuid);
        q.add(VmInstanceVO_.uuid, Op.EQ, ref.getResourceUuid());
        String rootVolumeUuid = q.findValue();
        if (rootVolumeUuid == null) {
            return;
        }

        acntMgr.changeResourceOwner(rootVolumeUuid, newOwnerUuid);

        // change vmnic(s)
        SimpleQuery<VmNicVO> sq = dbf.createQuery(VmNicVO.class);
        sq.select(VmNicVO_.uuid);
        sq.add(VmNicVO_.vmInstanceUuid, Op.EQ, ref.getResourceUuid());
        List<String> vmnics = sq.listValue();
        if (vmnics.isEmpty()) {
            return;
        }
        for (String vmnicUuid : vmnics) {
            acntMgr.changeResourceOwner(vmnicUuid, newOwnerUuid);
        }

        changeVmCdRomsOwner(ref.getResourceUuid(), newOwnerUuid);
    }

    private void changeVmCdRomsOwner(String vmInstanceUuid, String newOwnerUuid) {
        List<String> vmCdRomUuids = Q.New(VmCdRomVO.class)
                .select(VmCdRomVO_.uuid)
                .eq(VmCdRomVO_.vmInstanceUuid, vmInstanceUuid)
                .listValues();
        if (vmCdRomUuids.isEmpty()) {
            return;
        }

        for (String uuid : vmCdRomUuids) {
            acntMgr.changeResourceOwner(uuid, newOwnerUuid);
        }
    }

    // --- AfterChangeHostStatusExtensionPoint ---

    @Override
    public void afterChangeHostStatus(String hostUuid, HostStatus before, HostStatus next) {
        if (next == HostStatus.Disconnected) {
            List<Tuple> vms = Q.New(VmInstanceVO.class).select(VmInstanceVO_.uuid, VmInstanceVO_.state)
                    .eq(VmInstanceVO_.hostUuid, hostUuid)
                    .listTuple();
            if (vms.isEmpty()) {
                return;
            }

            new While<>(vms).step((vm, completion) -> {
                String vmUuid = vm.get(0, String.class);
                String vmState = vm.get(1, VmInstanceState.class).toString();
                VmStateChangedOnHostMsg msg = new VmStateChangedOnHostMsg();
                msg.setVmInstanceUuid(vmUuid);
                msg.setHostUuid(hostUuid);
                msg.setStateOnHost(VmInstanceState.Unknown);
                bus.makeTargetServiceIdByResourceUuid(msg, VmInstanceConstant.SERVICE_ID, vmUuid);
                bus.send(msg, new CloudBusCallBack(completion) {
                    @Override
                    public void run(MessageReply reply) {
                        if (!reply.isSuccess()) {
                            logger.warn(String.format("the host[uuid:%s] disconnected, but the vm[uuid:%s] fails to " +
                                    "change it's state to Unknown, %s", hostUuid, vmUuid, reply.getError()));
                            logger.warn(String.format("create an unknowngc job for vm[uuid:%s]", vmUuid));

                            UnknownVmGC gc = new UnknownVmGC();
                            gc.NAME = UnknownVmGC.getGCName(vmUuid);
                            gc.vmUuid = vmUuid;
                            gc.vmState = vmState;
                            gc.hostUuid = hostUuid;
                            if (gc.existedAndNotCompleted()) {
                                logger.debug(String.format("There is already a UnknownVmGC of vm[uuid:%s] " +
                                        "on host[uuid:%s], skip.", vmUuid, hostUuid));
                            } else {
                                gc.submit(VmGlobalConfig.UNKNOWN_GC_INTERVAL.value(Long.class), TimeUnit.SECONDS);
                            }
                        } else {
                            logger.debug(String.format("the host[uuid:%s] disconnected, change the VM[uuid:%s]' state to Unknown", hostUuid, vmUuid));
                        }
                        completion.done();
                    }
                });
            }, 20).run(new NopeWhileDoneCompletion());
        }
    }

    // --- VmInstanceMigrateExtensionPoint ---

    @Override
    public void beforeMigrateVm(VmInstanceInventory inv, String destHostUuid) {
    }

    @Override
    public void afterMigrateVm(VmInstanceInventory inv, String srcHostUuid) {
        if (!inv.getHypervisorType().equals(VmInstanceConstant.KVM_HYPERVISOR_TYPE)) {
            return;
        }

        VmPriorityLevel level = new VmPriorityOperator().getVmPriority(inv.getUuid());
        VmPriorityConfigVO priorityVO = Q.New(VmPriorityConfigVO.class).eq(VmPriorityConfigVO_.level, level).find();

        UpdateVmPriorityMsg msg = new UpdateVmPriorityMsg();
        msg.setPriorityConfigStructs(asList(new PriorityConfigStruct(priorityVO, inv.getUuid())));
        msg.setHostUuid(inv.getHostUuid());
        bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, inv.getHostUuid());
        bus.send(msg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                UpdateVmPriorityReply r = new UpdateVmPriorityReply();
                if (!reply.isSuccess()) {
                    logger.warn(String.format("update vm[%s] priority to [%s] failed,because %s",
                            inv.getUuid(), level.toString(), reply.getError()));
                }
            }
        });
    }

    @Override
    public void failedToMigrateVm(VmInstanceInventory inv, String destHostUuid, ErrorCode reason) {
    }

    // --- VmInstanceBeforeStartExtensionPoint ---

    @Override
    public ErrorCode handleSystemTag(String vmUuid, List<String> tags) {
        ErrorCode errorCode = handleResourceDirectorySystemTag(vmUuid, tags);
        if (errorCode != null) {
            return errorCode;
        }

        errorCode = handleNumaSystemTag(vmUuid, tags);

        if (errorCode != null) {
            return errorCode;
        }

        return null;
    }

    private ErrorCode handleNumaSystemTag(String vmUuid, List<String> tags) {
        if (!VmSystemTags.NUMA.hasTag(vmUuid)) {
            return null;
        }
        ResourceConfig rc = rcf.getResourceConfig(VmGlobalConfig.NUMA.getIdentity());
        rc.updateValue(vmUuid, Boolean.TRUE.toString());
        VmSystemTags.NUMA.delete(vmUuid);

        return null;
    }

    //todo move to directory
    private ErrorCode handleResourceDirectorySystemTag(String vmUuid, List<String> tags) {
        PatternedSystemTag tag = VmSystemTags.DIRECTORY_UUID;
        String token = VmSystemTags.DIRECTORY_UUID_TOKEN;

        String directoryUuid = SystemTagUtils.findTagValue(tags, tag, token);
        if (org.apache.commons.lang.StringUtils.isEmpty(directoryUuid)) {
            return null;
        }
        ResourceDirectoryRefVO refVO = new ResourceDirectoryRefVO();
        refVO.setResourceUuid(vmUuid);
        refVO.setDirectoryUuid(directoryUuid);
        refVO.setResourceType(VmInstanceVO.class.getSimpleName());
        refVO.setLastOpDate(new Timestamp(new Date().getTime()));
        refVO.setCreateDate(new Timestamp(new Date().getTime()));
        dbf.persist(refVO);
        return null;
    }
}

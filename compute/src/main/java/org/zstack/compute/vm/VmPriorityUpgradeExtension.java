package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.*;
import org.zstack.core.db.Q;
import org.zstack.header.Component;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.host.HostCanonicalEvents;
import org.zstack.header.host.HostConstant;
import org.zstack.header.host.HostInventory;
import org.zstack.header.host.HostStatus;
import org.zstack.header.message.MessageReply;
import org.zstack.header.tag.SystemTagVO;
import org.zstack.header.tag.SystemTagVO_;
import org.zstack.header.vm.*;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @ Author : yh.w
 * @ Date   : Created in 14:30 2019/9/24
 */
public class VmPriorityUpgradeExtension implements Component {
    private static final CLogger logger = Utils.getLogger(VmPriorityUpgradeExtension.class);

    @Autowired
    private EventFacade evtf;

    @Autowired
    private CloudBus bus;

    @Autowired
    private ResourceDestinationMaker destinationMaker;

    @Override
    public boolean start() {
        initRunningVmPriority();
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    public void updateVmPriorityOnHost(HostInventory inv) {
        List<String> vmUuids = getVmUuidsForPriorityUpdate(inv);
        if (vmUuids.isEmpty()) {
            return;
        }

        VmPriorityConfigVO priorityVO = Q.New(VmPriorityConfigVO.class).eq(VmPriorityConfigVO_.level, VmPriorityLevel.Normal).find();
        List<PriorityConfigStruct> priorityConfigStructs = new ArrayList<>();
        vmUuids.forEach(v -> {
            priorityConfigStructs.add(new PriorityConfigStruct(priorityVO, v));
        });

        UpdateVmPriorityMsg msg = new UpdateVmPriorityMsg();
        msg.setHostUuid(inv.getUuid());
        msg.setPriorityConfigStructs(priorityConfigStructs);
        bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, inv.getUuid());
        bus.send(msg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                UpdateVmPriorityReply r = new UpdateVmPriorityReply();
                if (!reply.isSuccess()) {
                    logger.warn(String.format("update vms priority failed on host[%s],because %s",
                            inv.getUuid(), reply.getError()));
                    return;
                }

                new VmPriorityOperator().batchSetVmPriority(vmUuids, VmPriorityLevel.Normal);
            }
        });
    }

    public List<String> getVmUuidsForPriorityUpdate(HostInventory inv) {
        List<String> vmUuids = Q.New(VmInstanceVO.class)
                .select(VmInstanceVO_.uuid)
                .eq(VmInstanceVO_.hostUuid, inv.getUuid())
                .eq(VmInstanceVO_.state, VmInstanceState.Running)
                .eq(VmInstanceVO_.type, VmInstanceConstant.USER_VM_TYPE)
                .listValues();
        if (vmUuids.isEmpty()) {
            return vmUuids;
        }

        vmUuids.removeIf( v -> !destinationMaker.isManagedByUs(v));

        List<String> updatedVms = Q.New(SystemTagVO.class)
                .select(SystemTagVO_.resourceUuid)
                .like(SystemTagVO_.tag, "vmPriority::%")
                .in(SystemTagVO_.resourceUuid, vmUuids)
                .listValues();

        for (String vmUuid: updatedVms) {
            String existingCheckSum = VmSystemTags.VM_PRIORITY_CHECKSUM.getTokenByResourceUuid(vmUuid, VmInstanceVO.class, VmSystemTags.VM_PRIORITY_CHECKSUM_TOKEN);
            if (existingCheckSum == null) {
                continue;
            }

            String level = VmSystemTags.VM_PRIORITY.getTokenByResourceUuid(vmUuid, VmInstanceVO.class, VmSystemTags.VM_PRIORITY_TOKEN);
            if (level == null) {
                throw new CloudRuntimeException(String.format("vm[uuid:%s] has vmPriority tag but no level", vmUuid));
            }

            VmPriorityConfigVO vo = Q.New(VmPriorityConfigVO.class).eq(VmPriorityConfigVO_.level, level).find();
            VmPriorityLevel()

            String newCheckSum = VmPriorityLevel.valueOf(level).generateChecksum();
            if (existingCheckSum.equals(newCheckSum)) {
                vmUuids.remove(vmUuid);
                continue;
            }

            logger.info(String.format("vm[uuid:%s] has vmPriority tag with level[%s], checksum has changed from %s to %s, update it", vmUuid, level, existingCheckSum, newCheckSum));
        }

        return vmUuids;
    }

    private void initRunningVmPriority() {
        if (!VmGlobalProperty.initRunningVmPriority) {
            return;
        }

        evtf.on(HostCanonicalEvents.HOST_STATUS_CHANGED_PATH, new EventCallback() {
            @Override
            protected void run(Map tokens, Object data) {
                HostCanonicalEvents.HostStatusChangedData d = (HostCanonicalEvents.HostStatusChangedData) data;
                if (!d.getNewStatus().equals(HostStatus.Connected.toString())
                    || !d.getInventory().getHypervisorType().equals("KVM")) {
                    return;
                }

                updateVmPriorityOnHost(d.getInventory());
            }
        });
    }
}

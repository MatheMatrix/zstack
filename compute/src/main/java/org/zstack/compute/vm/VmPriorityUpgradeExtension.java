package org.zstack.compute.vm;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.*;
import org.zstack.core.db.Q;
import org.zstack.header.Component;
import org.zstack.header.host.*;
import org.zstack.header.message.MessageReply;
import org.zstack.header.tag.SystemTagVO;
import org.zstack.header.tag.SystemTagVO_;
import org.zstack.header.vm.*;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        List<String> vmUuids = Q.New(VmInstanceVO.class)
                .select(VmInstanceVO_.uuid)
                .eq(VmInstanceVO_.hostUuid, inv.getUuid())
                .eq(VmInstanceVO_.state, VmInstanceState.Running)
                .eq(VmInstanceVO_.type, VmInstanceConstant.USER_VM_TYPE)
                .listValues();

        vmUuids.removeIf( v -> !destinationMaker.isManagedByUs(v));

        if (vmUuids.isEmpty()) {
            return;
        }

        List<SystemTagVO> vmPriorityTags = Q.New(SystemTagVO.class)
                .like(SystemTagVO_.tag, "vmPriority::%")
                .in(SystemTagVO_.resourceUuid, vmUuids)
                .list();

        List<String> hasTagVmUuids = vmPriorityTags.stream().map(SystemTagVO::getResourceUuid).collect(Collectors.toList());
        List<String> normalVmUuids = vmPriorityTags.stream().filter(t -> t.getTag().equals("vmPriority::" + VmPriorityLevel.Normal.name()))
                .map(SystemTagVO::getResourceUuid).collect(Collectors.toList());
        List<String> cpuHighVmUuids = vmPriorityTags.stream().filter(t -> t.getTag().equals("vmPriority::" + VmPriorityLevel.CpuHigh.name()))
                .map(SystemTagVO::getResourceUuid).collect(Collectors.toList());

        vmUuids.removeAll(hasTagVmUuids);

        VmPriorityConfigVO normalPriorityVO = Q.New(VmPriorityConfigVO.class).eq(VmPriorityConfigVO_.level, VmPriorityLevel.Normal).find();
        VmPriorityConfigVO cpuHighPriorityVO = Q.New(VmPriorityConfigVO.class).eq(VmPriorityConfigVO_.level, VmPriorityLevel.CpuHigh).find();

        // no tag vm, set to normal
        List<PriorityConfigStruct> priorityConfigStructs = new ArrayList<>();
        vmUuids.forEach(v -> {
            priorityConfigStructs.add(new PriorityConfigStruct(normalPriorityVO, v));
        });

        // normal level vm
        normalVmUuids.forEach(v -> {
            priorityConfigStructs.add(new PriorityConfigStruct(normalPriorityVO, v));
        });

        // cpu high level vm
        cpuHighVmUuids.forEach(v -> {
            priorityConfigStructs.add(new PriorityConfigStruct(cpuHighPriorityVO, v));
        });

        if (priorityConfigStructs.isEmpty()) {
            return;
        }

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

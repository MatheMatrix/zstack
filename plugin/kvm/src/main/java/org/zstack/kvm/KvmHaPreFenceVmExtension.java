package org.zstack.kvm;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.vm.VmSystemTags;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.header.core.Completion;
import org.zstack.header.host.HostConstant;
import org.zstack.header.message.MessageReply;
import org.zstack.header.vm.FenceVmOnHostMsg;
import org.zstack.header.vm.VmBeforeStartOnHypervisorExtensionPoint;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.List;

import static org.zstack.core.Platform.operr;

public class KvmHaPreFenceVmExtension implements VmBeforeStartOnHypervisorExtensionPoint {
    private static final CLogger logger = Utils.getLogger(KvmHaPreFenceVmExtension.class);

    @Autowired
    private CloudBus bus;

    @Override
    public void beforeStartVmOnHypervisor(VmInstanceSpec spec, Completion completion) {
        VmInstanceInventory vm = spec.getVmInventory();
        String vmUuid = vm.getUuid();

        if (!VmSystemTags.HA_PRE_FENCE_PENDING.hasTag(vmUuid)) {
            completion.success();
            return;
        }

        if (!KVMConstant.KVM_HYPERVISOR_TYPE.equals(vm.getHypervisorType())) {
            VmSystemTags.HA_PRE_FENCE_PENDING.deleteInherentTag(vmUuid);
            completion.success();
            return;
        }

        String destHostUuid = spec.getDestHost() != null ? spec.getDestHost().getUuid() : null;
        if (destHostUuid == null) {
            VmSystemTags.HA_PRE_FENCE_PENDING.deleteInherentTag(vmUuid);
            completion.success();
            return;
        }

        String suspectHostUuid = pickSuspectHostUuid(spec, vm);
        if (suspectHostUuid == null || suspectHostUuid.equals(destHostUuid)) {
            VmSystemTags.HA_PRE_FENCE_PENDING.deleteInherentTag(vmUuid);
            completion.success();
            return;
        }

        String siblingHostUuid = spec.getPreFenceSiblingHostUuid();
        if (siblingHostUuid == null) {
            VmSystemTags.HA_PRE_FENCE_PENDING.deleteInherentTag(vmUuid);
            completion.success();
            return;
        }

        if (siblingHostUuid.equals(suspectHostUuid)) {
            completion.fail(operr("HA-start vm[%s]: invalid pre-fence sibling host[%s] equals suspect host.",
                    vmUuid, suspectHostUuid));
            return;
        }

        FenceVmOnHostMsg fmsg = new FenceVmOnHostMsg();
        fmsg.setHostUuid(siblingHostUuid);
        fmsg.setSuspectHostUuid(suspectHostUuid);
        fmsg.setVmUuid(vmUuid);
        bus.makeTargetServiceIdByResourceUuid(fmsg, HostConstant.SERVICE_ID, siblingHostUuid);
        bus.send(fmsg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    completion.fail(reply.getError());
                    return;
                }

                logger.debug(String.format("[HA pre-fence] vm[%s] cleared by sibling[%s] for suspect[%s]",
                        vmUuid, siblingHostUuid, suspectHostUuid));
                VmSystemTags.HA_PRE_FENCE_PENDING.deleteInherentTag(vmUuid);
                completion.success();
            }
        });
    }

    private String pickSuspectHostUuid(VmInstanceSpec spec, VmInstanceInventory vm) {
        List<String> softAvoid = spec.getSoftAvoidHostUuids();
        if (softAvoid != null && !softAvoid.isEmpty()) {
            return softAvoid.get(0);
        }
        return vm.getLastHostUuid();
    }
}

package org.zstack.kvm;

import org.zstack.compute.vm.BeforeStopVmOnHypervisorExtensionPoint;
import org.zstack.compute.vm.VmSystemTags;
import org.zstack.header.vm.StopVmOnHypervisorMsg;
import org.zstack.header.vm.StopVmType;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

/**
 * Created by Wenhao.Zhang on 24-01-03
 */
public class KvmVmStopExtension implements BeforeStopVmOnHypervisorExtensionPoint {
    private static final CLogger logger = Utils.getLogger(KvmVmStopExtension.class);

    @Override
    public void beforeStopVmOnHypervisor(VmInstanceSpec spec, StopVmOnHypervisorMsg msg) {
        if (!VmSystemTags.NO_OPERATING_SYSTEM.hasTag(spec.getVmInventory().getUuid(), VmInstanceVO.class)) {
            return;
        }

        final String stopMode = msg.getType();
        if (!StopVmType.grace.toString().equals(stopMode)) {
            return;
        }

        logger.debug(String.format(
                "VM[uuid:%s] has no operation system. To avoid a long shutdown of the VM, use \"force\" mode to stop instead of \"%s\".",
                spec.getVmInventory().getUuid(), stopMode));
        msg.setType(StopVmType.force.toString());
    }
}

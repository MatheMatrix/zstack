package org.zstack.sdnController.znsproxy;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.core.workflow.Flow;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.host.HostException;
import org.zstack.header.host.HostInventory;
import org.zstack.header.host.HypervisorType;
import org.zstack.kvm.KVMConstant;
import org.zstack.kvm.KVMHostConnectExtensionPoint;
import org.zstack.kvm.KVMHostConnectedContext;
import org.zstack.sdnController.SdnControllerSystemTags;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.Map;

import static org.zstack.core.Platform.operr;

public class ZnsProxyKvmReconnectExtension implements KVMHostConnectExtensionPoint, org.zstack.header.host.HostConnectionReestablishExtensionPoint {
    private static final CLogger logger = Utils.getLogger(ZnsProxyKvmReconnectExtension.class);

    @Autowired
    private DatabaseFacade dbf;

    @Override
    public Flow createKvmHostConnectingFlow(final KVMHostConnectedContext context) {
        return new NoRollbackFlow() {
            String __name__ = "reinstall-zns-proxy-if-prepared";

            @Override
            public void run(FlowTrigger trigger, Map data) {
                try {
                    reinstallIfPrepared(context.getInventory().getUuid());
                    trigger.next();
                } catch (Exception e) {
                    trigger.fail(operr("reinstall zns-proxy on prepared host %s failed: %s",
                            context.getInventory().getUuid(), e.getMessage()));
                }
            }
        };
    }

    @Override
    public void connectionReestablished(HostInventory inv) throws HostException {
        try {
            reinstallIfPrepared(inv.getUuid());
        } catch (Exception e) {
            throw new HostException(String.format("reinstall zns-proxy on prepared host %s failed", inv.getUuid()), e);
        }
    }

    @Override
    public HypervisorType getHypervisorTypeForReestablishExtensionPoint() {
        return HypervisorType.valueOf(KVMConstant.KVM_HYPERVISOR_TYPE);
    }

    private void reinstallIfPrepared(String hostUuid) {
        if (!SdnControllerSystemTags.ZNS_PROXY_PREPARED.hasTag(hostUuid)) {
            logger.debug(String.format("[ZnsProxy] skip reconnect reinstall for host %s without prepared tag", hostUuid));
            return;
        }
        logger.info(String.format("[ZnsProxy] reconnect reinstall for prepared host %s", hostUuid));
        new ZnsProxyInstaller(dbf).reinstallPreparedHost(hostUuid);
    }
}

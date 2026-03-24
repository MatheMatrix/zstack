package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.Q;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.primary.CleanupVmInstanceMetadataOnPrimaryStorageMsg;
import org.zstack.header.storage.primary.PrimaryStorageConstant;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.storage.primary.PrimaryStorageVO_;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.header.vm.metadata.VmMetadataPathBuildExtensionPoint;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class VmExpungeMetadataFlow extends NoRollbackFlow {
    private static final CLogger logger = Utils.getLogger(VmExpungeMetadataFlow.class);
    private static final long CLEANUP_GC_INTERVAL_SEC = 300;

    @Autowired
    private CloudBus bus;
    @Autowired
    private PluginRegistry pluginRgty;

    @Override
    public void run(FlowTrigger trigger, Map data) {
        final VmInstanceSpec spec = (VmInstanceSpec) data.get(VmInstanceConstant.Params.VmInstanceSpec.toString());
        if (spec == null || spec.getVmInventory() == null) {
            logger.warn("[MetadataExpunge] missing VmInstanceSpec or VmInventory, skip metadata cleanup");
            trigger.next();
            return;
        }

        final String vmUuid = spec.getVmInventory().getUuid();

        String rootVolumeUuid = spec.getVmInventory().getRootVolumeUuid();
        if (rootVolumeUuid == null) {
            logger.debug(String.format("[MetadataExpunge] vm[uuid:%s] has no root volume, skipping metadata cleanup", vmUuid));
            trigger.next();
            return;
        }

        String psUuid = Q.New(VolumeVO.class).eq(VolumeVO_.uuid, rootVolumeUuid).select(VolumeVO_.primaryStorageUuid).findValue();
        if (psUuid == null) {
            logger.debug(String.format("[MetadataExpunge] vm[uuid:%s] root volume[uuid:%s] has no primaryStorageUuid, " +
                    "skipping metadata cleanup", vmUuid, rootVolumeUuid));
            trigger.next();
            return;
        }

        String psType = Q.New(PrimaryStorageVO.class).select(PrimaryStorageVO_.type).eq(PrimaryStorageVO_.uuid, psUuid).findValue();
        VmMetadataPathBuildExtensionPoint ext = pluginRgty.getExtensionFromMap(psType, VmMetadataPathBuildExtensionPoint.class);
        if (ext == null) {
            trigger.next();
            return;
        }
        final String metadataPath;
        try {
            metadataPath = ext.buildVmMetadataPath(psUuid, vmUuid);
        } catch (Exception e) {
            logger.warn(String.format("[MetadataExpunge] failed to build metadata path for vm[uuid:%s] on ps[uuid:%s], " +
                    "skip metadata cleanup: %s", vmUuid, psUuid, e.getMessage()));
            trigger.next();
            return;
        }

        CleanupVmInstanceMetadataOnPrimaryStorageMsg cmsg = new CleanupVmInstanceMetadataOnPrimaryStorageMsg();
        cmsg.setPrimaryStorageUuid(psUuid);
        cmsg.setVmUuid(vmUuid);
        cmsg.setMetadataPath(metadataPath);
        cmsg.setRootVolumeUuid(rootVolumeUuid);

        String hostUuid = spec.getVmInventory().getHostUuid();
        if (hostUuid == null) {
            hostUuid = spec.getVmInventory().getLastHostUuid();
        }
        cmsg.setHostUuid(hostUuid);

        final String finalPsUuid = psUuid;
        final String finalHostUuid = hostUuid;

        bus.makeTargetServiceIdByResourceUuid(cmsg, PrimaryStorageConstant.SERVICE_ID, psUuid);
        bus.send(cmsg, new CloudBusCallBack(trigger) {
            @Override
            public void run(MessageReply reply) {
                if (reply.isSuccess()) {
                    logger.info(String.format("[MetadataExpunge] successfully deleted metadata for vm[uuid:%s] on ps[uuid:%s]",
                            vmUuid, finalPsUuid));
                } else {
                    logger.warn(String.format("[MetadataExpunge] failed to delete metadata for vm[uuid:%s] on ps[uuid:%s]: %s, " +
                            "submitting GC job for retry", vmUuid, finalPsUuid, reply.getError()));
                    submitGC(finalPsUuid, vmUuid, rootVolumeUuid, metadataPath, finalHostUuid);
                }
                trigger.next();
            }
        });
    }

    private void submitGC(String psUuid, String vmUuid, String rootVolumeUuid, String metadataPath, String hostUuid) {
        CleanupVmInstanceMetadataOnPrimaryStorageGC gc = new CleanupVmInstanceMetadataOnPrimaryStorageGC();
        gc.NAME = CleanupVmInstanceMetadataOnPrimaryStorageGC.getGCName(vmUuid);
        gc.primaryStorageUuid = psUuid;
        gc.vmUuid = vmUuid;
        gc.rootVolumeUuid = rootVolumeUuid;
        gc.metadataPath = metadataPath;
        gc.hostUuid = hostUuid;
        gc.deduplicateSubmit(CLEANUP_GC_INTERVAL_SEC, TimeUnit.SECONDS);

        logger.info(String.format("[MetadataExpunge] submitted GC job [%s] for vm[uuid:%s] on ps[uuid:%s], " +
                        "retry interval: %d seconds", gc.NAME, vmUuid, psUuid,
                CLEANUP_GC_INTERVAL_SEC));
    }
}
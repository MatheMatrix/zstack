package org.zstack.compute.vm;

import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.gc.GC;
import org.zstack.core.gc.GCCompletion;
import org.zstack.core.gc.TimeBasedGarbageCollector;
import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.primary.CleanupVmInstanceMetadataOnPrimaryStorageMsg;
import org.zstack.header.storage.primary.PrimaryStorageConstant;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

public class CleanupVmInstanceMetadataOnPrimaryStorageGC extends TimeBasedGarbageCollector {
    private static final CLogger logger = Utils.getLogger(CleanupVmInstanceMetadataOnPrimaryStorageGC.class);

    @GC
    public String primaryStorageUuid;
    @GC
    public String vmUuid;
    @GC
    public String rootVolumeUuid;
    @GC
    public String metadataPath;
    @GC
    public String hostUuid;

    public static String getGCName(String vmUuid) {
        return String.format("gc-cleanup-vm-metadata-%s", vmUuid);
    }

    @Override
    protected void triggerNow(GCCompletion completion) {
        if (!dbf.isExist(primaryStorageUuid, PrimaryStorageVO.class)) {
            logger.debug(String.format("[MetadataCleanupGC] primary storage[uuid:%s] no longer exists, " +
                    "cancel gc for vm[uuid:%s]", primaryStorageUuid, vmUuid));
            completion.cancel();
            return;
        }

        CleanupVmInstanceMetadataOnPrimaryStorageMsg msg = new CleanupVmInstanceMetadataOnPrimaryStorageMsg();
        msg.setPrimaryStorageUuid(primaryStorageUuid);
        msg.setVmUuid(vmUuid);
        msg.setRootVolumeUuid(rootVolumeUuid);
        msg.setMetadataPath(metadataPath);
        msg.setHostUuid(hostUuid);

        bus.makeTargetServiceIdByResourceUuid(msg, PrimaryStorageConstant.SERVICE_ID, primaryStorageUuid);
        bus.send(msg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (reply.isSuccess()) {
                    logger.info(String.format("[MetadataCleanupGC] successfully cleaned up metadata " +
                            "for vm[uuid:%s] on ps[uuid:%s]", vmUuid, primaryStorageUuid));
                    completion.success();
                } else {
                    logger.warn(String.format("[MetadataCleanupGC] failed to clean up metadata " +
                            "for vm[uuid:%s] on ps[uuid:%s]: %s", vmUuid, primaryStorageUuid, reply.getError()));
                    completion.fail(reply.getError());
                }
            }
        });
    }
}

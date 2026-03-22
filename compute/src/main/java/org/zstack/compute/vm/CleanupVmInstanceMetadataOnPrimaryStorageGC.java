package org.zstack.compute.vm;

import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.db.Q;
import org.zstack.core.gc.GC;
import org.zstack.core.gc.GCCompletion;
import org.zstack.core.gc.TimeBasedGarbageCollector;
import org.zstack.header.host.HostVO;
import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.primary.CleanupVmInstanceMetadataOnPrimaryStorageMsg;
import org.zstack.header.storage.primary.PrimaryStorageAO_;
import org.zstack.header.storage.primary.PrimaryStorageConstant;
import org.zstack.header.storage.primary.PrimaryStorageConstants;
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

    public static String getGCName(String vmUuid, String primaryStorageUuid) {
        return String.format("gc-cleanup-vm-metadata-%s-%s", vmUuid, primaryStorageUuid);
    }

    @Override
    protected void triggerNow(GCCompletion completion) {
        if (!dbf.isExist(primaryStorageUuid, PrimaryStorageVO.class)) {
            logger.debug(String.format("[MetadataCleanupGC] primary storage[uuid:%s] no longer exists, " +
                    "cancel gc for vm[uuid:%s]", primaryStorageUuid, vmUuid));
            completion.cancel();
            return;
        }

        if (hostUuid != null && !dbf.isExist(hostUuid, HostVO.class)) {
            String psType = Q.New(PrimaryStorageVO.class)
                    .select(PrimaryStorageAO_.type)
                    .eq(PrimaryStorageAO_.uuid, primaryStorageUuid)
                    .findValue();

            if (PrimaryStorageConstants.LOCAL_STORAGE_TYPE.equals(psType)) {
                logger.debug(String.format("[MetadataCleanupGC] host[uuid:%s] no longer exists " +
                        "and primary storage[uuid:%s] is LocalStorage, cancel gc for vm[uuid:%s]",
                        hostUuid, primaryStorageUuid, vmUuid));
                completion.cancel();
                return;
            }

            logger.info(String.format("[MetadataCleanupGC] host[uuid:%s] no longer exists for vm[uuid:%s], " +
                    "clear hostUuid and let the primary storage backend pick an available host", hostUuid, vmUuid));
            hostUuid = null;
        }

        CleanupVmInstanceMetadataOnPrimaryStorageMsg msg = new CleanupVmInstanceMetadataOnPrimaryStorageMsg();
        msg.setPrimaryStorageUuid(primaryStorageUuid);
        msg.setVmInstanceUuid(vmUuid);
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

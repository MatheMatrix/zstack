package org.zstack.storage.primary.local;

import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.gc.GC;
import org.zstack.core.gc.GCCompletion;
import org.zstack.core.gc.TimeBasedGarbageCollector;
import org.zstack.header.message.MessageReply;
import org.zstack.header.host.HostVO;
import org.zstack.header.storage.primary.PrimaryStorageConstant;
import org.zstack.header.storage.primary.PrimaryStorageVO;

public class LocalStorageReturnHostCapacityGC extends TimeBasedGarbageCollector {
    @GC
    public String primaryStorageUuid;
    @GC
    public String hostUuid;
    @GC
    public long size;
    @GC
    public boolean noOverProvisioning;

    @Override
    protected void triggerNow(GCCompletion completion) {
        if (!dbf.isExist(primaryStorageUuid, PrimaryStorageVO.class)) {
            completion.cancel();
            return;
        }
        if (!dbf.isExist(hostUuid, HostVO.class)) {
            completion.cancel();
            return;
        }

        LocalStorageReturnHostCapacityMsg msg = new LocalStorageReturnHostCapacityMsg();
        msg.setPrimaryStorageUuid(primaryStorageUuid);
        msg.setHostUuid(hostUuid);
        msg.setSize(size);
        msg.setNoOverProvisioning(noOverProvisioning);
        bus.makeTargetServiceIdByResourceUuid(msg, PrimaryStorageConstant.SERVICE_ID, primaryStorageUuid);
        bus.send(msg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (reply.isSuccess()) {
                    completion.success();
                } else {
                    completion.fail(reply.getError());
                }
            }
        });
    }
}

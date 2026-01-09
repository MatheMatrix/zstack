package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.gc.GC;
import org.zstack.core.gc.GCCompletion;
import org.zstack.core.gc.TimeBasedGarbageCollector;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.core.progress.ChainInfo;
import org.zstack.header.message.MessageReply;
import org.zstack.header.vm.UpdateVmInstanceMetadataMsg;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.header.vm.VmInstanceVO;

public class UpdateVmInstanceMetadataGC extends TimeBasedGarbageCollector {
    @GC
    public String vmInstanceUuid;

    @Autowired
    protected ThreadFacade thdf;

    static public String getUpdateVmInstanceMetadataSyncSignature(String vmInstanceUuid) {
        return String.format("update-vm-%s-metadata", vmInstanceUuid);
    }

    @Override
    protected void triggerNow(GCCompletion completion) {
        if (!dbf.isExist(vmInstanceUuid, VmInstanceVO.class)) {
            completion.cancel();
            return;
        }

        String queueName = getUpdateVmInstanceMetadataSyncSignature(vmInstanceUuid);
        ChainInfo chainInfo = thdf.getChainTaskInfo(queueName);
        if (!chainInfo.getPendingTask().isEmpty()) {
            completion.cancel();
            return;
        }

        thdf.chainSubmit(new ChainTask(completion) {
            @Override
            public String getSyncSignature() {
                return queueName;
            }

            @Override
            public void run(final SyncTaskChain chain) {
                UpdateVmInstanceMetadataMsg msg = new UpdateVmInstanceMetadataMsg();
                msg.setUuid(vmInstanceUuid);
                bus.makeTargetServiceIdByResourceUuid(msg, VmInstanceConstant.SERVICE_ID, vmInstanceUuid);
                bus.send(msg, new CloudBusCallBack(completion) {
                    @Override
                    public void run(MessageReply reply) {
                        if (!reply.isSuccess()) {
                            completion.fail(reply.getError());
                        } else {
                            completion.success();
                        }
                        chain.next();
                    }
                });
            }

            @Override
            public String getName() {
                return queueName;
            }
        });
    }
}

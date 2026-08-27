package org.zstack.storage.backup;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.AbstractService;
import org.zstack.header.message.Message;
import org.zstack.header.message.MessageReply;
import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.backup.BackupMessage;

import java.util.LinkedHashMap;

import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.ORG_ZSTACK_CORE_CLOUDBUS_10002;

public class BackupMessageManagerImpl extends AbstractService implements BackupMessageManager {
    @Autowired
    private CloudBus bus;
    @Autowired
    private ThreadFacade thdf;

    @Override
    public boolean routeToQueue(NeedReplyMessage msg, String vmUuid, String targetServiceId) {
        if (vmUuid == null || BackupMessage.hasQueued(msg)) {
            return false;
        }

        NeedReplyMessage originalRequest = (NeedReplyMessage) msg.clone();
        originalRequest.setHeaders(new LinkedHashMap<>(msg.getHeaders()));
        sendToQueue(msg, vmUuid, targetServiceId, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                bus.reply(originalRequest, reply);
            }
        });
        return true;
    }

    private void sendToQueue(NeedReplyMessage msg, String vmUuid, String targetServiceId,
                             CloudBusCallBack callback) {
        BackupMessage bmsg = BackupMessage.valueOf(vmUuid, targetServiceId, msg);
        bus.makeTargetServiceIdByResourceUuid(bmsg, BackupMessage.SERVICE_ID, vmUuid);
        bus.send(bmsg, callback);
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg instanceof BackupMessage) {
            handle((BackupMessage) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    private void handle(BackupMessage msg) {
        thdf.chainSubmit(new ChainTask(msg) {
            @Override
            public String getSyncSignature() {
                return String.format(BackupMessage.SYNC_SIGNATURE, msg.getVmInstanceUuid());
            }

            @Override
            public void run(SyncTaskChain chain) {
                if (msg.getMessageDeadline() != -1 &&
                        msg.getMessageDeadline() <= System.currentTimeMillis()) {
                    MessageReply reply = new MessageReply();
                    reply.setError(Platform.touterr(ORG_ZSTACK_CORE_CLOUDBUS_10002, msg.toErrorString()));
                    bus.reply(msg, reply);
                    chain.next();
                    return;
                }

                NeedReplyMessage inner = msg.getMessage();
                inner.setId(Platform.getUuid());
                if (inner.getMessageDeadline() == -1) {
                    inner.setMessageDeadline(msg.getMessageDeadline());
                }
                if (inner.getMessageDeadline() != -1) {
                    inner.setTimeout(Math.max(1L,
                            inner.getMessageDeadline() - System.currentTimeMillis()));
                }
                bus.makeLocalServiceId(inner, msg.getTargetServiceId());
                bus.send(inner, new CloudBusCallBack(msg, chain) {
                    @Override
                    public void run(MessageReply reply) {
                        bus.reply(msg, reply);
                        chain.next();
                    }
                });
            }

            @Override
            public String getName() {
                return msg.getTaskName();
            }
        });
    }

    @Override
    public String getId() {
        return bus.makeLocalServiceId(BackupMessage.SERVICE_ID);
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }
}

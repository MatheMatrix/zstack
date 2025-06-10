package org.zstack.sshkeypair;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.MessageSafe;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SQL;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.core.upgrade.GrayVersion;
import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.HostConstant;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;
import org.zstack.header.message.MessageReply;
import org.zstack.header.sshkeypair.*;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.kvm.KVMAgentCommands;
import org.zstack.kvm.KVMHostAsyncHttpCallMsg;
import org.zstack.kvm.KVMHostAsyncHttpCallReply;

import java.sql.Timestamp;

import static org.zstack.core.Platform.operr;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class SshKeyPairBase {
    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    protected ThreadFacade thdf;

    protected SshKeyPairVO self;

    public SshKeyPairBase(SshKeyPairVO self) {
        this.self = self;
    }

    @MessageSafe
    void handleMessage(Message msg) {
        if (msg instanceof APIMessage) {
            handleAPIMessage((APIMessage) msg);
        } else {
            handleLocalMessage(msg);
        }
    }

    private void handleLocalMessage(Message msg) {
        if (msg instanceof AttachSshKeyPairToVmInstanceMsg) {
            handle((AttachSshKeyPairToVmInstanceMsg) msg);
        } else if (msg instanceof DetachSshKeyPairFromVmInstanceMsg) {
            handle((DetachSshKeyPairFromVmInstanceMsg) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    private void handleAPIMessage(APIMessage msg) {
        if (msg instanceof APIUpdateSshKeyPairMsg) {
            handle((APIUpdateSshKeyPairMsg) msg);
        } else if (msg instanceof APIDeleteSshKeyPairMsg) {
            handle((APIDeleteSshKeyPairMsg) msg);
        } else if (msg instanceof APIAttachSshKeyPairToVmInstanceMsg) {
            handle((APIAttachSshKeyPairToVmInstanceMsg) msg);
        } else if (msg instanceof APIDetachSshKeyPairFromVmInstanceMsg) {
            handle((APIDetachSshKeyPairFromVmInstanceMsg) msg);
        } else {
            bus.dealWithUnknownMessage(msg);
        }
    }

    public void handle(APIUpdateSshKeyPairMsg msg) {
        APIUpdateSshKeyPairEvent event = new APIUpdateSshKeyPairEvent(msg.getId());
        thdf.chainSubmit(new ChainTask(msg) {
            @Override
            public void run(SyncTaskChain chain) {
                updateSshKeyPair(msg, event, new Completion(chain) {
                    @Override
                    public void success() {
                        bus.publish(event);
                        chain.next();
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        event.setError(errorCode);
                        bus.publish(event);
                        chain.next();
                    }
                });
            }
            @Override
            public String getSyncSignature() {
                return String.format("%s-%s", SshKeyPairConstant.OPERATE_SSH_KEY_PAIR_THREAD_NAME, msg.getSshKeyPairUuid());
            }

            @Override
            public String getName() {
                return String.format("update-sshKeyPair-%s", msg.getSshKeyPairUuid());
            }
        });
    }

    private void updateSshKeyPair(APIUpdateSshKeyPairMsg msg, APIUpdateSshKeyPairEvent event, Completion completion) {
        SshKeyPairVO vo = dbf.findByUuid(msg.getUuid(), SshKeyPairVO.class);

        boolean updated = false;

        if (msg.getName() != null) {
            vo.setName(msg.getName());
            updated = true;
        }
        if (msg.getDescription() != null) {
            vo.setDescription(msg.getDescription());
            updated = true;
        }

        if (updated) {
            vo = dbf.updateAndRefresh(vo);
        }
        event.setInventory(SshKeyPairInventory.valueOf(vo));
        completion.success();
    }

    public void handle(APIDeleteSshKeyPairMsg msg) {
        APIDeleteSshKeyPairEvent event = new APIDeleteSshKeyPairEvent(msg.getId());
        thdf.chainSubmit(new ChainTask(msg) {
            @Override
            public void run(SyncTaskChain chain) {
                deleteSshKeyPair(msg, event, new Completion(chain) {
                    @Override
                    public void success() {
                        bus.publish(event);
                        chain.next();
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        event.setError(errorCode);
                        bus.publish(event);
                        chain.next();
                    }
                });
            }
            @Override
            public String getSyncSignature() {
                return String.format("%s-%s", SshKeyPairConstant.OPERATE_SSH_KEY_PAIR_THREAD_NAME, msg.getSshKeyPairUuid());
            }

            @Override
            public String getName() {
                return String.format("delete-sshKeyPair-%s", msg.getUuid());
            }
        });
    }

    private void deleteSshKeyPair(APIDeleteSshKeyPairMsg msg, APIDeleteSshKeyPairEvent event, Completion completion) {
        SshKeyPairVO vo = dbf.findByUuid(msg.getUuid(), SshKeyPairVO.class);

        dbf.remove(vo);

        completion.success();
    }

    public void handle(APIAttachSshKeyPairToVmInstanceMsg msg) {
        AttachSshKeyPairToVmInstanceMsg attachSshKeyPairToVmInstanceMsg = new AttachSshKeyPairToVmInstanceMsg();
        attachSshKeyPairToVmInstanceMsg.setSshKeyPairUuid(msg.getSshKeyPairUuid());
        attachSshKeyPairToVmInstanceMsg.setVmInstanceUuid(msg.getVmInstanceUuid());
        bus.makeTargetServiceIdByResourceUuid(attachSshKeyPairToVmInstanceMsg, SshKeyPairConstant.SERVICE_ID, msg.getSshKeyPairUuid());
        bus.send(attachSshKeyPairToVmInstanceMsg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                APIAttachSshKeyPairToVmInstanceEvent event = new APIAttachSshKeyPairToVmInstanceEvent(msg.getId());
                if (!reply.isSuccess()) {
                    event.setError(reply.getError());
                }
                bus.publish(event);
            }
        });
    }

    public static class AttachSshKeyPairToVmInstanceCommand extends KVMAgentCommands.AgentCommand {
        @GrayVersion(value = "5.0.0")
        public String sshKeyPairUuid;
        @GrayVersion(value = "5.0.0")
        public String vmInstanceUuid;
        @GrayVersion(value = "5.0.0")
        public String publicKey;

        public String getSshKeyPairUuid() {
            return sshKeyPairUuid;
        }

        public void setSshKeyPairUuid(String sshKeyPairUuid) {
            this.sshKeyPairUuid = sshKeyPairUuid;
        }

        public String getVmInstanceUuid() {
            return vmInstanceUuid;
        }

        public void setVmInstanceUuid(String vmInstanceUuid) {
            this.vmInstanceUuid = vmInstanceUuid;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }
    }

    public static class AttachSshKeyPairToVmInstanceRsp extends KVMAgentCommands.AgentResponse {
    }

    public void handle(APIDetachSshKeyPairFromVmInstanceMsg msg) {
        DetachSshKeyPairFromVmInstanceMsg detachSshKeyPairFromVmInstanceMsg = new DetachSshKeyPairFromVmInstanceMsg();
        detachSshKeyPairFromVmInstanceMsg.setSshKeyPairUuid(msg.getSshKeyPairUuid());
        detachSshKeyPairFromVmInstanceMsg.setVmInstanceUuid(msg.getVmInstanceUuid());
        bus.makeTargetServiceIdByResourceUuid(detachSshKeyPairFromVmInstanceMsg, SshKeyPairConstant.SERVICE_ID, msg.getSshKeyPairUuid());
        bus.send(detachSshKeyPairFromVmInstanceMsg, new CloudBusCallBack(msg) {
            @Override
            public void run(MessageReply reply) {
                APIDetachSshKeyPairFromVmInstanceEvent event = new APIDetachSshKeyPairFromVmInstanceEvent(msg.getId());
                if (!reply.isSuccess()) {
                    event.setError(reply.getError());
                }
                bus.publish(event);
            }
        });
    }

    private void handle(AttachSshKeyPairToVmInstanceMsg msg) {
        AttachSshKeyPairToVmInstanceReply reply = new AttachSshKeyPairToVmInstanceReply();
        SshKeyPairVO keyPair = dbf.findByUuid(msg.getSshKeyPairUuid(), SshKeyPairVO.class);
        VmInstanceVO instance = dbf.findByUuid(msg.getVmInstanceUuid(), VmInstanceVO.class);
        // **reuse the original attach logic via the same ChainTask**
        thdf.chainSubmit(new ChainTask(msg) {
            @Override
            public void run(SyncTaskChain chain) {
                AttachSshKeyPairToVmInstanceCommand cmd = new AttachSshKeyPairToVmInstanceCommand();
                cmd.setSshKeyPairUuid(msg.getSshKeyPairUuid());
                cmd.setVmInstanceUuid(msg.getVmInstanceUuid());
                cmd.setPublicKey(keyPair.getPublicKey());

                KVMHostAsyncHttpCallMsg smsg = new KVMHostAsyncHttpCallMsg();
                smsg.setCommand(cmd);
                smsg.setHostUuid(instance.getHostUuid());
                smsg.setPath(SshKeyPairConstant.SSH_KEY_PAIR_ATTACH_TO_VM);

                bus.makeTargetServiceIdByResourceUuid(smsg, HostConstant.SERVICE_ID, instance.getHostUuid());
                bus.send(smsg, new CloudBusCallBack(chain) {
                    @Override
                    public void run(MessageReply rpy) {
                        if (rpy.isSuccess()) {
                            KVMHostAsyncHttpCallReply r = rpy.castReply();
                            AttachSshKeyPairToVmInstanceRsp rsp = r.toResponse(AttachSshKeyPairToVmInstanceRsp.class);
                            if (rsp.isSuccess()) {
                                SshKeyPairRefVO ref = new SshKeyPairRefVO();
                                ref.setSshKeyPairUuid(msg.getSshKeyPairUuid());
                                ref.setResourceUuid(msg.getVmInstanceUuid());
                                ref.setResourceType(VmInstanceVO.class.getSimpleName());
                                ref.setCreateDate(new Timestamp(System.currentTimeMillis()));
                                dbf.persist(ref);
                            } else {
                                reply.setError(operr("operation error, because: %s", rsp.getError()));
                            }
                        } else {
                            reply.setError(rpy.getError());
                        }
                        bus.reply(msg, reply);
                        chain.next();
                    }
                });
            }
            @Override public String getSyncSignature() {
                return String.format("%s-%s", SshKeyPairConstant.OPERATE_SSH_KEY_PAIR_THREAD_NAME, msg.getSshKeyPairUuid());
            }
            @Override public String getName() {
                return String.format("attach-sshKeyPair-%s", msg.getSshKeyPairUuid());
            }
        });
    }

    private void handle(DetachSshKeyPairFromVmInstanceMsg msg) {
        DetachSshKeyPairFromVmInstanceReply reply = new DetachSshKeyPairFromVmInstanceReply();
        SshKeyPairVO keyPair = dbf.findByUuid(msg.getSshKeyPairUuid(), SshKeyPairVO.class);
        VmInstanceVO instance = dbf.findByUuid(msg.getVmInstanceUuid(), VmInstanceVO.class);
        thdf.chainSubmit(new ChainTask(msg) {
            @Override
            public void run(SyncTaskChain chain) {
                DetachSshKeyPairFromVmInstanceCommand cmd = new DetachSshKeyPairFromVmInstanceCommand();
                cmd.setSshKeyPairUuid(msg.getSshKeyPairUuid());
                cmd.setVmInstanceUuid(msg.getVmInstanceUuid());
                cmd.setPublicKey(keyPair.getPublicKey());

                KVMHostAsyncHttpCallMsg smsg = new KVMHostAsyncHttpCallMsg();
                smsg.setCommand(cmd);
                smsg.setHostUuid(instance.getHostUuid());
                smsg.setPath(SshKeyPairConstant.SSH_KEY_PAIR_DETACH_FROM_VM);

                bus.makeTargetServiceIdByResourceUuid(smsg, HostConstant.SERVICE_ID, instance.getHostUuid());
                bus.send(smsg, new CloudBusCallBack(chain) {
                    @Override
                    public void run(MessageReply rpy) {
                        if (rpy.isSuccess()) {
                            KVMHostAsyncHttpCallReply r = rpy.castReply();
                            DetachSshKeyPairFromVmInstanceRsp rsp = r.toResponse(DetachSshKeyPairFromVmInstanceRsp.class);
                            if (rsp.isSuccess()) {
                                SQL.New(SshKeyPairRefVO.class)
                                        .eq(SshKeyPairRefVO_.sshKeyPairUuid, msg.getSshKeyPairUuid())
                                        .eq(SshKeyPairRefVO_.resourceUuid, msg.getVmInstanceUuid())
                                        .eq(SshKeyPairRefVO_.resourceType, VmInstanceVO.class.getSimpleName())
                                        .delete();
                            } else {
                                reply.setError(operr("operation error, because: %s", rsp.getError()));
                            }
                        } else {
                            reply.setError(rpy.getError());
                        }
                        bus.reply(msg, reply);
                        chain.next();
                    }
                });
            }
            @Override public String getSyncSignature() {
                return String.format("%s-%s", SshKeyPairConstant.OPERATE_SSH_KEY_PAIR_THREAD_NAME, msg.getSshKeyPairUuid());
            }
            @Override public String getName() {
                return String.format("detach-sshKeyPair-%s", msg.getSshKeyPairUuid());
            }
        });
    }

    public static class DetachSshKeyPairFromVmInstanceCommand extends KVMAgentCommands.AgentCommand {
        @GrayVersion(value = "5.0.0")
        public String sshKeyPairUuid;
        @GrayVersion(value = "5.0.0")
        public String vmInstanceUuid;
        @GrayVersion(value = "5.0.0")
        public String publicKey;

        public String getSshKeyPairUuid() {
            return sshKeyPairUuid;
        }

        public void setSshKeyPairUuid(String sshKeyPairUuid) {
            this.sshKeyPairUuid = sshKeyPairUuid;
        }

        public String getVmInstanceUuid() {
            return vmInstanceUuid;
        }

        public void setVmInstanceUuid(String vmInstanceUuid) {
            this.vmInstanceUuid = vmInstanceUuid;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }
    }

    public static class DetachSshKeyPairFromVmInstanceRsp extends KVMAgentCommands.AgentResponse {
    }
}

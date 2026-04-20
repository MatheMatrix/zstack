package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.*;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.message.MessageReply;
import org.zstack.header.network.l3.*;
import org.zstack.header.vm.*;
import org.zstack.core.Platform;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.tag.TagManager;
import org.zstack.utils.ExceptionDSL;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;
import org.zstack.network.l3.L3NetworkManager;

import javax.persistence.PersistenceException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles VM NIC API messages: create, get attached network service, delete.
 * Extracted from VmInstanceManagerImpl to reduce God Class complexity.
 */
public class VmNicApiSubManager {
    private static final CLogger logger = Utils.getLogger(VmNicApiSubManager.class);

    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private TagManager tagMgr;
    @Autowired
    private ThreadFacade thdf;
    @Autowired
    protected L3NetworkManager l3nm;

    void handle(APICreateVmNicMsg msg) {
        final APICreateVmNicEvent evt = new APICreateVmNicEvent(msg.getId());
        VmNicInventory nic = new VmNicInventory();
        VmNicVO nicVO = new VmNicVO();
        List<UsedIpInventory> ips = new ArrayList<>();

        FlowChain flowChain = FlowChainBuilder.newSimpleFlowChain();
        flowChain.setName(String.format("create-nic-on-l3-network-%s", msg.getL3NetworkUuid()));
        // DEBT: NoRollbackFlow -- create NIC and persist to DB, MAC retry on duplicate. Tracked: TODO
        flowChain.then(new NoRollbackFlow() {
            String __name__ = "create-nic-and-presist-to-db";

            @Override
            public void run(FlowTrigger trigger, Map data) {
                int deviceId = 1;
                String mac = MacOperator.generateMacWithDeviceId((short) deviceId);
                nic.setUuid(Platform.getUuid());
                nic.setMac(mac);
                nic.setDeviceId(deviceId);
                nic.setType(VmInstanceConstant.VIRTUAL_NIC_TYPE);
                for (NicManageExtensionPoint ext : pluginRgty.getExtensionList(NicManageExtensionPoint.class)) {
                    ext.beforeCreateNic(nic, msg);
                }

                nicVO.setUuid(nic.getUuid());
                nicVO.setDeviceId(deviceId);
                nicVO.setMac(nic.getMac());
                nicVO.setAccountUuid(msg.getSession().getAccountUuid());
                nicVO.setType(nic.getType());

                int tries = 5;
                while (tries-- > 0) {
                    try {
                        new SQLBatch() {
                            @Override
                            protected void scripts() {
                                persist(nicVO);
                            }
                        }.execute();
                        break;
                    } catch (PersistenceException e) {
                        if (ExceptionDSL.isCausedBy(e, SQLIntegrityConstraintViolationException.class, "Duplicate entry")) {
                            logger.debug(String.format("Concurrent mac allocation. Mac[%s] has been allocated, try allocating another one. " +
                                    "The error[Duplicate entry] printed by jdbc.spi.SqlExceptionHelper is no harm, " +
                                    "we will try finding another mac", nicVO.getMac()));
                            logger.trace("", e);
                            nicVO.setMac(MacOperator.generateMacWithDeviceId((short) nicVO.getDeviceId()));
                        } else {
                            throw e;
                        }
                    }
                }

                trigger.next();
            }
        }).then(new Flow() {
            String __name__ = "allocate-nic-ip-and-mac";

            @Override
            public void run(FlowTrigger trigger, Map data) {
                List<ErrorCode> errors = new ArrayList<>();
                L3NetworkVO l3NetworkVO = dbf.findByUuid(msg.getL3NetworkUuid(), L3NetworkVO.class);
                new While<>(l3NetworkVO.getIpVersions()).each((version, wcomp) -> {
                    AllocateIpMsg allocateIpMsg = new AllocateIpMsg();
                    allocateIpMsg.setL3NetworkUuid(msg.getL3NetworkUuid());
                    allocateIpMsg.setRequiredIp(msg.getIp());
                    if (msg.getIp() == null) {
                        allocateIpMsg.setRequiredIp(nic.getIp());
                    }
                    allocateIpMsg.setIpVersion(version);
                    l3nm.updateIpAllocationMsg(allocateIpMsg, nic.getMac());
                    bus.makeTargetServiceIdByResourceUuid(allocateIpMsg, L3NetworkConstant.SERVICE_ID, msg.getL3NetworkUuid());

                    bus.send(allocateIpMsg, new CloudBusCallBack(wcomp) {
                        @Override
                        public void run(MessageReply reply) {
                            if (!reply.isSuccess()) {
                                errors.add(reply.getError());
                                wcomp.allDone();
                                return;
                            }

                            AllocateIpReply aReply = reply.castReply();
                            UsedIpInventory ipInventory = aReply.getIpInventory();
                            ips.add(ipInventory);
                            for (VmNicExtensionPoint ext : pluginRgty.getExtensionList(VmNicExtensionPoint.class)) {
                                ext.afterAddIpAddress(nic.getUuid(), ipInventory.getUuid());
                            }

                            if (nic.getL3NetworkUuid() == null) {
                                nic.setL3NetworkUuid(aReply.getIpInventory().getL3NetworkUuid());
                            }
                            if (nic.getUsedIpUuid() == null) {
                                nic.setUsedIpUuid(aReply.getIpInventory().getUuid());
                            }
                            /* TODO, to support nic driver type*/
                            wcomp.done();
                        }
                    });
                }).run(new WhileDoneCompletion(trigger) {
                    @Override
                    public void done(ErrorCodeList errorCodeList) {
                        if (errors.size() > 0) {
                            trigger.fail(errors.get(0));
                        } else {
                            trigger.next();
                        }
                    }
                });

            }

            @Override
            public void rollback(FlowRollback trigger, Map data) {
                if (!ips.isEmpty()) {
                    List<ReturnIpMsg> rmsgs = new ArrayList<>();
                    for (UsedIpInventory ip : ips) {
                        ReturnIpMsg rmsg = new ReturnIpMsg();
                        rmsg.setL3NetworkUuid(ip.getL3NetworkUuid());
                        rmsg.setUsedIpUuid(ip.getUuid());
                        bus.makeTargetServiceIdByResourceUuid(rmsg, L3NetworkConstant.SERVICE_ID, ip.getL3NetworkUuid());
                        rmsgs.add(rmsg);
                    }

                    new While<>(rmsgs).step((rmsg, wcomp) -> {
                        bus.send(rmsg, new CloudBusCallBack(wcomp) {
                            @Override
                            public void run(MessageReply reply) {
                                wcomp.done();

                            }
                        });
                    }, 2).run(new WhileDoneCompletion(trigger) {
                        @Override
                        public void done(ErrorCodeList errorCodeList) {
                            dbf.removeByPrimaryKey(nic.getUuid(), VmNicVO.class);
                            trigger.rollback();
                        }
                    });
                } else {
                    dbf.removeByPrimaryKey(nic.getUuid(), VmNicVO.class);
                    trigger.rollback();
                }
            }
        });

        flowChain.done(new FlowDoneHandler(msg) {
            @Override
            public void handle(Map data) {
                tagMgr.createTagsFromAPICreateMessage(msg, nic.getUuid(), VmNicVO.class.getSimpleName());
                evt.setInventory(VmNicInventory.valueOf(dbf.reload(nicVO)));
                bus.publish(evt);
            }
        }).error(new FlowErrorHandler(msg) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                evt.setError(errCode);
                bus.publish(evt);
            }
        }).start();
    }

    void handle(APIGetVmNicAttachedNetworkServiceMsg msg) {
        APIGetVmNicAttachedNetworkServiceReply reply = new APIGetVmNicAttachedNetworkServiceReply();
        List<String> networkServices = new ArrayList<>();
        VmNicVO nicVO = Q.New(VmNicVO.class).eq(VmNicVO_.uuid, msg.getVmNicUuid()).find();
        for (VmNicChangeNetworkExtensionPoint extension : pluginRgty.getExtensionList(VmNicChangeNetworkExtensionPoint.class)) {
            Map<String, String> ret = extension.getVmNicAttachedNetworkService(VmNicInventory.valueOf(nicVO));
            if (ret == null) {
                continue;
            }
            networkServices.addAll(ret.keySet());
        }
        reply.setNetworkServices(networkServices);
        bus.reply(msg, reply);
    }

    void handle(final APIDeleteVmNicMsg msg) {
        APIDeleteVmNicEvent evt = new APIDeleteVmNicEvent(msg.getId());

        VmNicVO nicVO = Q.New(VmNicVO.class).eq(VmNicVO_.uuid, msg.getUuid()).find();
        doDeleteVmNic(VmNicInventory.valueOf(nicVO), new Completion(msg) {
            @Override
            public void success() {
                bus.publish(evt);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                evt.setError(errorCode);
                bus.publish(evt);
            }
        });
    }

    private void doDeleteVmNic(VmNicInventory nic, Completion completion) {
        thdf.chainSubmit(new ChainTask(completion) {
            @Override
            public String getSyncSignature() {
                return getVmNicSyncSignature(nic.getUuid());
            }

            @Override
            public void run(SyncTaskChain chain) {
                if (nic.getVmInstanceUuid() == null) {
                    FlowChain fchain = FlowChainBuilder.newSimpleFlowChain();
                    fchain.setName(String.format("detach-network-service-from-vmnic-%s", nic.getUuid()));
                    for (ReleaseNetworkServiceOnDeletingNicExtensionPoint ext : pluginRgty.getExtensionList(ReleaseNetworkServiceOnDeletingNicExtensionPoint.class)) {
                        // DEBT: NoRollbackFlow -- release network service on deleting NIC. Tracked: TODO
                        fchain.then(new NoRollbackFlow() {
                            @Override
                            public void run(FlowTrigger trigger, Map data) {
                                ext.releaseNetworkServiceOnDeletingNic(nic, new NoErrorCompletion(trigger) {
                                    @Override
                                    public void done() {
                                        trigger.next();
                                    }
                                });
                            }
                        });
                    }
                    // DEBT: NoRollbackFlow -- return IP and delete NIC from DB. Tracked: TODO
                    fchain.then(new NoRollbackFlow() {
                        @Override
                        public void run(FlowTrigger trigger, Map data) {
                            List<ReturnIpMsg> msgs = new ArrayList<>();
                            for (UsedIpInventory ip : nic.getUsedIps()) {
                                ReturnIpMsg returnIpMsg = new ReturnIpMsg();
                                returnIpMsg.setUsedIpUuid(ip.getUuid());
                                returnIpMsg.setL3NetworkUuid(ip.getL3NetworkUuid());
                                bus.makeTargetServiceIdByResourceUuid(returnIpMsg, L3NetworkConstant.SERVICE_ID, ip.getL3NetworkUuid());
                                msgs.add(returnIpMsg);
                            }
                            new While<>(msgs).all((rmsg, com) -> bus.send(rmsg, new CloudBusCallBack(com) {
                                @Override
                                public void run(MessageReply reply) {
                                    if (!reply.isSuccess()) {
                                        logger.warn(String.format("failed to return ip address[uuid: %s]", rmsg.getUsedIpUuid()));
                                    }
                                    com.done();
                                }
                            })).run(new WhileDoneCompletion(trigger) {
                                @Override
                                public void done(ErrorCodeList errorCodeList) {
                                    for (NicManageExtensionPoint ext : pluginRgty.getExtensionList(NicManageExtensionPoint.class)) {
                                        ext.beforeDeleteNic(nic);
                                    }
                                    dbf.removeByPrimaryKey(nic.getUuid(), VmNicVO.class);
                                    trigger.next();
                                }
                            });
                        }
                    });
                    fchain.done(new FlowDoneHandler(completion) {
                        @Override
                        public void handle(Map data) {
                            completion.success();
                        }
                    }).error(new FlowErrorHandler(completion) {
                        @Override
                        public void handle(ErrorCode errCode, Map data) {
                            completion.fail(errCode);
                        }
                    }).start();

                    return;
                }
                DetachNicFromVmMsg detachNicFromVmMsg = new DetachNicFromVmMsg();
                detachNicFromVmMsg.setVmInstanceUuid(nic.getVmInstanceUuid());
                detachNicFromVmMsg.setVmNicUuid(nic.getUuid());
                bus.makeTargetServiceIdByResourceUuid(detachNicFromVmMsg, VmInstanceConstant.SERVICE_ID, nic.getVmInstanceUuid());
                bus.send(detachNicFromVmMsg, new CloudBusCallBack(completion) {
                    @Override
                    public void run(MessageReply reply) {
                        if (!reply.isSuccess()) {
                            completion.fail(reply.getError());
                        } else {
                            completion.success();
                        }
                    }
                });
            }

            @Override
            public String getName() {
                return String.format("delete-vmNic-%s", nic.getUuid());
            }
        });
    }

    private String getVmNicSyncSignature(String nicUuid) {
        return String.format("vmNic-%s", nicUuid);
    }
}

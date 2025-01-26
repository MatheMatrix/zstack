package org.zstack.sdnController;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.Platform;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cascade.CascadeConstant;
import org.zstack.core.cascade.CascadeFacade;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.core.workflow.ShareFlow;
import org.zstack.header.AbstractService;
import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.core.WhileDoneCompletion;
import org.zstack.header.core.NopeCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.message.APIDeleteMessage;
import org.zstack.header.message.Message;
import org.zstack.header.message.MessageReply;
import org.zstack.header.network.NetworkException;
import org.zstack.header.network.l2.*;
import org.zstack.header.network.l3.L3NetworkInventory;
import org.zstack.header.network.l3.L3NetworkVO;
import org.zstack.header.vm.*;
import org.zstack.network.hostNetworkInterface.HostNetworkInterfaceVO;
import org.zstack.network.hostNetworkInterface.HostNetworkInterfaceVO_;
import org.zstack.sdnController.header.*;
import org.zstack.tag.TagManager;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import javax.persistence.Tuple;
import java.lang.reflect.Type;
import java.util.*;

import static java.util.Arrays.asList;
import static org.zstack.core.Platform.operr;
import static org.zstack.sdnController.header.SdnControllerFlowDataParam.*;

public class SdnControllerManagerImpl extends AbstractService implements SdnControllerManager,
        L2NetworkCreateExtensionPoint, L2NetworkDeleteExtensionPoint, InstantiateResourceOnAttachingNicExtensionPoint,
        PreVmInstantiateResourceExtensionPoint, VmReleaseResourceExtensionPoint,
        ReleaseNetworkServiceOnDetachingNicExtensionPoint {
    private static final CLogger logger = Utils.getLogger(SdnControllerManagerImpl.class);

    @Autowired
    private CloudBus bus;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private TagManager tagMgr;
    @Autowired
    private CascadeFacade casf;
    @Autowired
    private ThreadFacade thdf;

    private Map<String, SdnControllerFactory> sdnControllerFactories = Collections.synchronizedMap(new HashMap<String, SdnControllerFactory>());

    @Override
    public int getSyncLevel() {
        return super.getSyncLevel();
    }

    @Override
    public List<String> getAliasIds() {
        return super.getAliasIds();
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg instanceof APIAddSdnControllerMsg) {
            handle((APIAddSdnControllerMsg) msg);
        } else if (msg instanceof APIRemoveSdnControllerMsg) {
            handle((APIRemoveSdnControllerMsg) msg);
        } else if (msg instanceof APIUpdateSdnControllerMsg) {
            handle((APIUpdateSdnControllerMsg) msg);
        } else if (msg instanceof SdnControllerDeletionMsg) {
            handle((SdnControllerDeletionMsg) msg);
        } else if (msg instanceof APISdnControllerAddHostMsg) {
            handle((APISdnControllerAddHostMsg) msg);
        } else if (msg instanceof APISdnControllerRemoveHostMsg) {
            handle((APISdnControllerRemoveHostMsg) msg);
        } else if (msg instanceof APIReconnectSdnControllerMsg) {
            handle((APIReconnectSdnControllerMsg) msg);
        } else if (msg instanceof APISdnControllerChangeHostMsg) {
            handle((APISdnControllerChangeHostMsg) msg);
        } else if (msg instanceof SdnControllerRemoveHostMsg) {
            handle((SdnControllerRemoveHostMsg) msg);
        }
    }

    private void handle(APIReconnectSdnControllerMsg msg) {
        APIReconnectSdnControllerEvent event = new APIReconnectSdnControllerEvent(msg.getId());
        sdnControllerSync(msg, new Completion(msg) {
            @Override
            public void success() {
                event.setInventory(SdnControllerInventory.valueOf(dbf.findByUuid(msg.getSdnControllerUuid(), SdnControllerVO.class)));
                bus.publish(event);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                event.setError(errorCode);
                bus.publish(event);
            }
        });
    }

    private void sdnControllerSync(APIReconnectSdnControllerMsg msg, Completion completion) {
        SdnControllerVO controllerVO = dbf.findByUuid(msg.getSdnControllerUuid(), SdnControllerVO.class);
        SdnControllerFactory factory = getSdnControllerFactory(controllerVO.getVendorType());

        FlowChain chain = factory.getSyncChain();
        chain.getData().put(SDN_CONTROLLER_INV, SdnControllerInventory.valueOf(controllerVO));
        chain.setName(String.format("sync-sdn-controller-%s-%s", controllerVO.getUuid(), controllerVO.getName()));
        chain.insert(new Flow() {
            String __name__ = "change-sdn-controller-status-to-connecting";

            @Override
            public void run(FlowTrigger trigger, Map data) {
                controllerVO.setStatus(SdnControllerStatus.Connecting);
                trigger.next();
            }

            @Override
            public void rollback(FlowRollback trigger, Map data) {
                controllerVO.setStatus(SdnControllerStatus.Disconnected);
                trigger.rollback();
            }
        }).then(new NoRollbackFlow() {
            String __name__ = "change-sdn-controller-status-to-connected";

            @Override
            public void run(FlowTrigger trigger, Map data) {
                controllerVO.setStatus(SdnControllerStatus.Connected);
                trigger.next();
            }
        }).done(new FlowDoneHandler(completion) {
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
    }

    private void handle(APISdnControllerAddHostMsg msg) {
        APISdnControllerAddHostEvent event = new APISdnControllerAddHostEvent(msg.getId());

        sdnControllerAddHost(msg, new Completion(msg) {
            @Override
            public void success() {
                SdnControllerHostRefVO ref = new SdnControllerHostRefVO();
                ref.setSdnControllerUuid(msg.getSdnControllerUuid());
                ref.setHostUuid(msg.getHostUuid());
                ref.setvSwitchType(msg.getvSwitchType());

                Map<String, String> nicNameDriverMap = new HashMap<>();
                Map<String, String> nicNamePciAddressMap = new HashMap<>();
                List<Tuple> nicTuples = Q.New(HostNetworkInterfaceVO.class)
                        .eq(HostNetworkInterfaceVO_.hostUuid, msg.getHostUuid())
                        .in(HostNetworkInterfaceVO_.interfaceName, msg.getNicNames())
                        .select(HostNetworkInterfaceVO_.interfaceName,
                                HostNetworkInterfaceVO_.driverType,
                                HostNetworkInterfaceVO_.pciDeviceAddress)
                        .listTuple();
                for (Tuple t : nicTuples) {
                    nicNameDriverMap.put(t.get(0, String.class), t.get(1, String.class));
                    nicNamePciAddressMap.put(t.get(0, String.class), t.get(2, String.class));
                }
                ref.setNicDrivers(JSONObjectUtil.toJsonString(nicNameDriverMap));
                ref.setNicPciAddresses(JSONObjectUtil.toJsonString(nicNamePciAddressMap));
                if (msg.getVtepIp() != null) {
                    ref.setVtepIp(msg.getVtepIp());
                }
                if (msg.getNetmask() != null) {
                    ref.setNetmask(msg.getNetmask());
                }
                if (msg.getBondMode() != null) {
                    ref.setBondMode(msg.getBondMode());
                }
                if (msg.getLacpMode() != null) {
                    ref.setLacpMode(msg.getLacpMode());
                }
                dbf.persist(ref);

                event.setInventory(SdnControllerInventory.valueOf(dbf.findByUuid(msg.getSdnControllerUuid(), SdnControllerVO.class)));
                bus.publish(event);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                event.setError(errorCode);
                bus.publish(event);
            }
        });
    }

    private void handle(APISdnControllerRemoveHostMsg amsg) {
        APISdnControllerRemoveHostEvent event = new APISdnControllerRemoveHostEvent(amsg.getId());

        SdnControllerRemoveHostMsg msg = SdnControllerRemoveHostMsg.fromApi(amsg);
        sdnControllerRemoveHost(msg, new Completion(msg) {
            @Override
            public void success() {
                SQL.New(SdnControllerHostRefVO.class)
                        .eq(SdnControllerHostRefVO_.sdnControllerUuid, msg.getSdnControllerUuid())
                        .eq(SdnControllerHostRefVO_.hostUuid, msg.getHostUuid())
                        .eq(SdnControllerHostRefVO_.vSwitchType, msg.getvSwitchType()).delete();

                event.setInventory(SdnControllerInventory.valueOf(dbf.findByUuid(msg.getSdnControllerUuid(), SdnControllerVO.class)));
                bus.publish(event);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                event.setError(errorCode);
                bus.publish(event);
            }
        });
    }

    private void handle(SdnControllerRemoveHostMsg msg) {
        SdnControllerRemoveHostReply reply = new SdnControllerRemoveHostReply();

        sdnControllerRemoveHost(msg, new Completion(msg) {
            @Override
            public void success() {
                SQL.New(SdnControllerHostRefVO.class)
                        .eq(SdnControllerHostRefVO_.sdnControllerUuid, msg.getSdnControllerUuid())
                        .eq(SdnControllerHostRefVO_.hostUuid, msg.getHostUuid())
                        .eq(SdnControllerHostRefVO_.vSwitchType, msg.getvSwitchType()).delete();

                bus.reply(msg, reply);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                reply.setError(errorCode);
                bus.reply(msg, reply);
            }
        });
    }

    private void handle(APISdnControllerChangeHostMsg msg) {
        APISdnControllerChangeHostEvent event = new APISdnControllerChangeHostEvent(msg.getId());

        SdnControllerHostRefVO newRef = Q.New(SdnControllerHostRefVO.class)
                .eq(SdnControllerHostRefVO_.sdnControllerUuid, msg.getSdnControllerUuid())
                .eq(SdnControllerHostRefVO_.hostUuid, msg.getHostUuid()).find();
        SdnControllerHostRefVO oldRef = SdnControllerHostRefVO.fromOther(newRef);

        boolean changed = false;
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        Map<String, String> nicNamePciAddressMap = gson.fromJson(newRef.getNicPciAddresses(), type);
        List<String> oldNicNames = new ArrayList<>(nicNamePciAddressMap.keySet());
        Collections.sort(oldNicNames);
        Collections.sort(msg.getNicNames());
        if (!oldNicNames.equals(msg.getNicNames())) {
            changed = true;
            Map<String, String> nicNameDriverMap = new HashMap<>();
            nicNamePciAddressMap = new HashMap<>();
            List<Tuple> nicTuples = Q.New(HostNetworkInterfaceVO.class)
                    .eq(HostNetworkInterfaceVO_.hostUuid, msg.getHostUuid())
                    .in(HostNetworkInterfaceVO_.interfaceName, msg.getNicNames())
                    .select(HostNetworkInterfaceVO_.interfaceName,
                            HostNetworkInterfaceVO_.driverType,
                            HostNetworkInterfaceVO_.pciDeviceAddress)
                    .listTuple();
            for (Tuple t : nicTuples) {
                nicNameDriverMap.put(t.get(0, String.class), t.get(1, String.class));
                nicNamePciAddressMap.put(t.get(0, String.class), t.get(2, String.class));
            }
            newRef.setNicDrivers(JSONObjectUtil.toJsonString(nicNameDriverMap));
            newRef.setNicPciAddresses(JSONObjectUtil.toJsonString(nicNamePciAddressMap));
        }

        if (msg.getVtepIp() != null && !msg.getVtepIp().equals(newRef.getVtepIp())) {
            changed = true;
            newRef.setVtepIp(msg.getVtepIp());
        }

        if (msg.getNetmask() != null && !msg.getNetmask().equals(newRef.getNetmask())) {
            changed = true;
            newRef.setNetmask(msg.getNetmask());
        }

        if (msg.getBondMode() != null && !msg.getBondMode().equals(newRef.getBondMode())) {
            changed = true;
            newRef.setBondMode(msg.getBondMode());
        }

        if (msg.getLacpMode() != null && !msg.getLacpMode().equals(newRef.getLacpMode())) {
            changed = true;
            newRef.setLacpMode(msg.getLacpMode());
        }

        if (!changed) {
            event.setInventory(SdnControllerInventory.valueOf(dbf.findByUuid(msg.getSdnControllerUuid(), SdnControllerVO.class)));
            bus.publish(event);
            return;
        }

        sdnControllerChangeHost(oldRef, newRef, new Completion(msg) {
            @Override
            public void success() {
                dbf.update(newRef);
                event.setInventory(SdnControllerInventory.valueOf(dbf.findByUuid(msg.getSdnControllerUuid(), SdnControllerVO.class)));
                bus.publish(event);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                event.setError(errorCode);
                bus.publish(event);
            }
        });
    }

    private void sdnControllerAddHost(APISdnControllerAddHostMsg msg, Completion completion) {
        SdnControllerVO controllerVO = dbf.findByUuid(msg.getSdnControllerUuid(), SdnControllerVO.class);
        SdnControllerFactory factory = getSdnControllerFactory(controllerVO.getVendorType());
        SdnController controller = factory.getSdnController(controllerVO);

        controller.addHost(msg, completion);
    }

    private void sdnControllerChangeHost(SdnControllerHostRefVO oldref, SdnControllerHostRefVO newRef, Completion completion) {
        SdnControllerVO controllerVO = dbf.findByUuid(oldref.getSdnControllerUuid(), SdnControllerVO.class);
        SdnControllerFactory factory = getSdnControllerFactory(controllerVO.getVendorType());
        SdnController controller = factory.getSdnController(controllerVO);

        controller.changeHost(oldref, newRef, completion);
    }

    private void sdnControllerRemoveHost(SdnControllerRemoveHostMsg msg, Completion completion) {
        SdnControllerVO controllerVO = dbf.findByUuid(msg.getSdnControllerUuid(), SdnControllerVO.class);
        SdnControllerFactory factory = getSdnControllerFactory(controllerVO.getVendorType());
        SdnController controller = factory.getSdnController(controllerVO);

        controller.removeHost(msg, completion);
    }

    private void doDeletionSdnController(SdnControllerDeletionMsg msg, Completion completion) {
        SdnControllerVO vo = dbf.findByUuid(msg.getSdnControllerUuid(), SdnControllerVO.class);
        SdnControllerInventory sdn = SdnControllerInventory.valueOf(vo);

        SdnControllerFactory factory = getSdnControllerFactory(vo.getVendorType());
        SdnController controller = factory.getSdnController(vo);

        FlowChain chain = FlowChainBuilder.newSimpleFlowChain();
        chain.setName(String.format("sdn-controller-deletion-%s", msg.getSdnControllerUuid()));
        chain.then(new NoRollbackFlow() {
            String __name__ =  String.format("detach-hardvxlan-network-of-sdn-controller-%s", vo.getName());

            @Override
            public void run(FlowTrigger trigger, Map data) {
                List<HardwareL2VxlanNetworkPoolVO> poolVos = Q.New(HardwareL2VxlanNetworkPoolVO.class)
                        .eq(HardwareL2VxlanNetworkPoolVO_.sdnControllerUuid, msg.getSdnControllerUuid()).list();
                new While<>(poolVos).each((pool, wcomp) -> {
                    DeleteL2NetworkMsg msg = new DeleteL2NetworkMsg();
                    msg.setUuid(pool.getUuid());
                    bus.makeTargetServiceIdByResourceUuid(msg, L2NetworkConstant.SERVICE_ID, pool.getUuid());
                    bus.send(msg, new CloudBusCallBack(wcomp) {
                        @Override
                        public void run(MessageReply reply) {
                            if (!reply.isSuccess()) {
                                logger.info(String.format("delete hardware vxpool[uuid:%s] failed, reason:%s", pool.getUuid(), reply.getError().getDetails()));
                            }
                            wcomp.done();
                        }
                    });
                }).run(new WhileDoneCompletion(trigger) {
                    @Override
                    public void done(ErrorCodeList errorCodeList) {
                        trigger.next();
                    }
                });
            }
        }).then(new NoRollbackFlow() {
            String __name__ =  "delete-sdn-network";

            @Override
            public void run(FlowTrigger trigger, Map data) {
                List<String> l2Uuids = controller.getL2Network();
                if (l2Uuids.isEmpty()) {
                    trigger.next();
                    return;
                }

                new While<>(l2Uuids).step((uuid, wcomp) -> {
                    DeleteL2NetworkMsg msg = new DeleteL2NetworkMsg();
                    msg.setUuid(uuid);
                    bus.makeTargetServiceIdByResourceUuid(msg, L2NetworkConstant.SERVICE_ID, uuid);
                    bus.send(msg, new CloudBusCallBack(wcomp) {
                        @Override
                        public void run(MessageReply reply) {
                            if (!reply.isSuccess()) {
                                logger.info(String.format("delete sdn l2 network[uuid:%s] failed, reason:%s", uuid, reply.getError().getDetails()));
                            }
                            wcomp.done();
                        }
                    });
                }, 5).run(new WhileDoneCompletion(trigger) {
                    @Override
                    public void done(ErrorCodeList errorCodeList) {
                        if (errorCodeList.getCauses().isEmpty()) {
                            trigger.next();
                        } else {
                            trigger.fail(errorCodeList.getCauses().get(0));
                        }
                    }
                });
            }
        }).then(new NoRollbackFlow() {
            String __name__ =  "remove-host-from-sdn-controller";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                List<SdnControllerHostRefVO> refVOS = Q.New(SdnControllerHostRefVO.class)
                        .eq(SdnControllerHostRefVO_.sdnControllerUuid, msg.getSdnControllerUuid())
                        .list();
                if (refVOS.isEmpty()) {
                    trigger.next();
                    return;
                }

                new While<>(refVOS).step((ref, wcomp) -> {
                    SdnControllerRemoveHostMsg msg = new SdnControllerRemoveHostMsg();
                    msg.setSdnControllerUuid(ref.getSdnControllerUuid());
                    msg.setHostUuid(ref.getHostUuid());
                    msg.setvSwitchType(ref.getvSwitchType());
                    bus.makeTargetServiceIdByResourceUuid(msg, SdnControllerConstant.SERVICE_ID, ref.getSdnControllerUuid());
                    bus.send(msg, new CloudBusCallBack(wcomp) {
                        @Override
                        public void run(MessageReply reply) {
                            if (!reply.isSuccess()) {
                                logger.debug(String.format("delete host [uuid:%s] from sdn controller[uuid:%s] failed, error:%s",
                                        msg.getHostUuid(), msg.getSdnControllerUuid(), reply.getError().getDetails()));
                            }
                            wcomp.done();
                        }
                    });
                }, 5).run(new WhileDoneCompletion(trigger) {
                    @Override
                    public void done(ErrorCodeList errorCodeList) {
                        if (errorCodeList.getCauses().isEmpty()) {
                            trigger.next();
                        } else {
                            trigger.fail(errorCodeList.getCauses().get(0));
                        }
                    }
                });
            }
        }).then(new NoRollbackFlow() {
            String __name__ = String.format("delete-sdn-controller-%s", vo.getName());

                @Override
                public void run(FlowTrigger trigger, Map data) {
                    controller.deleteSdnController(msg, sdn, new Completion(completion) {
                        @Override
                        public void success() {
                            completion.success();
                        }

                        @Override
                        public void fail(ErrorCode errorCode) {
                            completion.fail(errorCode);
                        }
                    });
                }
        }).then(new NoRollbackFlow() {
            String __name__ = "delete-sdn-controller-on-db";
            @Override
            public void run(FlowTrigger trigger, Map data) {
                dbf.removeByPrimaryKey(msg.getSdnControllerUuid(), SdnControllerVO.class);
                trigger.next();
            }
        }).done(new FlowDoneHandler(completion) {
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
    }

    private void handle(SdnControllerDeletionMsg msg) {
        SdnControllerDeletionReply reply = new SdnControllerDeletionReply();
        thdf.chainSubmit(new ChainTask(msg) {
            @Override
            public String getSyncSignature() {
                return String.format("sdn-controller-%s", msg.getSdnControllerUuid());
            }

            @Override
            public void run(SyncTaskChain chain) {
                doDeletionSdnController(msg, new Completion(msg) {
                    @Override
                    public void success() {
                        bus.reply(msg, reply);
                        chain.next();
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        reply.setError(errorCode);
                        bus.reply(msg, reply);
                        chain.next();
                    }
                });
            }

            @Override
            public String getName() {
                return String.format("attach-sdn-controller-%s", msg.getSdnControllerUuid());
            }
        });
    }

    private void doCreateSdnController(SdnControllerVO vo, APIAddSdnControllerMsg msg, Completion completion) {
        SdnControllerFactory factory = getSdnControllerFactory(msg.getVendorType());
        SdnController controller = factory.getSdnController(vo);

        Map data = new HashMap();
        FlowChain chain = FlowChainBuilder.newShareFlowChain();
        chain.setData(data);
        chain.setName(String.format("create-sdn-controller-%s", msg.getName()));
        chain.then(new ShareFlow() {
            @Override
            public void setup() {
                flow(new NoRollbackFlow() {
                    String __name__ = String.format("pre-process-for-create-sdn-controller-%s", msg.getName());

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        controller.preInitSdnController(msg, new Completion(trigger) {
                            @Override
                            public void success() {
                                trigger.next();
                            }

                            @Override
                            public void fail(ErrorCode errorCode) {
                                trigger.fail(errorCode);
                            }
                        });
                    }
                });
                flow(new Flow() {
                    String __name__ = String.format("create-sdn-controller-%s-on-db", msg.getName());

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        dbf.persist(vo);
                        trigger.next();
                    }

                    @Override
                    public void rollback(FlowRollback trigger, Map data) {
                        dbf.removeByPrimaryKey(vo.getUuid(), SdnControllerVO.class);
                        trigger.rollback();
                    }
                });
                flow(new Flow() {
                    String __name__ = String.format("init-sdn-controller-%s", msg.getName());

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        controller.initSdnController(msg, new Completion(completion) {
                            @Override
                            public void success() {
                                completion.success();
                            }

                            @Override
                            public void fail(ErrorCode errorCode) {
                                dbf.removeByPrimaryKey(vo.getUuid(), SdnControllerVO.class);
                                completion.fail(errorCode);
                            }
                        });
                    }

                    @Override
                    public void rollback(FlowRollback trigger, Map data) {
                        trigger.rollback();
                    }
                });
                flow(new NoRollbackFlow() {
                    String __name__ = String.format("post-process-for-create-sdn-controller--%s", msg.getName());

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        controller.postInitSdnController(vo, new Completion(trigger) {
                            @Override
                            public void success() {
                                trigger.next();
                            }

                            @Override
                            public void fail(ErrorCode errorCode) {
                                trigger.fail(errorCode);
                            }
                        });
                    }
                });
                done(new FlowDoneHandler(completion) {
                    @Override
                    public void handle(Map data) {
                        logger.debug(String.format("successfully create sdn controller"));
                        completion.success();
                    }
                });
                error(new FlowErrorHandler(completion) {
                    @Override
                    public void handle(ErrorCode errCode, Map data) {
                        completion.fail(errCode);
                    }
                });
            }
        }).start();
    }

    private void handle(APIAddSdnControllerMsg msg) {
        APIAddSdnControllerEvent event = new APIAddSdnControllerEvent(msg.getId());

        SdnControllerVO vo = new SdnControllerVO();
        vo.setVendorType(msg.getVendorType());
        if (msg.getResourceUuid() != null) {
            vo.setUuid(msg.getResourceUuid());
        } else {
            vo.setUuid(Platform.getUuid());
        }
        vo.setName(msg.getName());
        vo.setDescription(msg.getDescription());
        vo.setIp(msg.getIp());
        vo.setUsername(msg.getUserName());
        vo.setPassword(msg.getPassword());
        vo.setAccountUuid(msg.getSession().getAccountUuid());
        vo.setStatus(SdnControllerStatus.Connected);

        doCreateSdnController(vo, msg, new Completion(msg) {
            @Override
            public void success() {
                tagMgr.createTagsFromAPICreateMessage(msg, vo.getUuid(), SdnControllerVO.class.getSimpleName());
                event.setInventory(SdnControllerInventory.valueOf(dbf.findByUuid(vo.getUuid(), SdnControllerVO.class)));
                bus.publish(event);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                event.setError(errorCode);
                bus.publish(event);
            }
        });
    }

    private void handle(APIRemoveSdnControllerMsg msg) {
        APIRemoveSdnControllerEvent event = new APIRemoveSdnControllerEvent(msg.getId());

        final String issuer = SdnControllerVO.class.getSimpleName();
        SdnControllerVO vo = dbf.findByUuid(msg.getUuid(), SdnControllerVO.class);
        final List<SdnControllerInventory> ctx = asList(SdnControllerInventory.valueOf(vo));
        FlowChain chain = FlowChainBuilder.newSimpleFlowChain();
        chain.setName(String.format("delete-sdn-controller-%s-name-%s", msg.getUuid(), vo.getName()));
        if (msg.getDeletionMode() == APIDeleteMessage.DeletionMode.Permissive) {
            chain.then(new NoRollbackFlow() {
                @Override
                public void run(final FlowTrigger trigger, Map data) {
                    casf.asyncCascade(CascadeConstant.DELETION_CHECK_CODE, issuer, ctx, new Completion(trigger) {
                        @Override
                        public void success() {
                            trigger.next();
                        }

                        @Override
                        public void fail(ErrorCode errorCode) {
                            trigger.fail(errorCode);
                        }
                    });
                }
            }).then(new NoRollbackFlow() {
                @Override
                public void run(final FlowTrigger trigger, Map data) {
                    casf.asyncCascade(CascadeConstant.DELETION_DELETE_CODE, issuer, ctx, new Completion(trigger) {
                        @Override
                        public void success() {
                            trigger.next();
                        }

                        @Override
                        public void fail(ErrorCode errorCode) {
                            trigger.fail(errorCode);
                        }
                    });
                }
            });
        } else {
            chain.then(new NoRollbackFlow() {
                @Override
                public void run(final FlowTrigger trigger, Map data) {
                    casf.asyncCascade(CascadeConstant.DELETION_FORCE_DELETE_CODE, issuer, ctx, new Completion(trigger) {
                        @Override
                        public void success() {
                            trigger.next();
                        }

                        @Override
                        public void fail(ErrorCode errorCode) {
                            trigger.fail(errorCode);
                        }
                    });
                }
            });
        }

        chain.done(new FlowDoneHandler(msg) {
            @Override
            public void handle(Map data) {
                casf.asyncCascadeFull(CascadeConstant.DELETION_CLEANUP_CODE, issuer, ctx, new NopeCompletion());
                bus.publish(event);
            }
        }).error(new FlowErrorHandler(msg) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                event.setError(errCode);
                bus.publish(event);
            }
        }).start();
    }

    private void handle(APIUpdateSdnControllerMsg msg) {
        APIUpdateSdnControllerEvent event = new APIUpdateSdnControllerEvent(msg.getId());
        SdnControllerVO vo = dbf.findByUuid(msg.getUuid(), SdnControllerVO.class);
        Boolean changed = false;

        if (msg.getName() != null && !msg.getName().equals(vo.getName())) {
            vo.setName(msg.getName());
            changed = true;
        }

        if (msg.getDescription() != null && !msg.getDescription().equals(vo.getDescription())) {
            vo.setDescription(msg.getDescription());
            changed = true;
        }

        if (changed) {
            vo = dbf.updateAndRefresh(vo);
        }

        event.setInventory(SdnControllerInventory.valueOf(vo));
        bus.publish(event);
    }

    @Override
    public void beforeCreateL2Network(APICreateL2NetworkMsg msg) throws NetworkException {

    }

    @Override
    public void postCreateL2Network(L2NetworkInventory l2Network, APICreateL2NetworkMsg msg, Completion completion) {
        VSwitchType vSwitchType = VSwitchType.valueOf(l2Network.getvSwitchType());
        if (vSwitchType.getSdnControllerType() == null) {
            completion.success();
            return;
        }

        /* vswitch type: OvnDpdk will go here */
        SdnControllerFactory factory = getSdnControllerFactory(vSwitchType.getSdnControllerType());
        SdnController controller = factory.getSdnController(l2Network);
        if (controller == null) {
            completion.fail(operr("can not found sdn controller for l2 network[uuid:%s, vswitchType:%s]",
                    l2Network.getUuid(), l2Network.getvSwitchType()));
            return;
        }

        controller.createL2Network(l2Network, msg.getSystemTags(), completion);
    }

    @Override
    public void afterCreateL2Network(L2NetworkInventory l2Network) {

    }

    @Override
    public void preDeleteL2Network(L2NetworkInventory inventory) throws L2NetworkException {

    }

    @Override
    public void beforeDeleteL2Network(L2NetworkInventory inventory) {

    }

    @Override
    public void deleteL2Network(L2NetworkInventory inv, NoErrorCompletion completion) {
        VSwitchType vSwitchType = VSwitchType.valueOf(inv.getvSwitchType());
        if (vSwitchType.getSdnControllerType() == null) {
            completion.done();
            return;
        }

        /* vswitch type: OvnDpdk will go here */
        SdnControllerFactory factory = getSdnControllerFactory(vSwitchType.getSdnControllerType());
        SdnController controller = factory.getSdnController(inv);
        if (controller == null) {
            logger.warn(String.format("can not found sdn controller for l2 network[uuid:%s, vswitchType:%s]",
                    inv.getUuid(), inv.getvSwitchType()));
            completion.done();
            return;
        }

        controller.deleteL2Network(inv, new Completion(completion) {
            @Override
            public void success() {
                completion.done();
            }

            @Override
            public void fail(ErrorCode errorCode) {
                logger.warn(String.format("can not found sdn controller for l2 network[uuid:%s, vswitchType:%s]",
                        inv.getUuid(), inv.getvSwitchType()));
                completion.done();
            }
        });
    }

    @Override
    public void afterDeleteL2Network(L2NetworkInventory inventory) {

    }

    void addOvnLogicalPorts(String controllerType, List<VmNicInventory> nics, Completion completion) {
        SdnControllerFactory factory = getSdnControllerFactory(controllerType);
        if (factory == null) {
            completion.fail(operr("there is no sdn controller for sdn controller type:%s", controllerType));
        }

        SdnController controller = factory.getSdnController();
        controller.addLogicalPorts(nics, completion);
    }

    void addOvnLogicalPort(Map<String, List<VmNicInventory>> nicMaps, Completion completion) {
        new While<>(nicMaps.entrySet()).each((e, wcomp) -> {
            addOvnLogicalPorts(e.getKey(), e.getValue(), new Completion(wcomp) {
                @Override
                public void success() {
                    wcomp.done();
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    wcomp.addError(errorCode);
                    wcomp.allDone();
                }
            });
        }).run(new WhileDoneCompletion(completion) {
            @Override
            public void done(ErrorCodeList errorCodeList) {
                if (errorCodeList.getCauses().isEmpty()) {
                    completion.success();
                } else {
                    completion.fail(errorCodeList.getCauses().get(0));
                }
            }
        });
    }

    void removeOvnLogicalPorts(String controllerType, List<VmNicInventory> nics, Completion completion) {
        SdnControllerFactory factory = getSdnControllerFactory(controllerType);
        if (factory == null) {
            completion.fail(operr("there is no sdn controller for sdn controller type:%s", controllerType));
        }

        SdnController controller = factory.getSdnController();
        controller.removeLogicalPorts(nics, completion);
    }

    void removeLogicalPort(Map<String, List<VmNicInventory>> nicMaps, Completion completion) {
        new While<>(nicMaps.entrySet()).each((e, wcomp) -> {
            removeOvnLogicalPorts(e.getKey(), e.getValue(), new Completion(wcomp) {
                @Override
                public void success() {
                    wcomp.done();
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    wcomp.addError(errorCode);
                    wcomp.allDone();
                }
            });
        }).run(new WhileDoneCompletion(completion) {
            @Override
            public void done(ErrorCodeList errorCodeList) {
                if (errorCodeList.getCauses().isEmpty()) {
                    completion.success();
                } else {
                    completion.fail(errorCodeList.getCauses().get(0));
                }
            }
        });
    }

    @Override
    public void releaseVmResource(VmInstanceSpec spec, Completion completion) {
        if (VmInstanceConstant.VmOperation.DetachNic != spec.getCurrentVmOperation() &&
                VmInstanceConstant.VmOperation.Destroy != spec.getCurrentVmOperation()) {
            completion.success();
            return;
        }

        if (spec.getL3Networks() == null || spec.getL3Networks().isEmpty()) {
            completion.success();
            return;
        }

        // we run into this situation when VM nics are all detached and the
        // VM is being rebooted
        if (spec.getDestNics().isEmpty()) {
            completion.success();
            return;
        }

        Map<String, List<VmNicInventory>> nicMaps = new HashMap<>();
        for (VmNicInventory nic : spec.getDestNics()) {
            L3NetworkVO l3Vo = dbf.findByUuid(nic.getL3NetworkUuid(), L3NetworkVO.class);
            if (l3Vo == null) {
                continue;
            }

            L2NetworkVO l2VO = dbf.findByUuid(l3Vo.getL2NetworkUuid(), L2NetworkVO.class);
            if (l2VO == null) {
                continue;
            }

            VSwitchType vSwitchType = VSwitchType.valueOf(l2VO.getvSwitchType());
            if (vSwitchType.getSdnControllerType() == null) {
                continue;
            }

            nicMaps.computeIfAbsent(vSwitchType.getSdnControllerType(), k -> new ArrayList<>()).add(nic);
        }

        if (nicMaps.isEmpty()) {
            completion.success();
            return;
        }

        removeLogicalPort(nicMaps, completion);
    }

    @Override
    public void instantiateResourceOnAttachingNic(VmInstanceSpec spec, L3NetworkInventory l3, Completion completion) {
        L2NetworkVO l2NetworkVO = dbf.findByUuid(l3.getL2NetworkUuid(), L2NetworkVO.class);
        VSwitchType vSwitchType = VSwitchType.valueOf(l2NetworkVO.getvSwitchType());
        if (vSwitchType.getSdnControllerType() == null) {
            completion.success();
            return;
        }

        Map<String, List<VmNicInventory>> nicMaps = new HashMap<>();
        List<VmNicInventory> nics = new ArrayList<>();
        nics.add(spec.getDestNics().get(0));
        nicMaps.put(vSwitchType.getSdnControllerType(), nics);
        addOvnLogicalPort(nicMaps, completion);
    }

    @Override
    public void releaseResourceOnAttachingNic(VmInstanceSpec spec, L3NetworkInventory l3, NoErrorCompletion completion) {
        L2NetworkVO l2NetworkVO = dbf.findByUuid(l3.getL2NetworkUuid(), L2NetworkVO.class);
        VSwitchType vSwitchType = VSwitchType.valueOf(l2NetworkVO.getvSwitchType());
        if (vSwitchType.getSdnControllerType() == null) {
            completion.done();
            return;
        }

        Map<String, List<VmNicInventory>> nicMaps = new HashMap<>();
        List<VmNicInventory> nics = new ArrayList<>();
        nics.add(spec.getDestNics().get(0));
        nicMaps.put(vSwitchType.getSdnControllerType(), nics);

        removeLogicalPort(nicMaps, new Completion(completion) {
            @Override
            public void success() {
                completion.done();
            }

            @Override
            public void fail(ErrorCode errorCode) {
                logger.info(String.format("failed to remove logical port for vm[uuid:%s] nic[internalName:%s], because: %s",
                        spec.getVmInventory().getUuid(), spec.getDestNics().get(0).getInternalName(), errorCode.getDetails()));
                completion.done();
            }
        });
    }

    @Override
    public void releaseResourceOnDetachingNic(VmInstanceSpec spec, VmNicInventory nic, NoErrorCompletion completion) {
        L3NetworkVO l3Vo = dbf.findByUuid(nic.getL3NetworkUuid(), L3NetworkVO.class);
        L2NetworkVO l2NetworkVO = dbf.findByUuid(l3Vo.getL2NetworkUuid(), L2NetworkVO.class);
        VSwitchType vSwitchType = VSwitchType.valueOf(l2NetworkVO.getvSwitchType());
        if (vSwitchType.getSdnControllerType() == null) {
            completion.done();
            return;
        }

        Map<String, List<VmNicInventory>> nicMaps = new HashMap<>();
        List<VmNicInventory> nics = new ArrayList<>();
        nics.add(spec.getDestNics().get(0));
        nicMaps.put(vSwitchType.getSdnControllerType(), nics);

        removeLogicalPort(nicMaps, new Completion(completion) {
            @Override
            public void success() {
                completion.done();
            }

            @Override
            public void fail(ErrorCode errorCode) {
                logger.info(String.format("failed to remove logical port for vm[uuid:%s] nic[internalName:%s], because: %s",
                        spec.getVmInventory().getUuid(), spec.getDestNics().get(0).getInternalName(), errorCode.getDetails()));
                completion.done();
            }
        });
    }

    @Override
    public void preBeforeInstantiateVmResource(VmInstanceSpec spec) throws VmInstantiateResourceException {

    }

    @Override
    public void preInstantiateVmResource(VmInstanceSpec spec, Completion completion) {
        if (spec.getL3Networks() == null || spec.getL3Networks().isEmpty()) {
            completion.success();
            return;
        }

        // we run into this situation when VM nics are all detached and the
        // VM is being rebooted
        if (spec.getDestNics().isEmpty()) {
            completion.success();
            return;
        }

        Map<String, List<VmNicInventory>> nicMaps = new HashMap<>();
        for (VmNicInventory nic : spec.getDestNics()) {
            L3NetworkVO l3Vo = dbf.findByUuid(nic.getL3NetworkUuid(), L3NetworkVO.class);
            if (l3Vo == null) {
                continue;
            }

            L2NetworkVO l2VO = dbf.findByUuid(l3Vo.getL2NetworkUuid(), L2NetworkVO.class);
            if (l2VO == null) {
                continue;
            }

            VSwitchType vSwitchType = VSwitchType.valueOf(l2VO.getvSwitchType());
            if (vSwitchType.getSdnControllerType() ==null) {
                continue;
            }

            nicMaps.computeIfAbsent(vSwitchType.getSdnControllerType(), k -> new ArrayList<>()).add(nic);
        }

        if (nicMaps.isEmpty()) {
            completion.success();
            return;
        }

        addOvnLogicalPort(nicMaps, completion);
    }

    @Override
    public void preReleaseVmResource(VmInstanceSpec spec, Completion completion) {
        if (spec.getL3Networks() == null || spec.getL3Networks().isEmpty()) {
            completion.success();
            return;
        }

        // we run into this situation when VM nics are all detached and the
        // VM is being rebooted
        if (spec.getDestNics().isEmpty()) {
            completion.success();
            return;
        }

        Map<String, List<VmNicInventory>> nicMaps = new HashMap<>();
        for (VmNicInventory nic : spec.getDestNics()) {
            L3NetworkVO l3Vo = dbf.findByUuid(nic.getL3NetworkUuid(), L3NetworkVO.class);
            if (l3Vo == null) {
                continue;
            }

            L2NetworkVO l2VO = dbf.findByUuid(l3Vo.getL2NetworkUuid(), L2NetworkVO.class);
            if (l2VO == null) {
                continue;
            }

            VSwitchType vSwitchType = VSwitchType.valueOf(l2VO.getvSwitchType());
            if (vSwitchType.getSdnControllerType() ==null) {
                continue;
            }

            nicMaps.computeIfAbsent(vSwitchType.getSdnControllerType(), k -> new ArrayList<>()).add(nic);
        }

        if (nicMaps.isEmpty()) {
            completion.success();
            return;
        }

        removeLogicalPort(nicMaps, completion);
    }

    @Override
    public SdnControllerFactory getSdnControllerFactory(String type) {
        SdnControllerFactory factory = sdnControllerFactories.get(type);
        if (factory == null) {
            throw new CloudRuntimeException(String.format("Cannot find sdn controller for type(%s)", type));
        }

        return factory;
    }

    @Override
    public SdnController getSdnController(SdnControllerVO sdnControllerVO) {
        SdnControllerFactory factory = getSdnControllerFactory(sdnControllerVO.getVendorType());
        return factory.getSdnController(sdnControllerVO);
    }

    @Override
    public String getId() {
        return bus.makeLocalServiceId(SdnControllerConstant.SERVICE_ID);
    }

    @Override
    public boolean start() {
        for (SdnControllerFactory f : pluginRgty.getExtensionList(SdnControllerFactory.class)) {
            SdnControllerFactory old = sdnControllerFactories.get(f.getVendorType().toString());
            if (old != null) {
                throw new CloudRuntimeException(String.format("duplicate SdnControllerFactory[%s, %s] for type[%s]",
                        f.getClass().getName(), old.getClass().getName(), f.getVendorType()));
            }
            sdnControllerFactories.put(f.getVendorType().toString(), f);
        }

        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }
}

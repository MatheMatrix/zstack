package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusListCallBack;
import org.zstack.core.cloudbus.ResourceDestinationMaker;
import org.zstack.core.config.GlobalConfig;
import org.zstack.core.config.GlobalConfigUpdateExtensionPoint;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.core.thread.CancelablePeriodicTask;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.Component;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.message.MessageReply;
import org.zstack.header.vm.*;
import org.zstack.header.vm.VmInstanceDeletionPolicyManager.VmInstanceDeletionPolicy;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.Tuple;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.zstack.utils.CollectionUtils.transformToList;

/**
 * Manages VM expunge lifecycle — periodic task that expunges destroyed VMs
 * after their retention period expires.
 * Extracted from VmInstanceManagerImpl to reduce God Class complexity.
 */
public class VmExpungeSubManager implements ManagementNodeReadyExtensionPoint, Component {
    private static final CLogger logger = Utils.getLogger(VmExpungeSubManager.class);

    @Autowired
    private ThreadFacade thdf;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private VmInstanceDeletionPolicyManager deletionPolicyMgr;
    @Autowired
    private CloudBus bus;
    @Autowired
    private ResourceDestinationMaker destMaker;

    private Future<Void> expungeVmTask;

    @Override
    public void managementNodeReady() {
        startVmExpungeTask();
    }

    @Override
    public boolean start() {
        installGlobalConfigUpdater();
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    private void installGlobalConfigUpdater() {
        VmGlobalConfig.VM_EXPUNGE_INTERVAL.installUpdateExtension(new GlobalConfigUpdateExtensionPoint() {
            @Override
            public void updateGlobalConfig(GlobalConfig oldConfig, GlobalConfig newConfig) {
                startVmExpungeTask();
            }
        });
        VmGlobalConfig.VM_EXPUNGE_PERIOD.installUpdateExtension(new GlobalConfigUpdateExtensionPoint() {
            @Override
            public void updateGlobalConfig(GlobalConfig oldConfig, GlobalConfig newConfig) {
                startVmExpungeTask();
            }
        });
        VmGlobalConfig.VM_DELETION_POLICY.installUpdateExtension(new GlobalConfigUpdateExtensionPoint() {
            @Override
            public void updateGlobalConfig(GlobalConfig oldConfig, GlobalConfig newConfig) {
                startVmExpungeTask();
            }
        });
    }

    private synchronized void startVmExpungeTask() {
        if (expungeVmTask != null) {
            expungeVmTask.cancel(true);
        }

        expungeVmTask = thdf.submitCancelablePeriodicTask(new CancelablePeriodicTask() {

            private List<Tuple> getVmDeletedStateManagedByUs() {
                int qun = 10000;
                SimpleQuery q = dbf.createQuery(VmInstanceVO.class);
                q.add(VmInstanceVO_.state, Op.EQ, VmInstanceState.Destroyed);
                long amount = q.count();
                int times = (int) (amount / qun) + (amount % qun != 0 ? 1 : 0);
                int start = 0;
                List<Tuple> ret = new java.util.ArrayList<>();
                for (int i = 0; i < times; i++) {
                    q = dbf.createQuery(VmInstanceVO.class);
                    q.select(VmInstanceVO_.uuid, VmInstanceVO_.lastOpDate);
                    q.add(VmInstanceVO_.state, Op.EQ, VmInstanceState.Destroyed);
                    q.setLimit(qun);
                    q.setStart(start);
                    List<Tuple> ts = q.listTuple();
                    start += qun;

                    for (Tuple t : ts) {
                        String vmUuid = t.get(0, String.class);
                        if (!destMaker.isManagedByUs(vmUuid)) {
                            continue;
                        }
                        ret.add(t);
                    }
                }

                return ret;
            }

            @Override
            public synchronized boolean run() {
                final List<Tuple> vms = getVmDeletedStateManagedByUs();
                if (vms.isEmpty()) {
                    logger.debug("[VM Expunging Task]: no vm to expunge");
                    return false;
                }

                final Timestamp current = dbf.getCurrentSqlTime();

                final List<ExpungeVmMsg> msgs = transformToList(vms, new org.zstack.utils.function.Function<ExpungeVmMsg, Tuple>() {
                    @Override
                    public ExpungeVmMsg call(Tuple t) {
                        String uuid = t.get(0, String.class);
                        Timestamp date = t.get(1, Timestamp.class);
                        long end = date.getTime() + TimeUnit.SECONDS.toMillis(VmGlobalConfig.VM_EXPUNGE_PERIOD.value(Long.class));
                        if (current.getTime() >= end) {
                            VmInstanceDeletionPolicy deletionPolicy = deletionPolicyMgr.getDeletionPolicy(uuid);

                            if (deletionPolicy == VmInstanceDeletionPolicy.Never) {
                                logger.debug(String.format("[VM Expunging Task]: the deletion policy of the vm[uuid:%s] is Never, don't expunge it",
                                        uuid));
                                return null;
                            } else {
                                ExpungeVmMsg msg = new ExpungeVmMsg();
                                msg.setVmInstanceUuid(uuid);
                                bus.makeTargetServiceIdByResourceUuid(msg, VmInstanceConstant.SERVICE_ID, uuid);
                                return msg;
                            }
                        } else {
                            return null;
                        }
                    }
                });

                if (msgs.isEmpty()) {
                    logger.debug("[VM Expunging Task]: no vm to expunge");
                    return false;
                }

                bus.send(msgs, 100, new CloudBusListCallBack(null) {
                    @Override
                    public void run(List<MessageReply> replies) {
                        for (MessageReply r : replies) {
                            ExpungeVmMsg msg = msgs.get(replies.indexOf(r));
                            if (!r.isSuccess()) {
                                logger.warn(String.format("failed to expunge the vm[uuid:%s], %s",
                                        msg.getVmInstanceUuid(), r.getError()));
                            } else {
                                logger.debug(String.format("successfully expunged the vm[uuid:%s]",
                                        msg.getVmInstanceUuid()));
                            }
                        }
                    }
                });

                return false;
            }

            @Override
            public TimeUnit getTimeUnit() {
                return TimeUnit.SECONDS;
            }

            @Override
            public long getInterval() {
                return VmGlobalConfig.VM_EXPUNGE_INTERVAL.value(Long.class);
            }

            @Override
            public String getName() {
                return "expunge-vm-task";
            }
        });

        logger.debug(String.format("vm expunging task starts running, [period: %s seconds, interval: %s seconds]",
                VmGlobalConfig.VM_EXPUNGE_PERIOD.value(Long.class), VmGlobalConfig.VM_EXPUNGE_INTERVAL.value(Long.class)));
    }
}

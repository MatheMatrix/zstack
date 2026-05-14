package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.header.core.workflow.Flow;
import org.zstack.header.core.workflow.FlowRollback;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.CheckVmStateOnHypervisorMsg;
import org.zstack.header.host.CheckVmStateOnHypervisorReply;
import org.zstack.header.host.HostConstant;
import org.zstack.header.host.HostErrors;
import org.zstack.header.message.MessageReply;
import org.zstack.header.host.MigrateVmOnHypervisorMsg;
import org.zstack.header.vm.*;
import org.zstack.longjob.LongJobUtils;

import java.util.Map;

import static org.zstack.utils.CollectionDSL.list;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class VmMigrateOnHypervisorFlow implements Flow {
    @Autowired
    protected CloudBus bus;
    @Autowired
    protected ErrorFacade errf;

    @Override
    public void run(final FlowTrigger chain, Map data) {
        final VmInstanceSpec spec = (VmInstanceSpec) data.get(VmInstanceConstant.Params.VmInstanceSpec.toString());

        ErrorCode err = LongJobUtils.buildErrIfCanceled();
        if (err != null) {
            chain.fail(err);
            return;
        }


        boolean migrateFromDest = false;
        String strategy = null;
        Integer downTime = null;
        if (spec.getMessage() instanceof MigrateVmMessage) {
            MigrateVmMessage vmMessage = (MigrateVmMessage) spec.getMessage();
            migrateFromDest = vmMessage.isMigrateFromDestination();
            strategy = vmMessage.getStrategy();
            downTime = vmMessage.getDownTime();
        }

        MigrateVmOnHypervisorMsg msg = new MigrateVmOnHypervisorMsg();
        msg.setVmInventory(spec.getVmInventory());
        msg.setDestHostInventory(spec.getDestHost());
        msg.setSrcHostUuid(spec.getVmInventory().getHostUuid());
        msg.setMigrateFromDestination(migrateFromDest);
        msg.setStrategy(strategy);
        msg.setDownTime(downTime);
        bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, msg.getHostUuid());
        bus.send(msg, new CloudBusCallBack(chain) {
            @Override
            public void run(MessageReply reply) {
                if (reply.isSuccess()) {
                    chain.next();
                } else {
                    ErrorCode canceledError = LongJobUtils.buildErrIfCanceled();
                    if (canceledError != null) {
                        chain.fail(canceledError);
                        return;
                    }

                    ErrorCode error = reply.getError();
                    if (HostErrors.FAILED_TO_MIGRATE_VM_ON_HYPERVISOR.isEqual(error.getCode())) {
                        checkVmStateOnDestinationHost(spec, error, chain);
                        return;
                    }

                    chain.fail(error);
                }
            }
        });
    }

    private void checkVmStateOnDestinationHost(final VmInstanceSpec spec, final ErrorCode migrateError,
                                               final FlowTrigger chain) {
        CheckVmStateOnHypervisorMsg msg = new CheckVmStateOnHypervisorMsg();
        msg.setVmInstanceUuids(list(spec.getVmInventory().getUuid()));
        msg.setHostUuid(spec.getDestHost().getUuid());
        bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, msg.getHostUuid());
        bus.send(msg, new CloudBusCallBack(chain) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    chain.fail(migrateError);
                    return;
                }

                CheckVmStateOnHypervisorReply r = reply.castReply();
                Map<String, String> states = r.getStates();
                if (states == null || states.isEmpty()) {
                    chain.fail(migrateError);
                    return;
                }

                String state = states.get(spec.getVmInventory().getUuid());
                if (VmInstanceState.Running.toString().equals(state)) {
                    chain.next();
                    return;
                }

                chain.fail(migrateError);
            }
        });
    }

    @Override
    public void rollback(FlowRollback chain, Map data) {
        chain.rollback();
    }
}

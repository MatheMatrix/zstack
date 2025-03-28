package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.header.allocator.*;
import org.zstack.header.core.WhileDoneCompletion;
import org.zstack.header.core.workflow.Flow;
import org.zstack.header.core.workflow.FlowRollback;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.host.HostInventory;
import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.primary.GetVolumeBackingChainFromPrimaryStorageMsg;
import org.zstack.header.storage.primary.GetVolumeBackingChainFromPrimaryStorageReply;
import org.zstack.header.storage.primary.PrimaryStorageConstant;
import org.zstack.header.vm.*;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.*;

import static org.zstack.core.Platform.operr;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class CheckVmVolumeSnapshotChainForStartVmFlow implements Flow {
    private static final CLogger logger = Utils.getLogger(CheckVmVolumeSnapshotChainForStartVmFlow.class);
    @Autowired
    protected DatabaseFacade dbf;
    @Autowired
    protected CloudBus bus;
    @Autowired
    protected ErrorFacade errf;
    @Autowired
    protected VmInstanceExtensionPointEmitter extEmitter;

    private static final String SUCCESS = CheckVmVolumeSnapshotChainForStartVmFlow.class.getName();

    @Override
    public void run(final FlowTrigger chain, final Map data) {
        final VmInstanceSpec spec = (VmInstanceSpec) data.get(VmInstanceConstant.Params.VmInstanceSpec.toString());
        Map<String, List<String>> volumesSnapshotChain = VmVolumeSnapshotChainUtil.getVmVolumesAliveSnapshotChain(spec.getVmInventory());
        logger.debug("============>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        logger.debug(volumesSnapshotChain.toString());
        logger.debug(volumesSnapshotChain.keySet().toString());
        logger.debug(volumesSnapshotChain.values().toString());

        List<ErrorCode> errors = new ArrayList<>();
        new While<>(spec.getVmInventory().getAllDiskVolumes()).all((volume, completion) -> {
            GetVolumeBackingChainFromPrimaryStorageMsg gmsg = new GetVolumeBackingChainFromPrimaryStorageMsg();
            gmsg.setVolumeUuid(volume.getUuid());
            gmsg.setRootInstallPaths(Collections.singletonList(volume.getInstallPath()));
            gmsg.setPrimaryStorageUuid(volume.getPrimaryStorageUuid());
            gmsg.setVolumeFormat(volume.getFormat());
            bus.makeTargetServiceIdByResourceUuid(gmsg, PrimaryStorageConstant.SERVICE_ID, gmsg.getPrimaryStorageUuid());
            bus.send(gmsg, new CloudBusCallBack(chain) {
                @Override
                public void run(MessageReply reply) {
                    if (!reply.isSuccess()) {
                        errors.add(reply.getError());
                        return;
                    }

                    GetVolumeBackingChainFromPrimaryStorageReply gr = reply.castReply();
                    List<String> backingChainInstallPath = gr.getBackingChainInstallPath().get(volume.getInstallPath());

                    List<String> aliveChainInDB = volumesSnapshotChain.get(volume.getUuid());

                    if (backingChainInstallPath.size() < aliveChainInDB.size()) {
                        throw new OperationFailureException(operr(""));
                    }

                    int top_index = backingChainInstallPath.indexOf(aliveChainInDB.get(0));
                    int base_index = backingChainInstallPath.indexOf(aliveChainInDB.get(aliveChainInDB.size() - 1));
                    List<String> new_chain_in_xml = backingChainInstallPath.subList(top_index, base_index + 1);

                    for (int i = 0; i < new_chain_in_xml.size() - 1; i++) {
                        if (!new_chain_in_xml.get(i).equals(aliveChainInDB.get(i))) {
                            throw new OperationFailureException(operr(""));
                        }
                    }

                    completion.done();
                }
            });
        }).run(new WhileDoneCompletion(chain) {
            @Override
            public void done(ErrorCodeList errorCodeList) {

            }
        });
    }

    @Override
    public void rollback(FlowRollback chain, Map data) {
        final VmInstanceSpec spec = (VmInstanceSpec) data.get(VmInstanceConstant.Params.VmInstanceSpec.toString());
        if (data.containsKey(SUCCESS)) {
            VmInstanceVO vm = dbf.findByUuid(spec.getVmInventory().getUuid(), VmInstanceVO.class);
            vm.setHostUuid(null);
            dbf.update(vm);

            HostInventory host = spec.getDestHost();
            ReturnHostCapacityMsg msg = new ReturnHostCapacityMsg();
            msg.setCpuCapacity(spec.getVmInventory().getCpuNum());
            msg.setMemoryCapacity(spec.getVmInventory().getMemorySize());
            msg.setHostUuid(host.getUuid());
            msg.setServiceId(bus.makeLocalServiceId(HostAllocatorConstant.SERVICE_ID));
            bus.send(msg);

            extEmitter.cleanUpAfterVmFailedToStart(spec.getVmInventory(), spec.getCurrentVmOperation());
        }
        chain.rollback();
    }
}

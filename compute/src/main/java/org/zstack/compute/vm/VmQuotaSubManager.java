package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.compute.vm.quota.*;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.header.apimediator.ApiMessageInterceptionException;
import org.zstack.header.configuration.DiskOfferingVO;
import org.zstack.header.configuration.DiskOfferingVO_;
import org.zstack.header.configuration.InstanceOfferingVO;
import org.zstack.header.configuration.InstanceOfferingVO_;
import org.zstack.header.identity.*;
import org.zstack.header.identity.Quota.QuotaPair;
import org.zstack.header.identity.quota.QuotaMessageHandler;
import org.zstack.header.image.ImageConstant;
import org.zstack.header.vm.*;
import org.zstack.header.volume.APICreateDataVolumeMsg;
import org.zstack.header.volume.APIRecoverDataVolumeMsg;

import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.zstack.core.Platform.argerr;
import static org.zstack.core.Platform.operr;
import static org.zstack.utils.CollectionDSL.list;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.*;

/**
 * Manages VM quota definitions and quota message handler registration.
 * Extracted from VmInstanceManagerImpl to reduce God Class complexity.
 */
public class VmQuotaSubManager implements ReportQuotaExtensionPoint {

    @Autowired
    private DatabaseFacade dbf;

    @Override
    public List<Quota> reportQuota() {
        Quota quota = new Quota();
        quota.defineQuota(new VmTotalNumQuotaDefinition());
        quota.defineQuota(new VmRunningNumQuotaDefinition());
        quota.defineQuota(new VmRunningCpuNumQuotaDefinition());
        quota.defineQuota(new VmRunningMemoryNumQuotaDefinition());
        quota.defineQuota(new DataVolumeNumQuotaDefinition());
        quota.defineQuota(new VolumeSizeQuotaDefinition());
        quota.addQuotaMessageChecker(new QuotaMessageHandler<>(APICreateVmInstanceMsg.class)
                .addCounterQuota(VmQuotaConstant.VM_TOTAL_NUM)
                .addCounterQuota(VmQuotaConstant.VM_RUNNING_NUM)
                .addMessageRequiredQuotaHandler(VmQuotaConstant.VM_RUNNING_CPU_NUM, (msg) -> {
                    if (msg.getCpuNum() != null) {
                        return Integer.toUnsignedLong(msg.getCpuNum());
                    }

                    Integer cpuNum = Q.New(InstanceOfferingVO.class)
                            .select(InstanceOfferingVO_.cpuNum)
                            .eq(InstanceOfferingVO_.uuid, msg.getInstanceOfferingUuid())
                            .findValue();
                    return Integer.toUnsignedLong(cpuNum);
                }).addMessageRequiredQuotaHandler(VmQuotaConstant.VM_RUNNING_MEMORY_SIZE, (msg) -> {
                    if (msg.getMemorySize() != null) {
                        return msg.getMemorySize();
                    }

                    return Q.New(InstanceOfferingVO.class)
                            .select(InstanceOfferingVO_.memorySize)
                            .eq(InstanceOfferingVO_.uuid, msg.getInstanceOfferingUuid())
                            .findValue();
                }).addMessageRequiredQuotaHandler(VmQuotaConstant.DATA_VOLUME_NUM, (msg) -> {
                    if (msg.getDataDiskOfferingUuids() == null || msg.getDataDiskOfferingUuids().isEmpty()) {
                        return 0L;
                    }

                    return (long) (msg.getDataDiskOfferingUuids().size());
                }).addMessageRequiredQuotaHandler(VmQuotaConstant.VOLUME_SIZE, (msg) -> {
                    long allVolumeSizeAsked = 0;

                    String sql;
                    Long imgSize;
                    ImageConstant.ImageMediaType imgType = null;
                    if (msg.getImageUuid() != null) {
                        sql = "select img.size, img.mediaType" +
                                " from ImageVO img" +
                                " where img.uuid = :iuuid";
                        TypedQuery<Tuple> iq = dbf.getEntityManager().createQuery(sql, Tuple.class);
                        iq.setParameter("iuuid", msg.getImageUuid());
                        Tuple it = iq.getSingleResult();
                        imgSize = it.get(0, Long.class);
                        imgType = it.get(1, ImageConstant.ImageMediaType.class);
                    } else {
                        imgSize = 0L;
                    }

                    List<String> diskOfferingUuids = new ArrayList<>();
                    if (msg.getDataDiskOfferingUuids() != null && !msg.getDataDiskOfferingUuids().isEmpty()) {
                        diskOfferingUuids.addAll(msg.getDataDiskOfferingUuids());
                    }
                    if (imgType == ImageConstant.ImageMediaType.RootVolumeTemplate) {
                        if (msg.getRootDiskOfferingUuid() != null) {
                            diskOfferingUuids.add(msg.getRootDiskOfferingUuid());
                        } else {
                            allVolumeSizeAsked += imgSize;
                        }
                    } else if (imgType == ImageConstant.ImageMediaType.ISO) {
                        if (msg.getRootDiskOfferingUuid() != null) {
                            diskOfferingUuids.add(msg.getRootDiskOfferingUuid());
                        } else if (msg.getRootDiskSize() != null) {
                            allVolumeSizeAsked += msg.getRootDiskSize();
                        } else {
                            throw new ApiMessageInterceptionException(argerr(ORG_ZSTACK_COMPUTE_VM_10261, "rootDiskOfferingUuid cannot be null when image mediaType is ISO"));
                        }
                    } else {
                        if (msg.getRootDiskOfferingUuid() != null) {
                            diskOfferingUuids.add(msg.getRootDiskOfferingUuid());
                        } else if (msg.getRootDiskSize() != null) {
                            allVolumeSizeAsked += msg.getRootDiskSize();
                        } else {
                            throw new ApiMessageInterceptionException(argerr(ORG_ZSTACK_COMPUTE_VM_10262, "rootDiskOfferingUuid cannot be null when create vm without image"));
                        }
                    }

                    HashMap<String, Long> diskOfferingCountMap = new HashMap<>();
                    if (!diskOfferingUuids.isEmpty()) {
                        for (String diskOfferingUuid : diskOfferingUuids) {
                            if (diskOfferingCountMap.containsKey(diskOfferingUuid)) {
                                diskOfferingCountMap.put(diskOfferingUuid, diskOfferingCountMap.get(diskOfferingUuid) + 1);
                            } else {
                                diskOfferingCountMap.put(diskOfferingUuid, 1L);
                            }
                        }
                        for (String diskOfferingUuid : diskOfferingCountMap.keySet()) {
                            sql = "select diskSize from DiskOfferingVO where uuid = :uuid";
                            TypedQuery<Long> dq = dbf.getEntityManager().createQuery(sql, Long.class);
                            dq.setParameter("uuid", diskOfferingUuid);
                            Long dsize = dq.getSingleResult();
                            dsize = dsize == null ? 0 : dsize;
                            allVolumeSizeAsked += dsize * diskOfferingCountMap.get(diskOfferingUuid);
                        }
                    }

                    return allVolumeSizeAsked;
                }).addCheckCondition((msg) -> !msg.getStrategy().equals(VmCreationStrategy.JustCreate.toString())));

        quota.addQuotaMessageChecker(new QuotaMessageHandler<>(APIRecoverVmInstanceMsg.class)
                .addCounterQuota(VmQuotaConstant.VM_TOTAL_NUM));
        quota.addQuotaMessageChecker(new QuotaMessageHandler<>(APIStartVmInstanceMsg.class)
                .addCounterQuota(VmQuotaConstant.VM_RUNNING_NUM)
                .addMessageRequiredQuotaHandler(VmQuotaConstant.VM_RUNNING_CPU_NUM, (msg) -> new VmQuotaUtil().getRequiredCpu(msg.getVmInstanceUuid()))
                .addMessageRequiredQuotaHandler(VmQuotaConstant.VM_RUNNING_MEMORY_SIZE, (msg) -> new VmQuotaUtil().getRequiredMemory(msg.getVmInstanceUuid())));
        quota.addQuotaMessageChecker(new QuotaMessageHandler<>(StartVmInstanceMsg.class)
                .addCounterQuota(VmQuotaConstant.VM_RUNNING_NUM)
                .addMessageRequiredQuotaHandler(VmQuotaConstant.VM_RUNNING_CPU_NUM, (msg) -> new VmQuotaUtil().getRequiredCpu(msg.getVmInstanceUuid()))
                .addMessageRequiredQuotaHandler(VmQuotaConstant.VM_RUNNING_MEMORY_SIZE, (msg) -> new VmQuotaUtil().getRequiredMemory(msg.getVmInstanceUuid())));
        quota.addQuotaMessageChecker(new QuotaMessageHandler<>(APICreateDataVolumeMsg.class)
                .addMessageRequiredQuotaHandler(VmQuotaConstant.VOLUME_SIZE, (msg) -> {
                    if (msg.getDiskOfferingUuid() == null) {
                        return msg.getDiskSize();
                    }

                    String sql = "select diskSize from DiskOfferingVO where uuid = :uuid ";
                    TypedQuery<Long> dq = dbf.getEntityManager().createQuery(sql, Long.class);
                    dq.setParameter("uuid", msg.getDiskOfferingUuid());
                    Long dsize = dq.getSingleResult();
                    dsize = dsize == null ? 0 : dsize;
                    return dsize;
                })
                .addCounterQuota(VmQuotaConstant.DATA_VOLUME_NUM));
        quota.addQuotaMessageChecker(new QuotaMessageHandler<>(APIRecoverDataVolumeMsg.class)
                .addCounterQuota(VmQuotaConstant.DATA_VOLUME_NUM));

        quota.addQuotaMessageChecker(new QuotaMessageHandler<>(APIChangeResourceOwnerMsg.class)
                .addCheckCondition((msg) -> Q.New(VmInstanceVO.class)
                        .eq(VmInstanceVO_.uuid, msg.getResourceUuid())
                        .notEq(VmInstanceVO_.type, "baremetal2")
                        .isExists())
                .addCounterQuota(VmQuotaConstant.VM_TOTAL_NUM)
                .addMessageRequiredQuotaHandler(VmQuotaConstant.VM_RUNNING_CPU_NUM, (msg) -> {
                    VmInstanceState state = Q.New(VmInstanceVO.class)
                            .select(VmInstanceVO_.state)
                            .eq(VmInstanceVO_.uuid, msg.getResourceUuid())
                            .findValue();

                    // vm is running
                    if (list(VmInstanceState.Stopped, VmInstanceState.Destroying,
                            VmInstanceState.Destroyed, VmInstanceState.Created).contains(state)) {
                        return 0L;
                    }

                    return new VmQuotaUtil().getRequiredCpu(msg.getResourceUuid());
                }).addMessageRequiredQuotaHandler(VmQuotaConstant.VM_RUNNING_MEMORY_SIZE, (msg) -> {
                    VmInstanceState state = Q.New(VmInstanceVO.class)
                            .select(VmInstanceVO_.state)
                            .eq(VmInstanceVO_.uuid, msg.getResourceUuid())
                            .findValue();

                    // vm is running
                    if (list(VmInstanceState.Stopped, VmInstanceState.Destroying,
                            VmInstanceState.Destroyed, VmInstanceState.Created).contains(state)) {
                        return 0L;
                    }

                    return new VmQuotaUtil().getRequiredMemory(msg.getResourceUuid());
                }).addMessageRequiredQuotaHandler(VmQuotaConstant.VM_RUNNING_NUM, (msg) -> {
                    VmInstanceState state = Q.New(VmInstanceVO.class)
                            .select(VmInstanceVO_.state)
                            .eq(VmInstanceVO_.uuid, msg.getResourceUuid())
                            .findValue();

                    // vm is running
                    if (list(VmInstanceState.Stopped, VmInstanceState.Destroying,
                            VmInstanceState.Destroyed, VmInstanceState.Created).contains(state)) {
                        return 0L;
                    }

                    return 1L;
                }));
        return list(quota);
    }
}

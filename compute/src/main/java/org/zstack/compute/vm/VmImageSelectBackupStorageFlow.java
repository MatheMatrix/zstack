package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.image.ImageBackupStorageRefInventory;
import org.zstack.header.image.ImageConstant.ImageMediaType;
import org.zstack.header.image.ImageStatus;
import org.zstack.header.storage.backup.BackupStorageStatus;
import org.zstack.header.storage.primary.*;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.header.vm.VmInstanceConstant.VmOperation;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.DebugUtils;
import org.zstack.utils.function.Function;

import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.zstack.core.Platform.operr;
import static org.zstack.core.progress.ProgressReportService.taskProgress;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.*;

/**
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class VmImageSelectBackupStorageFlow extends NoRollbackFlow {
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private ErrorFacade errf;
    @Autowired
    private PluginRegistry pluginRgty;

    private String findBackupStorage(VmInstanceSpec spec, String imageUuid) {
        taskProgress("Choose backup storage for downloading the image");

        spec.getImageSpec().setNeedDownload(imageNeedDownload(spec, imageUuid));
        if (!spec.getImageSpec().isNeedDownload() && spec.getImageSpec().getInventory().getBackupStorageRefs().isEmpty()) {
            return null;
        }

        if (spec.getImageSpec().getInventory().getBackupStorageRefs().size() == 1) {
            return spec.getImageSpec().getInventory().getBackupStorageRefs().iterator().next().getBackupStorageUuid();
        }

        DebugUtils.Assert(spec.getVmInventory().getZoneUuid() != null, "zone uuid must be set if the image is on multiple backup storage");

        ImageBackupStorageSelector selector = new ImageBackupStorageSelector();
        selector.setZoneUuid(spec.getVmInventory().getZoneUuid());
        selector.setImageUuid(imageUuid);
        String bsUuid = selector.select();

        if (bsUuid != null) {
            return bsUuid;
        }

        if (!spec.getImageSpec().isNeedDownload()) {
            // the image is already on the primary storage,
            // in this case, the backup storage needs not to be Connected
            selector.setCheckStatus(false);
            bsUuid = selector.select();
            if (bsUuid != null) {
                return bsUuid;
            }
        }

        String imageName = spec.getImageSpec().getInventory().getName();
        String imageBsInfo = getImageBackupStorageInfo(imageUuid);
        if (spec.getVmInventory().getZoneUuid() != null) {
            String zoneUuid = spec.getVmInventory().getZoneUuid();
            String zoneBsInfo = getZoneBackupStorageInfo(zoneUuid);
            throw new OperationFailureException(operr(ORG_ZSTACK_COMPUTE_VM_10085,
                    "cannot find the image[name:%s, uuid:%s] in any connected backup storage" +
                            " attached to the zone[uuid:%s]." +
                            "\nimage is on backup storage: %s" +
                            "\nzone attached backup storage: %s" +
                            "\nsuggestion: attach the image's backup storage to the zone," +
                            " or sync the image to an attached and connected backup storage.",
                    imageName, imageUuid, zoneUuid, imageBsInfo, zoneBsInfo)
            );
        } else {
            throw new OperationFailureException(operr(ORG_ZSTACK_COMPUTE_VM_10086,
                    "cannot find the image[name:%s, uuid:%s] in any connected backup storage." +
                            "\nimage is on backup storage: %s" +
                            "\nsuggestion: ensure the backup storage is connected," +
                            " or sync the image to a connected backup storage.",
                    imageName, imageUuid, imageBsInfo)
            );
        }
    }

    private boolean imageNeedDownload(VmInstanceSpec spec, String imageUuid) {
        List<String> psUuid;
        if (VmOperation.NewCreate == spec.getCurrentVmOperation()) {
            psUuid = spec.getVolumeSpecs().isEmpty() ? spec.getCandidatePrimaryStorageUuidsForRootVolume() :
                    Collections.singletonList(spec.getVolumeSpecs().get(0).getPrimaryStorageInventory().getUuid());
        } else {
            psUuid = Collections.singletonList(spec.getVmInventory().getRootVolume().getPrimaryStorageUuid());
        }

        if (psUuid.isEmpty()) {
            return true;
        }

        List<String> hasImageCachePsUuids = Q.New(ImageCacheVO.class).eq(ImageCacheVO_.imageUuid, imageUuid)
                .in(ImageCacheVO_.primaryStorageUuid, psUuid)
                .select(ImageCacheVO_.primaryStorageUuid)
                .listValues();

        return new HashSet<>(hasImageCachePsUuids).size() < psUuid.size();
    }

    @Transactional(readOnly = true)
    private String findIsoBsUuidInTheZone(final String isoImageUuid, final String zoneUuid) {
        String sql = "select ref.backupStorageUuid" +
                " from ImageBackupStorageRefVO ref, BackupStorageZoneRefVO zoneref" +
                " where ref.backupStorageUuid = zoneref.backupStorageUuid" +
                " and zoneref.zoneUuid = :zoneUuid" +
                " and ref.imageUuid = :imgUuid";

        TypedQuery<String> q = dbf.getEntityManager().createQuery(sql, String.class);
        q.setParameter("zoneUuid", zoneUuid);
        q.setParameter("imgUuid", isoImageUuid);
        q.setMaxResults(1);
        List<String> ret = q.getResultList();
        if (ret.isEmpty()) {
            String isoName = Optional.ofNullable((String) Q.New(org.zstack.header.image.ImageVO.class)
                    .eq(org.zstack.header.image.ImageVO_.uuid, isoImageUuid)
                    .select(org.zstack.header.image.ImageVO_.name)
                    .findValue()).orElse(isoImageUuid);
            String isoBsInfo = getImageBackupStorageInfo(isoImageUuid);
            String zoneBsInfo = getZoneBackupStorageInfo(zoneUuid);
            throw new OperationFailureException(operr(ORG_ZSTACK_COMPUTE_VM_10087,
                    "no backup storage attached to the zone[uuid:%s] contains the ISO[name:%s, uuid:%s]." +
                            "\nISO is on backup storage: %s" +
                            "\nzone attached backup storage: %s" +
                            "\nsuggestion: attach the ISO's backup storage to the zone," +
                            " or sync the ISO to an attached backup storage.",
                    zoneUuid, isoName, isoImageUuid, isoBsInfo, zoneBsInfo));
        }

        return ret.get(0);
    }

    @Transactional(readOnly = true)
    private String getImageBackupStorageInfo(String imageUuid) {
        String sql = "select bs.name, bs.status" +
                " from BackupStorageVO bs, ImageBackupStorageRefVO ref" +
                " where bs.uuid = ref.backupStorageUuid" +
                " and ref.imageUuid = :imageUuid";
        TypedQuery<Tuple> q = dbf.getEntityManager().createQuery(sql, Tuple.class);
        q.setParameter("imageUuid", imageUuid);
        List<Tuple> tuples = q.getResultList();
        if (tuples.isEmpty()) {
            return "none";
        }
        return tuples.stream()
                .map(t -> String.format("%s(%s)", t.get(0, String.class), t.get(1, BackupStorageStatus.class)))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    @Transactional(readOnly = true)
    private String getZoneBackupStorageInfo(String zoneUuid) {
        String sql = "select bs.name, bs.status" +
                " from BackupStorageVO bs, BackupStorageZoneRefVO ref" +
                " where bs.uuid = ref.backupStorageUuid" +
                " and ref.zoneUuid = :zoneUuid";
        TypedQuery<Tuple> q = dbf.getEntityManager().createQuery(sql, Tuple.class);
        q.setParameter("zoneUuid", zoneUuid);
        List<Tuple> tuples = q.getResultList();
        if (tuples.isEmpty()) {
            return "none";
        }
        return tuples.stream()
                .map(t -> String.format("%s(%s)", t.get(0, String.class), t.get(1, BackupStorageStatus.class)))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    @Override
    public void run(FlowTrigger trigger, Map data) {
        VmInstanceSpec spec = (VmInstanceSpec) data.get(VmInstanceConstant.Params.VmInstanceSpec.toString());

        if (VmOperation.NewCreate == spec.getCurrentVmOperation()
                || VmOperation.ChangeImage == spec.getCurrentVmOperation()) {
            if (spec.getImageSpec().getInventory() == null) {
                trigger.next();
                return;
            }

            final String bsUuid = findBackupStorage(spec, spec.getImageSpec().getInventory().getUuid());
            spec.getImageSpec().setSelectedBackupStorage(CollectionUtils.find(
                    spec.getImageSpec().getInventory().getBackupStorageRefs(),
                    new Function<ImageBackupStorageRefInventory, ImageBackupStorageRefInventory>() {
                        @Override
                        public ImageBackupStorageRefInventory call(ImageBackupStorageRefInventory arg) {
                            return arg.getBackupStorageUuid().equals(bsUuid)
                                    && ImageStatus.Ready.toString().equals(arg.getStatus())
                                    ? arg : null;
                        }
                    }));

            if (ImageMediaType.ISO.toString().equals(spec.getImageSpec().getInventory().getMediaType())) {
                spec.getCdRomSpecs().get(0).setBackupStorageUuid(bsUuid);
            }

            spec.getCdRomSpecs().forEach(cdRomSpec -> {
                if (cdRomSpec.getBackupStorageUuid() != null) {
                    return;
                }
                if (cdRomSpec.getImageUuid() == null) {
                    return;
                }
                cdRomSpec.setBackupStorageUuid(
                        findIsoBsUuidInTheZone(cdRomSpec.getImageUuid(), spec.getVmInventory().getZoneUuid())
                );
            });
        } else if ((VmOperation.Start == spec.getCurrentVmOperation()
                || VmOperation.Reboot == spec.getCurrentVmOperation())
                && !spec.getCdRomSpecs().isEmpty()) {
            spec.getCdRomSpecs().forEach(cdRomSpec -> {
                if (cdRomSpec.getImageUuid() == null) {
                    return;
                }
                cdRomSpec.setBackupStorageUuid(
                        findIsoBsUuidInTheZone(cdRomSpec.getImageUuid(), spec.getVmInventory().getZoneUuid())
                );
            });
        }

        trigger.next();
    }
}

package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.Platform;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.allocator.HostAllocatorError;
import org.zstack.header.allocator.HostAllocatorSpec;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.host.HostVO;
import org.zstack.header.image.ImageBackupStorageRefInventory;
import org.zstack.header.image.ImageStatus;
import org.zstack.header.storage.backup.*;
import org.zstack.header.storage.primary.*;
import org.zstack.header.vm.VmInstanceConstant.VmOperation;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.function.Function;
import org.zstack.utils.logging.CLogger;

import java.util.*;

import static org.zstack.core.Platform.err;
import static org.zstack.core.Platform.operr;

/**
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class ImageBackupStorageAllocatorFlow extends AbstractHostAllocatorFlow {
    private static final CLogger logger = Utils.getLogger(ImageBackupStorageAllocatorFlow.class);

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private ErrorFacade errf;

    private boolean checkIfNeedBackupStorageToDownloadImage(HostAllocatorSpec spec, List<HostVO> candidates) {
        if (Q.New(ImageCacheVolumeRefVO.class).eq(ImageCacheVolumeRefVO_.imageUuid, spec.getImage().getUuid()).isExists()) {
            return false;
        }

        List<String> clusterUuids = CollectionUtils.transformToList(candidates, new Function<String, HostVO>() {
            @Override
            public String call(HostVO arg) {
                return arg.getClusterUuid();
            }
        });

        SimpleQuery<PrimaryStorageClusterRefVO> pq = dbf.createQuery(PrimaryStorageClusterRefVO.class);
        pq.select(PrimaryStorageClusterRefVO_.primaryStorageUuid);
        pq.add(PrimaryStorageClusterRefVO_.clusterUuid, Op.IN, clusterUuids);
        Set<String> psUuids = new HashSet<>(pq.listValue());
        if (psUuids.isEmpty()) {
            return true;
        }

        if (spec.getRequiredPrimaryStorageUuids() != null && !spec.getRequiredPrimaryStorageUuids().isEmpty()) {
            psUuids.retainAll(spec.getRequiredPrimaryStorageUuids());
            if (psUuids.isEmpty()) {
                return true;
            }
        }

        long imageCacheCount = SQL.New("select count(distinct cache.primaryStorageUuid) from ImageCacheVO cache where cache.primaryStorageUuid in :psUuids and cache.imageUuid = :imageUuid", Long.class)
                .param("psUuids", psUuids)
                .param("imageUuid", spec.getImage().getUuid())
                .find();
        long imageCacheShadowCount = SQL.New("select count(distinct cache.primaryStorageUuid) from ImageCacheShadowVO cache where cache.primaryStorageUuid in :psUuids and cache.imageUuid = :imageUuid", Long.class)
                .param("psUuids", psUuids)
                .param("imageUuid", spec.getImage().getUuid())
                .find();

        return Math.max(imageCacheCount, imageCacheShadowCount) < psUuids.size();
    }

    @Override
    public void allocate() {
        if (!VmOperation.NewCreate.toString().equals(spec.getVmOperation())) {
            next(candidates);
            return;
        }

        if (spec.getImage() == null){
            next(candidates);
            return;
        }

        throwExceptionIfIAmTheFirstFlow();

        if (!checkIfNeedBackupStorageToDownloadImage(spec, candidates)) {
            next(candidates);
            return;
        }

        List<String> bsUuids = CollectionUtils.transformToList(spec.getImage().getBackupStorageRefs(), new Function<String, ImageBackupStorageRefInventory>() {
            @Override
            public String call(ImageBackupStorageRefInventory arg) {
                return ImageStatus.Deleted.toString().equals(arg.getStatus()) ? null : arg.getBackupStorageUuid();
            }
        });

        if (bsUuids.isEmpty()) {
            throw new OperationFailureException(operr(
                    "the image[uuid:%s, name:%s] is deleted on all backup storage", spec.getImage().getUuid(), spec.getImage().getName()
            ));
        }

        SimpleQuery<BackupStorageVO> bq = dbf.createQuery(BackupStorageVO.class);
        bq.select(BackupStorageVO_.uuid);
        bq.add(BackupStorageVO_.status, Op.EQ, BackupStorageStatus.Connected);
        bq.add(BackupStorageVO_.uuid, Op.IN, bsUuids);
        bsUuids = bq.listValue();
        if (bsUuids.isEmpty()) {
            // we stop allocation on purpose, to prevent further pagination proceeding
            throw new OperationFailureException(err(HostAllocatorError.NO_AVAILABLE_HOST,
                    "all backup storage that image[uuid:%s] is on can not satisfy conditions[status = %s]",
                    spec.getImage().getUuid(), BackupStorageStatus.Connected.toString()
            ));
        }

        SimpleQuery<BackupStorageZoneRefVO> q = dbf.createQuery(BackupStorageZoneRefVO.class);
        q.select(BackupStorageZoneRefVO_.zoneUuid);
        q.add(BackupStorageZoneRefVO_.backupStorageUuid, Op.IN, bsUuids);
        final List<String> zoneUuids = q.listValue();

        candidates = CollectionUtils.transformToList(candidates, new Function<HostVO, HostVO>() {
            @Override
            public HostVO call(HostVO arg) {
                if (zoneUuids.contains(arg.getZoneUuid())) {
                    return arg;
                }
                return null;
            }
        });

        if (candidates.isEmpty()) {
            fail(Platform.operr("no host found in zones[uuids:%s] that attaches to backup storage where image[%s] is on", zoneUuids, spec.getImage().getUuid()));
        } else {
            next(candidates);
        }
    }
}

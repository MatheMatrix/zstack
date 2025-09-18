package org.zstack.storage.volume;

import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.Platform;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.volume.VolumeProvisioningStrategy;
import org.zstack.tag.SystemTagCreator;

import javax.persistence.TypedQuery;

import static org.zstack.utils.CollectionDSL.e;
import static org.zstack.utils.CollectionDSL.map;

public class VolumeUtils {
    public static void SetVolumeProvisioningStrategy(String volumeUuid, VolumeProvisioningStrategy strategy) {
        SystemTagCreator tagCreator = VolumeSystemTags.VOLUME_PROVISIONING_STRATEGY.newSystemTagCreator(volumeUuid);
        tagCreator.setTagByTokens(
                map(
                        e(VolumeSystemTags.VOLUME_PROVISIONING_STRATEGY_TOKEN, strategy.toString())
                )
        );
        tagCreator.inherent = false;
        tagCreator.recreate = true;
        tagCreator.create();
    }

    @Transactional(readOnly = true)
    public static long calculateSnapshotSizeByVolume(String volumeUuid) {
        DatabaseFacade dbf = Platform.getComponentLoader().getComponent(DatabaseFacade.class);
        String sql = "select sum(sp.size) from VolumeSnapshotVO sp where sp.volumeUuid = :uuid";
        TypedQuery<Long> q = dbf.getEntityManager().createQuery(sql, Long.class);
        q.setParameter("uuid", volumeUuid);
        Long size = q.getSingleResult();
        return size == null ? 0 : size;
    }
}

package org.zstack.storage.zbs;

import org.zstack.core.db.SQL;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.storage.primary.PrimaryStorageVO_;

/**
 * @author Xingwei Yu
 * @date 2024/4/9 17:50
 */
public class ZbsHelper {
    public static final String ZBS_HEARTBEAT_VOLUME_NAME = "zbs_zstack_heartbeat";
    public static final String ZBS_CBD_LUN_PATH_FORMAT = "cbd:%s/%s/%s";
    public static final String ZBS_CBD_PREFIX_SCHEME = "cbd://";

    public static void configUrl(String psUuid) {
        SQL.New(PrimaryStorageVO.class).eq(PrimaryStorageVO_.uuid, psUuid).set(PrimaryStorageVO_.url, ZBS_CBD_PREFIX_SCHEME + psUuid).update();
    }

    public static String buildVolumePath(String physicalPoolName, String logicalPoolName, String volId) {
        String base = volId.replace("-", "");
        return String.format(ZBS_CBD_LUN_PATH_FORMAT, physicalPoolName, logicalPoolName, base);
    }

    public static String getLogicalPoolNameFromPath(String url) {
        return url.split("/")[1];
    }

    public static String getPhysicalPoolNameFromPath(String url) {
        return url.split("/")[0].split(":")[1];
    }

    public static String getLunNameFromPath(String url) {
        return url.split("/")[2].split("@")[0];
    }

    public static String getSnapshotNameFromPath(String url) {
        return url.split("/")[2].split("@")[1];
    }

    public static String getVolumeInstallPathFromSnapshot(String snapshotInstallPath) {
        return snapshotInstallPath.split("@")[0];
    }
}

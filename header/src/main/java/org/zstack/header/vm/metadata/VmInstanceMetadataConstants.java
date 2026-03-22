package org.zstack.header.vm.metadata;

public class VmInstanceMetadataConstants {
    private VmInstanceMetadataConstants() {
    }

    public static final String SBLK_METADATA_LV_SUFFIX = "_vmmeta";
    public static final String FILE_METADATA_SUFFIX = ".vmmeta";
    public static final String METADATA_DIR_NAME = "vm-metadata";

    private static final String[] STORAGE_PATH_MARKERS = {
            "/rootVolumes/", "/dataVolumes/", "/volumeSnapshots/", "/memory/"
    };

    public static String extractOldPrefix(String path) {
        if (path == null || !path.startsWith("/")) {
            return null;
        }
        int earliest = Integer.MAX_VALUE;
        for (String marker : STORAGE_PATH_MARKERS) {
            int idx = path.indexOf(marker);
            if (idx >= 0 && idx < earliest) {
                earliest = idx;
            }
        }
        return earliest == Integer.MAX_VALUE ? null : path.substring(0, earliest + 1);
    }
}
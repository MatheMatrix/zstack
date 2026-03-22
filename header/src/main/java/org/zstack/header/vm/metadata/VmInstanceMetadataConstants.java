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
        for (String marker : STORAGE_PATH_MARKERS) {
            int idx = path.indexOf(marker);
            if (idx >= 0) {
                return path.substring(0, idx + 1);
            }
        }
        return null;
    }
}
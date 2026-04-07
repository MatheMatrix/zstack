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

    /**
     * Extracts the storage prefix (mount path + marker) from a volume install path.
     * e.g. {@code /mnt/ps/rootVolumes/acct-xxx/vol-xxx/xxx.qcow2} → {@code /mnt/ps/rootVolumes/}
     * <p>
     * Uses the <b>last</b> occurrence of a marker, since everything after the
     * real marker is auto-generated and will never contain another marker string.
     */
    public static String extractOldPrefix(String path) {
        if (path == null || !path.startsWith("/")) {
            return null;
        }

        int latest = -1;
        String foundMarker = null;
        for (String marker : STORAGE_PATH_MARKERS) {
            int idx = path.lastIndexOf(marker);
            if (idx > latest) {
                latest = idx;
                foundMarker = marker;
            }
        }
        return foundMarker == null ? null : path.substring(0, latest + foundMarker.length());
    }
}
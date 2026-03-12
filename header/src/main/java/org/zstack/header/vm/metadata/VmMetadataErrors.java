package org.zstack.header.vm.metadata;

public enum VmMetadataErrors {
    METADATA_INVALID_FORMAT(1300),
    METADATA_SCHEMA_VERSION_MISMATCH(1301),
    METADATA_UUID_CONFLICT(1302),
    METADATA_STORAGE_NOT_SUPPORTED(1303),
    METADATA_CROSS_STORAGE_FORBIDDEN(1304),
    METADATA_INSTALL_PATH_NOT_FOUND(1305),
    METADATA_CACHE_VM_NOT_REGISTERABLE(1306),
    METADATA_VM_REGISTERING(1307),
    METADATA_READ_CORRUPTED(1308),
    METADATA_PAYLOAD_TOO_LARGE(1309),
    METADATA_PS_UNREACHABLE(1310),
    METADATA_FEATURE_DISABLED(1311),
    ;

    private String code;

    private VmMetadataErrors(int id) {
        code = String.format("VM_METADATA.%s", id);
    }

    @Override
    public String toString() {
        return code;
    }
}

package org.zstack.header.storage.primary;

public class VmInstanceMetadataSummary {
    private String uuid;
    private String name;
    private String path;
    private String architecture;
    private String hostUuid;
    private String schemaVersion;
    private boolean incomplete;
    private String vmCategory;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public boolean isIncomplete() {
        return incomplete;
    }

    public void setIncomplete(boolean incomplete) {
        this.incomplete = incomplete;
    }

    public String getVmCategory() {
        return vmCategory;
    }

    public void setVmCategory(String vmCategory) {
        this.vmCategory = vmCategory;
    }
}

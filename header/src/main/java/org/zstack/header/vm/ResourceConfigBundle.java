package org.zstack.header.vm;

public class ResourceConfigBundle {
    String uuid;
    String resourceUuid;
    String identity;
    String value;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getIdentity() {
        return identity;
    }

    public String getValue() {
        return value;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getResourceUuid() {
        return resourceUuid;
    }

    public void setResourceUuid(String resourceUuid) {
        this.resourceUuid = resourceUuid;
    }
}

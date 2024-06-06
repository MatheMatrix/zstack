package org.zstack.identity.imports.entity;

import org.zstack.core.Platform;

/**
 * For creating {@link ThirdPartyAccountSourceVO}
 */
public abstract class AbstractAccountSourceSpec {
    private String uuid = Platform.getUuid();
    private String type;
    private String description;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

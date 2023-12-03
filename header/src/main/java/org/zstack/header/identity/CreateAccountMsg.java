package org.zstack.header.identity;

import org.zstack.header.log.NoLogging;
import org.zstack.header.message.NeedReplyMessage;

/**
 * @Author: DaoDao
 * @Date: 2022/8/24
 */
public class CreateAccountMsg extends NeedReplyMessage {
    private String uuid;
    private String name;
    @NoLogging
    private String password;
    private String type;
    private String description;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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


package org.zstack.core.config;

import org.zstack.header.message.NeedReplyMessage;

import java.util.LinkedHashMap;

public class GlobalConfigUpdateMsg extends NeedReplyMessage {
    private LinkedHashMap globalConfigs;

    public LinkedHashMap getGlobalConfigs() {
        return globalConfigs;
    }

    public void setGlobalConfigs(LinkedHashMap globalConfigs) {
        this.globalConfigs = globalConfigs;
    }
}

package org.zstack.header.vm;

import org.zstack.header.message.MessageReply;

public class TakeVmProcessIdentifierCreateTimeReply extends MessageReply {
    private String createTime;

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}

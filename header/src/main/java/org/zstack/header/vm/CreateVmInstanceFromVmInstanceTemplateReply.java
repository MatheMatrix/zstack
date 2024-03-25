package org.zstack.header.vm;

import org.zstack.header.message.MessageReply;

public class CreateVmInstanceFromVmInstanceTemplateReply extends MessageReply {
    private CloneTemplateVmResults results;

    public CloneTemplateVmResults getResults() {
        return results;
    }

    public void setResults(CloneTemplateVmResults results) {
        this.results = results;
    }
}

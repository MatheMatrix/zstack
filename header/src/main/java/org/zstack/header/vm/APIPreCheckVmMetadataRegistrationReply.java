package org.zstack.header.vm;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

import java.util.Collections;
import java.util.List;

@RestResponse(fieldsTo = {"all"})
public class APIPreCheckVmMetadataRegistrationReply extends APIReply {
    private List<PreCheckItem> checkResults;

    public List<PreCheckItem> getCheckResults() {
        return checkResults;
    }

    public void setCheckResults(List<PreCheckItem> checkResults) {
        this.checkResults = checkResults;
    }

    public static APIPreCheckVmMetadataRegistrationReply __example__() {
        APIPreCheckVmMetadataRegistrationReply reply = new APIPreCheckVmMetadataRegistrationReply();
        PreCheckItem item = new PreCheckItem();
        item.setName("schema_version_check");
        item.setPassed(true);
        item.setMessage("Schema version 1.0 is supported");
        reply.checkResults = Collections.singletonList(item);
        return reply;
    }
}

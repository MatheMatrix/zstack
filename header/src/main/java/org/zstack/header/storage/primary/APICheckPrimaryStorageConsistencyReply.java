package org.zstack.header.storage.primary;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

@RestResponse(fieldsTo = {"all"})
public class APICheckPrimaryStorageConsistencyReply extends APIReply {
    private boolean consistent;

    public boolean isConsistent() {
        return consistent;
    }

    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }

    public static APICheckPrimaryStorageConsistencyReply __example__() {
        APICheckPrimaryStorageConsistencyReply reply = new APICheckPrimaryStorageConsistencyReply();
        reply.setConsistent(true);
        return reply;
    }
}

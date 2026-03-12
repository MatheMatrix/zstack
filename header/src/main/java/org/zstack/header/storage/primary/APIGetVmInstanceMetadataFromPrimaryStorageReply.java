package org.zstack.header.storage.primary;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;


@RestResponse(fieldsTo = {"all"})
public class APIGetVmInstanceMetadataFromPrimaryStorageReply extends APIReply {
    private String metadata;

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public static APIGetVmInstanceMetadataFromPrimaryStorageReply __example__() {
        APIGetVmInstanceMetadataFromPrimaryStorageReply reply = new APIGetVmInstanceMetadataFromPrimaryStorageReply();
        return reply;
    }
}

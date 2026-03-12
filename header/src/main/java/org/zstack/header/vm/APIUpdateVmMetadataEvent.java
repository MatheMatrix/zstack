package org.zstack.header.vm;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse(fieldsTo = {"all"})
public class APIUpdateVmMetadataEvent extends APIEvent {
    public APIUpdateVmMetadataEvent() {
        super(null);
    }

    public APIUpdateVmMetadataEvent(String apiId) {
        super(apiId);
    }

    public static APIUpdateVmMetadataEvent __example__() {
        return new APIUpdateVmMetadataEvent();
    }
}

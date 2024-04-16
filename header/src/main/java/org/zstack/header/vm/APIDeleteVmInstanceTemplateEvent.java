package org.zstack.header.vm;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse
public class APIDeleteVmInstanceTemplateEvent extends APIEvent {

    public APIDeleteVmInstanceTemplateEvent(String apiId) {
        super(apiId);
    }

    public APIDeleteVmInstanceTemplateEvent() {
        super(null);
    }

    public static APIDeleteVmInstanceTemplateEvent __example__() {
        return new APIDeleteVmInstanceTemplateEvent();
    }
}

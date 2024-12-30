package org.zstack.header.host;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

@RestResponse(fieldsTo = {"all"})
public class APIUpdateHostIscsiInitiatorNameEvent extends APIEvent {
    private HostInventory inventory;

    public APIUpdateHostIscsiInitiatorNameEvent() { super(null); }

    public APIUpdateHostIscsiInitiatorNameEvent(String apiId) {
        super(apiId);
    }

    public HostInventory getInventory() {
        return inventory;
    }

    public void setInventory(HostInventory inventory) {
        this.inventory = inventory;
    }

    public static APIUpdateHostIscsiInitiatorNameEvent __example__() {
        APIUpdateHostIscsiInitiatorNameEvent event = new APIUpdateHostIscsiInitiatorNameEvent();
        HostInventory hi = new HostInventory ();
        hi.setAvailableCpuCapacity(2L);
        hi.setAvailableMemoryCapacity(4L);
        hi.setClusterUuid(uuid());
        hi.setManagementIp("192.168.0.1");
        hi.setName("example");
        hi.setState(HostState.Enabled.toString());
        hi.setStatus(HostStatus.Connected.toString());
        hi.setClusterUuid(uuid());
        hi.setZoneUuid(uuid());
        hi.setUuid(uuid());
        hi.setTotalCpuCapacity(4L);
        hi.setTotalMemoryCapacity(4L);
        hi.setHypervisorType("KVM");
        hi.setDescription("example");
        hi.setNqn("nqn.2014-08.org.nvmexpress.example");
        event.setInventory(hi);
        return event;
    }
}

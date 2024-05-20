package org.zstack.header.network.l3;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;
import java.util.List;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;

@RestResponse(allTo = "inventory")
public class APIReserveIpRangeEvent extends APIEvent {
    /**
     * @desc see :ref:`IpRangeInventory`
     */
    private ReservedIpRangeInventory inventory;

    public APIReserveIpRangeEvent(String apiId) {
        super(apiId);
    }

    public APIReserveIpRangeEvent() {
        super(null);
    }

    public ReservedIpRangeInventory getInventory() {
        return inventory;
    }

    public void setInventory(ReservedIpRangeInventory inventory) {
        this.inventory = inventory;
    }

    public static APIReserveIpRangeEvent __example__() {
        APIReserveIpRangeEvent event = new APIReserveIpRangeEvent();
        ReservedIpRangeInventory ipRange = new ReservedIpRangeInventory();

        ipRange.setL3NetworkUuid(uuid());
        ipRange.setName("Test-IP-Range");
        ipRange.setStartIp("192.168.100.10");
        ipRange.setEndIp("192.168.100.250");

        event.setInventory(ipRange);
        return event;
    }

}

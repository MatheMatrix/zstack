package org.zstack.header.network.l3;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;
import java.util.List;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;

@RestResponse(allTo = "inventories")
public class APIReserveIpRangeEvent extends APIEvent {
    /**
     * @desc see :ref:`IpRangeInventory`
     */
    private List<IpRangeInventory> inventories;

    public APIReserveIpRangeEvent(String apiId) {
        super(apiId);
    }

    public APIReserveIpRangeEvent() {
        super(null);
    }

    public List<IpRangeInventory> getInventories() {
        return inventories;
    }

    public void setInventories(List<IpRangeInventory> inventories) {
        this.inventories = inventories;
    }

    public static APIReserveIpRangeEvent __example__() {
        APIReserveIpRangeEvent event = new APIReserveIpRangeEvent();
        IpRangeInventory ipRange = new IpRangeInventory();

        ipRange.setL3NetworkUuid(uuid());
        ipRange.setName("Test-IP-Range");
        ipRange.setStartIp("192.168.100.10");
        ipRange.setEndIp("192.168.100.250");
        ipRange.setNetmask("255.255.255.0");
        ipRange.setGateway("192.168.100.1");

        event.setInventories(asList(ipRange));
        return event;
    }

}

package org.zstack.header.vm.devices;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.List;

import static java.util.Arrays.asList;

/**
 * Created by LiangHanYu on 2022/6/20 18:03
 */
@AutoQuery(replyClass = APIQueryVmInstanceDeviceAddressGroupReply.class, inventoryClass = VmInstanceDeviceAddressGroupInventory.class)
@RestRequest(
        path = "/vmInstance/device/address/group",
        optionalPaths = {"/vmInstance/device/address/group/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryVmInstanceDeviceAddressGroupReply.class
)
public class APIQueryVmInstanceDeviceAddressGroupMsg extends APIQueryMessage {
    public static List<String> __example__() {
        return asList("uuid=" + uuid());
    }
}

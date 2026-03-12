package org.zstack.storage.ceph.primary;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.Collections;
import java.util.List;
import org.zstack.header.vm.MetadataImpact;

@RestRequest(
        path = "/primary-storage/ceph/osdgroups",
        optionalPaths = {"/primary-storage/ceph/osdgroups/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryCephOsdGroupReply.class
)
@AutoQuery(replyClass = APIQueryCephOsdGroupReply.class, inventoryClass = CephOsdGroupInventory.class)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryCephOsdGroupMsg extends APIQueryMessage {

    public static List<String> __example__() {
        return Collections.singletonList("uuid=" + uuid());
    }
}

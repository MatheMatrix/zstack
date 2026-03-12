package org.zstack.header.vm.cdrom;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;
import java.util.List;
import static java.util.Arrays.asList;
import org.zstack.header.vm.MetadataImpact;

/**
 * Create by lining at 2018/12/26
 */
@AutoQuery(replyClass = APIQueryVmCdRomReply.class, inventoryClass = VmCdRomInventory.class)
@RestRequest(
        path = "/vm-instances/cdroms",
        optionalPaths = {"/vm-instances/cdroms/{uuid}"},
        responseClass = APIQueryVmCdRomReply.class,
        method = HttpMethod.GET
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryVmCdRomMsg extends APIQueryMessage {
    public static List<String> __example__() {
        return asList("name=cd-1");
    }
}

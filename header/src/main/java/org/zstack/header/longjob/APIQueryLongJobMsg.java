package org.zstack.header.longjob;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.Arrays;
import java.util.List;
import org.zstack.header.vm.MetadataImpact;

/**
 * Created by GuoYi on 11/13/17.
 */
@AutoQuery(replyClass = APIQueryLongJobReply.class, inventoryClass = LongJobInventory.class)
@RestRequest(
        path = "/longjobs",
        optionalPaths = {"/longjobs/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryLongJobReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryLongJobMsg extends APIQueryMessage {
    public static List<String> __example__() {
        return Arrays.asList();
    }
}

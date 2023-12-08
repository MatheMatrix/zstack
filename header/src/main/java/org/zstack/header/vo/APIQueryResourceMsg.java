package org.zstack.header.vo;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.List;

import static java.util.Arrays.asList;

/**
 * author:kaicai.hu
 * Date:2023/12/8
 */

@AutoQuery(replyClass = APIQueryResourceReply.class, inventoryClass = ResourceInventory.class)
@RestRequest(
        path = "/resources",
        optionalPaths = {"/resources/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryResourceReply.class
)
public class APIQueryResourceMsg extends APIQueryMessage {

    public static List<String> __example__() {
        return asList();
    }

}

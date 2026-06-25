package org.zstack.header.resource;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.AccountConstant;
import org.zstack.header.identity.Action;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

@AutoQuery(replyClass = APIQueryResourceSourceRefReply.class, inventoryClass = ResourceSourceRefInventory.class)
@Action(category = AccountConstant.ACTION_CATEGORY, names = {"read"}, adminOnly = true)
@RestRequest(
        path = "/resources/source-refs",
        method = HttpMethod.GET,
        responseClass = APIQueryResourceSourceRefReply.class
)
public class APIQueryResourceSourceRefMsg extends APIQueryMessage {
}

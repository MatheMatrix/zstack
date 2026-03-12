package org.zstack.identity.imports.api;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;
import org.zstack.identity.imports.entity.AccountThirdPartyAccountSourceRefInventory;

import java.util.List;

import static java.util.Arrays.asList;
import org.zstack.header.vm.MetadataImpact;

@AutoQuery(replyClass = APIQueryThirdPartyAccountSourceBindingReply.class, inventoryClass = AccountThirdPartyAccountSourceRefInventory.class)
@RestRequest(
        path = "/account-import/bindings",
        method = HttpMethod.GET,
        responseClass = APIQueryThirdPartyAccountSourceBindingReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryThirdPartyAccountSourceBindingMsg extends APIQueryMessage {

    public static List<String> __example__() {
        return asList("accountUuid=" + uuid());
    }

}

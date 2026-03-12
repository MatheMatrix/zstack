package org.zstack.ldap.api;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;
import org.zstack.ldap.entity.LdapServerInventory;

import java.util.List;

import static java.util.Arrays.asList;
import org.zstack.header.vm.MetadataImpact;

@AutoQuery(replyClass = APIQueryLdapServerReply.class, inventoryClass = LdapServerInventory.class)
@RestRequest(
        path = "/ldap/servers",
        optionalPaths = {"/ldap/servers/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryLdapServerReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryLdapServerMsg extends APIQueryMessage {

    public static List<String> __example__() {
        return asList("name=ldap server");
    }

}

package org.zstack.directory;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.List;

import static java.util.Arrays.asList;
import org.zstack.header.vm.MetadataImpact;

/**
 * @author shenjin
 * @date 2023/1/11 11:25
 */
@AutoQuery(replyClass = APIQueryDirectoryReply.class, inventoryClass = DirectoryInventory.class)
@RestRequest(
        path = "/directories",
        optionalPaths = {"/directories/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryDirectoryReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryDirectoryMsg extends APIQueryMessage {

    public static List<String> __example__() {
        return asList("uuid=" + uuid());
    }
}

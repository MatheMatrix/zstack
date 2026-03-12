package org.zstack.storage.ceph.backup;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.storage.backup.APIQueryBackupStorageReply;

import java.util.Collections;
import java.util.List;
import org.zstack.header.vm.MetadataImpact;

/**
 * Created by frank on 8/6/2015.
 */
@AutoQuery(replyClass = APIQueryBackupStorageReply.class, inventoryClass = CephBackupStorageInventory.class)
@RestRequest(
        path = "/backup-storage/ceph",
        optionalPaths = {"/backup-storage/ceph/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryBackupStorageReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryCephBackupStorageMsg extends APIQueryMessage {

    public static List<String> __example__() {
        return Collections.singletonList("uuid=" + uuid());
    }

}

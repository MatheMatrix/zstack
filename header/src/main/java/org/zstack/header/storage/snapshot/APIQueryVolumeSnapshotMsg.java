package org.zstack.header.storage.snapshot;

import org.springframework.http.HttpMethod;
import org.zstack.header.query.APIQueryMessage;
import org.zstack.header.query.AutoQuery;
import org.zstack.header.rest.RestRequest;

import java.util.Collections;
import java.util.List;
import org.zstack.header.vm.MetadataImpact;

/**
 */
@AutoQuery(replyClass = APIQueryVolumeSnapshotReply.class, inventoryClass = VolumeSnapshotInventory.class)
@RestRequest(
        path = "/volume-snapshots",
        optionalPaths = {"/volume-snapshots/{uuid}"},
        method = HttpMethod.GET,
        responseClass = APIQueryVolumeSnapshotReply.class
)
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIQueryVolumeSnapshotMsg extends APIQueryMessage {
 
    public static List<String> __example__() {
        return Collections.singletonList("uuid=" + uuid());
    }

}

package org.zstack.header.image;

import org.springframework.http.HttpMethod;
import org.zstack.header.message.APICreateMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;

import java.util.List;

//@RestRequest(
//        path = "/images/groups/from/snapshots/",
//        method = HttpMethod.POST,
//        responseClass = APICreateImageGroupFromSnapshotEvent.class,
//        parameterName = "params"
//)
public class APICreateImageGroupFromSnapshotMsg extends APICreateMessage {
//    @APIParam
    String rootSnapshotUuid;
//    @APIParam
    List<String> dataSnapshotUuids;

}

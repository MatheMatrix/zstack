package org.zstack.header.storage.snapshot.group;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

import java.util.Collections;
import java.util.List;

@RestResponse(allTo = "results")
public class APIBatchDeleteVolumeSnapshotGroupEvent extends APIEvent {
    // 快照组，快照快照组里面每个快照
    private List<DeleteSnapshotGroupResult> results;

    public APIBatchDeleteVolumeSnapshotGroupEvent(String apiId) {
        super(apiId);
    }

    public APIBatchDeleteVolumeSnapshotGroupEvent() {
        super();
    }

    public List<DeleteSnapshotGroupResult> getResults() {
        return results;
    }

    public void setResults(List<DeleteSnapshotGroupResult> results) {
        this.results = results;
    }

    public static APIBatchDeleteVolumeSnapshotGroupEvent __example__() {
        APIBatchDeleteVolumeSnapshotGroupEvent event = new APIBatchDeleteVolumeSnapshotGroupEvent(uuid());
        DeleteSnapshotGroupResult result = new DeleteSnapshotGroupResult();
        result.setError(new ErrorCode("SYS.1001", "internal error"));
        event.setResults(Collections.singletonList(result));
        return event;
    }
}

package org.zstack.header.storage.snapshot.group;

import org.zstack.header.message.MessageReply;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class BatchDeleteVolumeSnapshotGroupInnerReply extends MessageReply {
    private List<DeleteSnapshotGroupResult> results = Collections.synchronizedList(new ArrayList<>());

    public List<DeleteSnapshotGroupResult> getResults() {
        return results;
    }

    public void setResults(List<DeleteSnapshotGroupResult> results) {
        this.results = results;
    }

    public void addResult(DeleteSnapshotGroupResult result) {
        results.add(result);
    }
}

package org.zstack.header.vm;

import org.zstack.header.message.MessageReply;

import java.util.Map;

/**
 * {@link BatchCheckMetadataStatusMsg} 的回复。
 *
 * @see MetadataStatusResult
 */
public class BatchCheckMetadataStatusReply extends MessageReply {

    /**
     * key = vmUuid, value = 该 VM 的元数据状态结果。
     */
    private Map<String, MetadataStatusResult> results;

    public Map<String, MetadataStatusResult> getResults() {
        return results;
    }

    public void setResults(Map<String, MetadataStatusResult> results) {
        this.results = results;
    }
}

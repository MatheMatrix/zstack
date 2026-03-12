package org.zstack.header.search;

import org.zstack.header.message.APIMessage;
import org.zstack.header.vm.MetadataImpact;

@MetadataImpact(MetadataImpact.Impact.NONE)

public class APIDeleteSearchIndexMsg extends APIMessage {
    private String indexName;

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }
 
    public static APIDeleteSearchIndexMsg __example__() {
        APIDeleteSearchIndexMsg msg = new APIDeleteSearchIndexMsg();


        return msg;
    }

}

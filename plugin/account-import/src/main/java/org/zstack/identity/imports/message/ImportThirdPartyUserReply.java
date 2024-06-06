package org.zstack.identity.imports.message;

import org.zstack.header.message.MessageReply;
import org.zstack.identity.imports.entity.ImportAccountResult;

import java.util.List;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public class ImportThirdPartyUserReply extends MessageReply {
    private List<ImportAccountResult> results;

    public List<ImportAccountResult> getResults() {
        return results;
    }

    public void setResults(List<ImportAccountResult> results) {
        this.results = results;
    }
}

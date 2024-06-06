package org.zstack.identity.imports.message;

import org.zstack.header.message.MessageReply;
import org.zstack.identity.imports.entity.AccountImportChunk;

import java.util.List;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public class ImportThirdPartyAccountReply extends MessageReply {
    private List<AccountImportChunk> results;

    public List<AccountImportChunk> getResults() {
        return results;
    }

    public void setResults(List<AccountImportChunk> results) {
        this.results = results;
    }
}

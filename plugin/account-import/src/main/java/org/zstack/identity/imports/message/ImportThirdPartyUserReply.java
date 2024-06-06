package org.zstack.identity.imports.message;

import org.zstack.header.message.MessageReply;
import org.zstack.identity.imports.entity.AccountImportContext;

import java.util.List;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public class ImportThirdPartyUserReply extends MessageReply {
    private List<AccountImportContext.Result> results;

    public List<AccountImportContext.Result> getResults() {
        return results;
    }

    public void setResults(List<AccountImportContext.Result> results) {
        this.results = results;
    }
}

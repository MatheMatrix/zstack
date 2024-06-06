package org.zstack.identity.imports.message;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.identity.imports.entity.ImportAccountBatch;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public class ImportThirdPartyAccountMsg extends NeedReplyMessage implements ImportSourceMessage {
    private ImportAccountBatch batch;

    public ImportAccountBatch getBatch() {
        return batch;
    }

    public void setBatch(ImportAccountBatch batch) {
        this.batch = batch;
    }

    @Override
    public String getSourceUuid() {
        return batch.sourceUuid;
    }
}

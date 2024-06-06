package org.zstack.identity.imports.message;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.identity.imports.entity.SyncNewcomersStrategy;
import org.zstack.identity.imports.entity.SyncRetireesStrategy;

/**
 * AccountThirdPartySyncMsg will call {@link ImportThirdPartyAccountMsg}
 *
 * Created by Wenhao.Zhang on 2024/06/05
 */
public class SyncThirdPartyAccountMsg extends NeedReplyMessage implements ImportSourceMessage {
    private String sourceUuid;
    private SyncNewcomersStrategy forNewcomers;
    private SyncRetireesStrategy forRetirees;

    @Override
    public String getSourceUuid() {
        return sourceUuid;
    }

    public void setSourceUuid(String sourceUuid) {
        this.sourceUuid = sourceUuid;
    }

    public SyncNewcomersStrategy getForNewcomers() {
        return forNewcomers;
    }

    public void setForNewcomers(SyncNewcomersStrategy forNewcomers) {
        this.forNewcomers = forNewcomers;
    }

    public SyncRetireesStrategy getForRetirees() {
        return forRetirees;
    }

    public void setForRetirees(SyncRetireesStrategy forRetirees) {
        this.forRetirees = forRetirees;
    }
}

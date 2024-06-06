package org.zstack.identity.imports.entity;

import org.zstack.identity.imports.message.SyncThirdPartyAccountMsg;

/**
 * Created by Wenhao.Zhang on 2024/06/06
 */
public class SyncAccountSpec {
    public String sourceUuid;
    public String sourceType;
    public SyncNewcomersStrategy forNewcomers;
    public SyncRetireesStrategy forRetirees;

    public SyncAccountSpec withAccountImportSource(String sourceUuid, String sourceType) {
        this.sourceUuid = sourceUuid;
        this.sourceType = sourceType;
        return this;
    }

    public SyncAccountSpec withAccountThirdPartySyncMsg(SyncThirdPartyAccountMsg message) {
        this.forNewcomers = message.getForNewcomers();
        this.forRetirees = message.getForRetirees();
        return this;
    }

    public SyncAccountSpec withNewcomersStrategy(SyncNewcomersStrategy strategy) {
        this.forNewcomers = strategy;
        return this;
    }

    public SyncAccountSpec withRetireesStrategy(SyncRetireesStrategy strategy) {
        this.forRetirees = strategy;
        return this;
    }
}

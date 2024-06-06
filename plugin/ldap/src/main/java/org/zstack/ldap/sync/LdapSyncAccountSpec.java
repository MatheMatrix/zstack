package org.zstack.ldap.sync;

import org.zstack.identity.imports.entity.SyncAccountSpec;
import org.zstack.identity.imports.entity.SyncNewcomersStrategy;
import org.zstack.identity.imports.entity.SyncRetireesStrategy;
import org.zstack.identity.imports.message.SyncThirdPartyAccountMsg;
import org.zstack.ldap.entity.LdapFilterRuleVO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by Wenhao.Zhang on 2024/06/06
 */
public class LdapSyncAccountSpec extends SyncAccountSpec {
    public final List<LdapFilterRuleVO> rules = new ArrayList<>();

    public LdapSyncAccountSpec() {}

    public LdapSyncAccountSpec(SyncAccountSpec from) {
        this.sourceUuid = from.sourceUuid;
        this.sourceType = from.sourceType;
        this.forNewcomers = from.forNewcomers;
        this.forRetirees = from.forRetirees;
    }

    @Override
    public LdapSyncAccountSpec withAccountImportSource(String sourceUuid, String sourceType) {
        super.withAccountImportSource(sourceUuid, sourceType);
        return this;
    }

    @Override
    public LdapSyncAccountSpec withAccountThirdPartySyncMsg(SyncThirdPartyAccountMsg message) {
        super.withAccountThirdPartySyncMsg(message);
        return this;
    }

    @Override
    public LdapSyncAccountSpec withNewcomersStrategy(SyncNewcomersStrategy strategy) {
        super.withNewcomersStrategy(strategy);
        return this;
    }

    @Override
    public LdapSyncAccountSpec withRetireesStrategy(SyncRetireesStrategy strategy) {
        super.withRetireesStrategy(strategy);
        return this;
    }

    public LdapSyncAccountSpec withFilterRules(Collection<LdapFilterRuleVO> rules) {
        this.rules.addAll(rules);
        return this;
    }

    public LdapSyncBackend createBackend() {
        return new LdapSyncBackend(this);
    }
}

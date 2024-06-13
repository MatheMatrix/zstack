package org.zstack.ldap.header;

import org.zstack.identity.imports.header.SyncTaskSpec;
import org.zstack.ldap.entity.LdapFilterRuleVO;

import java.util.ArrayList;
import java.util.List;

public class LdapSyncTaskSpec extends SyncTaskSpec {
    private List<LdapFilterRuleVO> rules = new ArrayList<>();
    private String defaultFilter;
    private int maxAccountCount;
    private String usernameProperty;

    public LdapSyncTaskSpec() {
    }

    public LdapSyncTaskSpec(SyncTaskSpec spec) {
        this.setSourceUuid(spec.getSourceUuid());
        this.setSourceType(spec.getSourceType());
        this.setCreateAccountStrategy(spec.getCreateAccountStrategy());
        this.setDeleteAccountStrategy(spec.getDeleteAccountStrategy());
    }

    public List<LdapFilterRuleVO> getRules() {
        return rules;
    }

    public void setRules(List<LdapFilterRuleVO> rules) {
        this.rules = rules;
    }

    public String getDefaultFilter() {
        return defaultFilter;
    }

    public void setDefaultFilter(String defaultFilter) {
        this.defaultFilter = defaultFilter;
    }

    public int getMaxAccountCount() {
        return maxAccountCount;
    }

    public void setMaxAccountCount(int maxAccountCount) {
        this.maxAccountCount = maxAccountCount;
    }

    public String getUsernameProperty() {
        return usernameProperty;
    }

    public void setUsernameProperty(String usernameProperty) {
        this.usernameProperty = usernameProperty;
    }
}

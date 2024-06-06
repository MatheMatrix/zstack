package org.zstack.identity.imports.entity;

import org.zstack.header.identity.AccountType;
import org.zstack.header.log.NoLogging;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public class ImportAccountSpec {
    private String sourceUuid;
    private String sourceType;
    public List<AccountSpec> accountList = new ArrayList<>();

    /**
     * {@link SyncTaskSpec} -> {@link ImportAccountSpec} and {@link ImportAccountSpec.AccountSpec} ->
     * {@link ImportAccountResult}
     */
    public static class SyncTaskSpec {
        public String sourceUuid;
        public String sourceType;
        public SyncCreatedAccountStrategy createAccountStrategy;
        public SyncDeletedAccountStrategy deleteAccountStrategy;

        public SyncTaskSpec withAccountSource(String sourceUuid, String sourceType) {
            this.sourceUuid = sourceUuid;
            this.sourceType = sourceType;
            return this;
        }

        public SyncTaskSpec withCreateAccountStrategy(SyncCreatedAccountStrategy strategy) {
            this.createAccountStrategy = strategy;
            return this;
        }

        public SyncTaskSpec withDeleteAccountStrategy(SyncDeletedAccountStrategy strategy) {
            this.deleteAccountStrategy = strategy;
            return this;
        }
    }

    public static class AccountSpec {
        private String accountUuid;
        private boolean createIfNotExist = true;

        private String credentials;
        private AccountType accountType;
        private String username;
        @NoLogging
        private String password;
        private List<String> systemTags = new ArrayList<>();

        public String getAccountUuid() {
            return accountUuid;
        }

        public void setAccountUuid(String accountUuid) {
            this.accountUuid = accountUuid;
        }

        public boolean isCreateIfNotExist() {
            return createIfNotExist;
        }

        public void setCreateIfNotExist(boolean createIfNotExist) {
            this.createIfNotExist = createIfNotExist;
        }

        public String getCredentials() {
            return credentials;
        }

        public void setCredentials(String credentials) {
            this.credentials = credentials;
        }

        public AccountType getAccountType() {
            return accountType;
        }

        public void setAccountType(AccountType accountType) {
            this.accountType = accountType;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public List<String> getSystemTags() {
            return systemTags;
        }

        public void setSystemTags(List<String> systemTags) {
            this.systemTags = systemTags;
        }
    }

    public String getSourceUuid() {
        return sourceUuid;
    }

    public void setSourceUuid(String sourceUuid) {
        this.sourceUuid = sourceUuid;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public List<AccountSpec> getAccountList() {
        return accountList;
    }

    public void setAccountList(List<AccountSpec> accountList) {
        this.accountList = accountList;
    }
}

package org.zstack.identity.imports.entity;

import org.zstack.header.identity.AccountType;
import org.zstack.header.log.NoLogging;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
public class ImportAccountBatch {
    public String sourceUuid;
    public String sourceType;
    public List<AccountSpec> accountList = new ArrayList<>();

    public static class AccountSpec {
        public String accountUuid;
        public boolean createIfNotExist = true;

        public String keyFromSource;
        public AccountType accountType;
        public String username;
        @NoLogging
        public String password;
        public List<String> systemTags = new ArrayList<>();
    }
}

package org.zstack.ldap.compute;

import org.zstack.identity.imports.header.SyncTaskResult;
import org.zstack.ldap.LdapConstant;

public class LdapSyncProgress {
    public String ldapServerUuid;
    public String stage;
    public int completeCount;
    public int totalCount;

    public static class ImportStage {
        public int total;
        public int success;
        public int fail;
    }
    public ImportStage importStage = new ImportStage();

    public static class CleanStage {
        public int total;
        public int success;
        public int skip;
        public int fail;
    }
    public CleanStage cleanStage = new CleanStage();

    public LdapSyncProgress withLdapServer(String ldapServerUuid) {
        this.ldapServerUuid = ldapServerUuid;
        return this;
    }

    public LdapSyncProgress withStage(String stage) {
        this.stage = stage;
        return this;
    }

    public LdapSyncProgress withExistingRecordCount(long existCount) {
        this.cleanStage.total = (int) existCount;
        this.totalCount = importStage.total + cleanStage.total;
        return this;
    }

    public LdapSyncProgress withSearchRecordCount(int searchCount) {
        this.importStage.total = searchCount;
        this.totalCount = importStage.total + cleanStage.total;
        return this;
    }

    public synchronized LdapSyncProgress appendFailCountInImportStage(int failCount) {
        this.completeCount += failCount;
        this.importStage.fail += failCount;
        return this;
    }

    public synchronized LdapSyncProgress appendSuccessCountInImportStage(int successCount) {
        this.completeCount += successCount;
        this.importStage.success += successCount;
        return this;
    }

    public synchronized LdapSyncProgress appendFailCountInCleanStage(int failCount) {
        this.completeCount += failCount;
        this.cleanStage.fail += failCount;
        return this;
    }

    public synchronized LdapSyncProgress appendSuccessCountInCleanStage(int successCount) {
        this.completeCount += successCount;
        this.cleanStage.success += successCount;
        return this;
    }

    public synchronized LdapSyncProgress appendSkipCountInCleanStage(int skipCount) {
        this.completeCount += skipCount;
        this.cleanStage.skip += skipCount;
        return this;
    }

    public float progress() {
        return totalCount == 0 ? 0f : 100f * completeCount / totalCount;
    }

    public SyncTaskResult makeResult() {
        SyncTaskResult result = new SyncTaskResult();
        result.setSourceUuid(ldapServerUuid);
        result.setSourceType(LdapConstant.LOGIN_TYPE);

        result.getImportStage().setTotal(importStage.total);
        result.getImportStage().setSuccess(importStage.success);
        result.getImportStage().setFail(importStage.fail);

        result.getCleanStage().setTotal(cleanStage.total);
        result.getCleanStage().setSkip(cleanStage.skip);
        result.getCleanStage().setSuccess(cleanStage.success);
        result.getCleanStage().setFail(cleanStage.fail);

        return result;
    }
}

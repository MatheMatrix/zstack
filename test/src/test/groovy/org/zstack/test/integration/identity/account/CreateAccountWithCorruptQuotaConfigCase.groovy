package org.zstack.test.integration.identity.account

import org.zstack.compute.vm.VmQuotaGlobalConfig
import org.zstack.core.config.GlobalConfigVO
import org.zstack.core.config.GlobalConfigVO_
import org.zstack.core.db.Q
import org.zstack.core.db.SQL
import org.zstack.header.exception.CloudRuntimeException
import org.zstack.header.identity.AccountConstant
import org.zstack.header.identity.AccountType
import org.zstack.identity.Account
import org.zstack.test.integration.ZStackTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

/**
 * Regression test for ZSTAC-84863: account creation must surface a clear
 * CloudRuntimeException when a quota global config row is corrupt
 * (non-numeric value), instead of letting an uncaught NumberFormatException
 * propagate and roll back the transaction with no diagnostic context.
 *
 * Covers both code paths that build QuotaVOs from quota global configs:
 *   1. {@link Account#create(Account.AccountBuilder)} — IAM2 / test helper path.
 *   2. {@code APICreateAccountMsg} → {@code AccountManagerImpl.createAccount(CreateAccountMsg)}
 *      — the SDK / REST path users actually hit.
 */
class CreateAccountWithCorruptQuotaConfigCase extends SubCase {
    EnvSpec env

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(ZStackTest.springSpec)
    }

    @Override
    void environment() {
        env = env {}
    }

    @Override
    void test() {
        env.create {
            testCreateAccountFailsOnNonNumericQuota()
            testCreateAccountFailsOnEmptyQuotaValue()
            testCreateAccountApiFailsOnNonNumericQuota()
        }
    }

    /**
     * Inject a non-numeric value into a quota global config row by direct
     * SQL update (bypassing the validator), then assert Account.create
     * fails with a CloudRuntimeException whose message identifies the
     * offending category, config name and raw value.
     */
    void testCreateAccountFailsOnNonNumericQuota() {
        assertCorruptQuotaFailsAccountCreate("not-a-number", "corruptQuotaTest1")
    }

    /**
     * Long.parseLong("") throws NumberFormatException too — make sure the
     * empty-string flavour of corruption is also turned into a clear error,
     * with the same diagnostic tokens as the non-numeric case.
     */
    void testCreateAccountFailsOnEmptyQuotaValue() {
        assertCorruptQuotaFailsAccountCreate("", "corruptQuotaTest2")
    }

    /**
     * API path coverage: drives APICreateAccountMsg through the SDK so the
     * failure surfaces from {@code AccountManagerImpl.createAccount(CreateAccountMsg)}.
     * This is the path users actually hit — verify the same four diagnostic
     * tokens (category / config name / raw value / account name) reach the
     * SDK error response, not just the static {@code Account.create} helper.
     */
    void testCreateAccountApiFailsOnNonNumericQuota() {
        String quotaName = VmQuotaGlobalConfig.VM_TOTAL_NUM.name
        String corruptValue = "not-a-number-api"
        String accountName = "corruptQuotaApiTest"
        String original = readQuotaValue(quotaName)
        try {
            writeQuotaValueRaw(quotaName, corruptValue)

            // ApiHelper.createAccount asserts res.error == null, so an API
            // failure surfaces as AssertionError carrying the JSON-serialized
            // ErrorCode (whose description embeds our CloudRuntimeException msg).
            AssertionError caught = null
            try {
                createAccount {
                    delegate.name = accountName
                    delegate.password = "password"
                }
            } catch (AssertionError e) {
                caught = e
            }

            assert caught != null:
                    "expected API failure when quota config has invalid value[${corruptValue}]"
            assertDiagnosticTokensPresent(caught.message, quotaName, corruptValue, accountName)
        } finally {
            writeQuotaValueRaw(quotaName, original)
        }
    }

    /**
     * Drives the corruption→create→assert→restore lifecycle for one
     * corrupt value via the static {@code Account.create} helper. Asserts
     * the resulting CloudRuntimeException carries all four diagnostic
     * tokens (category, config name, raw value, account name) so failure
     * messages stay actionable for operators.
     */
    private void assertCorruptQuotaFailsAccountCreate(String corruptValue, String accountName) {
        String quotaName = VmQuotaGlobalConfig.VM_TOTAL_NUM.name
        String original = readQuotaValue(quotaName)
        try {
            writeQuotaValueRaw(quotaName, corruptValue)

            CloudRuntimeException caught = null
            try {
                Account.create(Account.AccountBuilder.New()
                        .name(accountName)
                        .type(AccountType.Normal)
                        .password("password"))
            } catch (CloudRuntimeException e) {
                caught = e
            }

            assert caught != null:
                    "expected CloudRuntimeException when quota config has invalid value[${corruptValue}]"
            assertDiagnosticTokensPresent(caught.message, quotaName, corruptValue, accountName)
        } finally {
            writeQuotaValueRaw(quotaName, original)
        }
    }

    /**
     * Shared assertion: every failure path must surface the same four
     * tokens so operators can pinpoint the offending row and account
     * without grepping logs. Kept here so both the static-helper path
     * and the API path stay in lockstep.
     */
    private static void assertDiagnosticTokensPresent(String msg,
                                                      String quotaName,
                                                      String corruptValue,
                                                      String accountName) {
        assert msg.contains(AccountConstant.QUOTA_GLOBAL_CONFIG_CATETORY):
                "error message should include quota category, got: ${msg}"
        assert msg.contains(quotaName):
                "error message should include config name, got: ${msg}"
        assert msg.contains(corruptValue):
                "error message should include raw value, got: ${msg}"
        assert msg.contains(accountName):
                "error message should include account name, got: ${msg}"
    }

    private static String readQuotaValue(String name) {
        return Q.New(GlobalConfigVO.class)
                .eq(GlobalConfigVO_.category, AccountConstant.QUOTA_GLOBAL_CONFIG_CATETORY)
                .eq(GlobalConfigVO_.name, name)
                .select(GlobalConfigVO_.value)
                .findValue()
    }

    private static void writeQuotaValueRaw(String name, String value) {
        SQL.New(GlobalConfigVO.class)
                .eq(GlobalConfigVO_.category, AccountConstant.QUOTA_GLOBAL_CONFIG_CATETORY)
                .eq(GlobalConfigVO_.name, name)
                .set(GlobalConfigVO_.value, value)
                .update()
    }
}

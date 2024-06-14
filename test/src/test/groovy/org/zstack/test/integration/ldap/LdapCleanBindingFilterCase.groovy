package org.zstack.test.integration.ldap

import org.junit.ClassRule
import org.zapodot.junit.ldap.EmbeddedLdapRule
import org.zapodot.junit.ldap.EmbeddedLdapRuleBuilder
import org.zstack.sdk.identity.ldap.entity.LdapFilterRuleInventory
import org.zstack.sdk.identity.ldap.entity.LdapServerInventory
import org.zstack.test.integration.ZStackTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

/**
 * @author shenjin
 * @date 2022/10/24 16:45
 */
class LdapCleanBindingFilterCase extends SubCase {
    EnvSpec env

    LdapServerInventory ldapServer

    public static String DOMAIN_DSN = "dc=example,dc=com"
    @ClassRule
    public static EmbeddedLdapRule embeddedLdapRule = EmbeddedLdapRuleBuilder.newInstance()
            .bindingToPort(1888)
            .usingDomainDsn(DOMAIN_DSN)
            .importingLdifs("users-import.ldif")
            .build()

    @Override
    void setup() {
        useSpring(ZStackTest.springSpec)
    }

    @Override
    void environment() {
        env = Env.localStorageOneVmEnv()
    }

    @Override
    void test() {
        env.create {
            prepare()
            testUpdateFiler()
        }
    }

    @Override
    void clean() {
        env.delete()
    }

    void prepare() {
        ldapServer = addLdapServer {
            name = "ldap0"
            description = "test-ldap0"
            base = DOMAIN_DSN
            url = "ldap://localhost:1888"
            username = ""
            password = ""
            encryption = "None"
        } as LdapServerInventory

        assert ldapServer.filterRules.isEmpty()
    }

    void testUpdateFiler(){
        String filter = "(age=3)"

        logger.info("Test 1: add ldap filter rule")
        addLdapFilterRule {
            delegate.ldapServerUuid = ldapServer.uuid
            delegate.rules = [filter]
            delegate.policy = "ACCEPT"
            delegate.target = "AddNew"
        }

        def ldapList = queryLdapServer {
            delegate.conditions = ["uuid=${ldapServer.uuid}"]
        } as List<LdapServerInventory>
        assert ldapList.size() == 1
        assert ldapList[0].filterRules.size() == 1

        def acceptRule = ldapList[0].filterRules[0] as LdapFilterRuleInventory
        assert acceptRule.policy == "ACCEPT"
        assert filter == acceptRule.rule

        addLdapFilterRule {
            delegate.ldapServerUuid = ldapServer.uuid
            delegate.rules = [filter]
            delegate.policy = "DENY"
            delegate.target = "AddNew"
        }

        ldapList = queryLdapServer {
            delegate.conditions = ["uuid=${ldapServer.uuid}"]
        } as List<LdapServerInventory>
        assert ldapList.size() == 1
        assert ldapList[0].filterRules.size() == 2

        assert ((ldapList[0].filterRules as List<LdapFilterRuleInventory>).count { it ->
            it.policy == "DENY" && it.rule == filter
        }) == 1
        assert ((ldapList[0].filterRules as List<LdapFilterRuleInventory>).count { it ->
            it.policy == "ACCEPT" && it.rule == filter && it.uuid == acceptRule.uuid
        }) == 1

        def denyRule = (ldapList[0].filterRules as List<LdapFilterRuleInventory>).find { it -> it.policy == "DENY" }

        logger.info("Test 2: update ldap filter rule")
        updateLdapFilterRule {
            delegate.uuid = acceptRule.uuid
            delegate.rule = "(age=6)"
        }

        ldapList = queryLdapServer {
            delegate.conditions = ["uuid=${ldapServer.uuid}"]
        } as List<LdapServerInventory>
        assert ldapList.size() == 1
        assert ldapList[0].filterRules.size() == 2
        assert ((ldapList[0].filterRules as List<LdapFilterRuleInventory>).count { it ->
            it.policy == "ACCEPT" && it.rule == "(age=6)" && it.uuid == acceptRule.uuid
        }) == 1

        logger.info("Test 3: remove ldap filter rule")
        removeLdapFilterRule {
            delegate.uuidList = [denyRule.uuid]
        }

        ldapList = queryLdapServer {
            delegate.conditions = ["uuid=${ldapServer.uuid}"]
        } as List<LdapServerInventory>
        assert ldapList.size() == 1
        assert ldapList[0].filterRules.size() == 1
        assert ((ldapList[0].filterRules as List<LdapFilterRuleInventory>).count { it -> it.uuid == acceptRule.uuid }) == 1
        assert ((ldapList[0].filterRules as List<LdapFilterRuleInventory>).count { it -> it.uuid == denyRule.uuid }) == 0
    }
}

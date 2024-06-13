package org.zstack.ldap.api

import org.zstack.ldap.api.APIAddLdapFilterRuleEvent

doc {
    title "AddLdapFilterRule"

    category "ldap"

    desc """添加LDAP用户同步过滤规则"""

    rest {
        request {
			url "POST /v1/ldap/filter"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIAddLdapFilterRuleMsg.class

            desc """"""
            
			params {

				column {
					name "ldapServerUuid"
					enclosedIn "params"
					desc "LDAP服务器的UUID"
					location "body"
					type "String"
					optional false
					since "zsv 4.3.0"
				}
				column {
					name "rules"
					enclosedIn "params"
					desc "LDAP用户同步过滤规则"
					location "body"
					type "List"
					optional false
					since "zsv 4.3.0"
				}
				column {
					name "policy"
					enclosedIn "params"
					desc "LDAP用户同步过滤对策，基于上面的规则是允许还是禁止"
					location "body"
					type "String"
					optional false
					since "zsv 4.3.0"
					values ("ACCEPT","DENY")
				}
				column {
					name "target"
					enclosedIn "params"
					desc "LDAP用户同步过滤启用目标，是添加新用户时启用该规则，还是删除失效用户时启用该规则"
					location "body"
					type "String"
					optional false
					since "zsv 4.3.0"
					values ("AddNew","DeleteInvalid")
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "zsv 4.3.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "zsv 4.3.0"
				}
			}
        }

        response {
            clz APIAddLdapFilterRuleEvent.class
        }
    }
}
package org.zstack.ldap.api

import org.zstack.ldap.api.APIUpdateLdapFilterRuleEvent

doc {
    title "UpdateLdapFilterRule"

    category "ldap"

    desc """更新LDAP用户同步过滤规则"""

    rest {
        request {
			url "PUT /v1/ldap/filter/{uuid}/actions"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIUpdateLdapFilterRuleMsg.class

            desc """"""
            
			params {

				column {
					name "uuid"
					enclosedIn "updateLdapFilterRule"
					desc "LDAP用户同步过滤规则的UUID"
					location "url"
					type "String"
					optional false
					since "zsv 4.3.0"
				}
				column {
					name "rule"
					enclosedIn "updateLdapFilterRule"
					desc "LDAP用户同步过滤规则"
					location "body"
					type "String"
					optional true
					since "zsv 4.3.0"
				}
				column {
					name "policy"
					enclosedIn "updateLdapFilterRule"
					desc "LDAP用户同步过滤对策，基于上面的规则是允许还是禁止"
					location "body"
					type "String"
					optional true
					since "zsv 4.3.0"
					values ("ACCEPT","DENY")
				}
				column {
					name "target"
					enclosedIn "updateLdapFilterRule"
					desc "LDAP用户同步过滤启用目标，是添加新用户时启用该规则，还是删除失效用户时启用该规则"
					location "body"
					type "String"
					optional true
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
            clz APIUpdateLdapFilterRuleEvent.class
        }
    }
}
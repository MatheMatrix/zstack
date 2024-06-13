package org.zstack.ldap.api

import org.zstack.ldap.api.APIRemoveLdapFilterRuleEvent

doc {
    title "RemoveLdapFilterRule"

    category "ldap"

    desc """删除LDAP用户同步过滤规则"""

    rest {
        request {
			url "DELETE /v1/ldap/filters"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIRemoveLdapFilterRuleMsg.class

            desc """"""
            
			params {

				column {
					name "uuidList"
					enclosedIn ""
					desc "需要删除的LDAP用户同步过滤规则的UUID列表"
					location "body"
					type "List"
					optional false
					since "zsv 4.3.0"
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
            clz APIRemoveLdapFilterRuleEvent.class
        }
    }
}
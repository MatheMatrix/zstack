package org.zstack.ldap.api

import org.zstack.ldap.api.APIGetLdapEntryReply

doc {
    title "GetLdapEntry"

    category "ldap"

    desc """查询LDAP/AD条目"""

    rest {
        request {
			url "GET /v1/ldap/entry"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIGetLdapEntryMsg.class

            desc """"""
            
			params {

				column {
					name "ldapFilter"
					enclosedIn ""
					desc "查询条件"
					location "query"
					type "String"
					optional false
					since "zsv 4.3.0"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "query"
					type "List"
					optional true
					since "zsv 4.3.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "query"
					type "List"
					optional true
					since "zsv 4.3.0"
				}
				column {
					name "limit"
					enclosedIn ""
					desc "最多返回的记录数，类似MySQL的limit"
					location "query"
					type "Integer"
					optional true
					since "zsv 4.3.0"
				}
				column {
					name "ldapServerUuid"
					enclosedIn ""
					desc ""
					location "query"
					type "String"
					optional true
					since "zsv 4.3.0"
				}
			}
        }

        response {
            clz APIGetLdapEntryReply.class
        }
    }
}
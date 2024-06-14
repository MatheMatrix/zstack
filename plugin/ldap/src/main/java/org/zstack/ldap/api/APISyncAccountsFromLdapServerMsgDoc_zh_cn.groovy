package org.zstack.ldap.api

import org.zstack.ldap.api.APISyncAccountsFromLdapServerEvent

doc {
    title "SyncAccountsFromLdapServer"

    category "ldap"

    desc """从LDAP服务器上同步用户"""

    rest {
        request {
			url "PUT /v1/ldap/servers/{uuid}/actions"

			header (Authorization: 'OAuth the-session-uuid')

            clz APISyncAccountsFromLdapServerMsg.class

            desc """"""
            
			params {

				column {
					name "uuid"
					enclosedIn "syncAccountsFromLdapServer"
					desc "LDAP服务器的UUID，唯一标示该资源"
					location "url"
					type "String"
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
            clz APISyncAccountsFromLdapServerEvent.class
        }
    }
}
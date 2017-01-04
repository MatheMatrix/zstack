package org.zstack.ldap



doc {
    title "在这里填写API标题"

    desc "在这里填写API描述"

    rest {
        request {
			url "POST /v1/ldap/bindings"


            header (OAuth: 'the-session-uuid')

            clz APICreateLdapBindingMsg.class

            desc ""
            
			params {

				column {
					name "ldapUid"
					enclosedIn ""
					desc ""
					location "body"
					type "String"
					optional false
					since "0.6"
					
				}
				column {
					name "accountUuid"
					enclosedIn ""
					desc "账户UUID"
					location "body"
					type "String"
					optional false
					since "0.6"
					
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc ""
					location "body"
					type "List"
					optional true
					since "0.6"
					
				}
				column {
					name "userTags"
					enclosedIn ""
					desc ""
					location "body"
					type "List"
					optional true
					since "0.6"
					
				}
			}
        }

        response {
            clz APICreateLdapBindingEvent.class
        }
    }
}
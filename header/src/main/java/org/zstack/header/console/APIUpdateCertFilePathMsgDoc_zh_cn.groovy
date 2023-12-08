package org.zstack.header.console

import org.zstack.header.console.APIUpdateCertFilePathEvent

doc {
    title "UpdateCertFilePath"

    category "console"

    desc """更新证书(https)路径"""

    rest {
        request {
			url "PUT /v1/consoles/certfilepath/actions"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIUpdateCertFilePathMsg.class

            desc """更新证书(https)路径"""
            
			params {

				column {
					name "certFilePath"
					enclosedIn "updateCertFilePath"
					desc "证书路径"
					location "body"
					type "String"
					optional false
					since "4.8.0"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "4.8.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "4.8.0"
				}
			}
        }

        response {
            clz APIUpdateCertFilePathEvent.class
        }
    }
}
package org.zstack.sdnController.header

import org.zstack.sdnController.header.APIReconnectSdnControllerEvent

doc {
    title "ReconnectSdnController"

    category "SdnController"

    desc """在这里填写API描述"""

    rest {
        request {
			url "PUT /v1/sdn-controllers/{sdnControllerUuid}/actions"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIReconnectSdnControllerMsg.class

            desc """"""
            
			params {

				column {
					name "sdnControllerUuid"
					enclosedIn "reconnectSdnController"
					desc ""
					location "url"
					type "String"
					optional false
					since "5.3.0"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "5.3.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "5.3.0"
				}
			}
        }

        response {
            clz APIReconnectSdnControllerEvent.class
        }
    }
}
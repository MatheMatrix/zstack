package org.zstack.header.host

import org.zstack.header.host.APIPowerOnHostEvent

doc {
    title "PowerOnHost"

    category "host"

    desc """物理机开机"""

    rest {
        request {
			url "PUT /v1/hosts/power/{uuid}/actions"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIPowerOnHostMsg.class

            desc """一台物理机开机"""
            
			params {

				column {
					name "uuid"
					enclosedIn "powerOnHost"
					desc "资源的UUID，唯一标示该资源"
					location "url"
					type "String"
					optional false
					since "4.7.0"
				}
				column {
					name "returnEarly"
					enclosedIn "powerOnHost"
					desc "是否提前返回"
					location "body"
					type "boolean"
					optional true
					since "4.7.0"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "4.7.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "4.7.0"
				}
			}
        }

        response {
            clz APIPowerOnHostEvent.class
        }
    }
}
package org.zstack.header.host

import org.zstack.header.host.APIGetHostTaskReply

doc {
    title "GetHostTask"

    category "host"

    desc """获取物理机上的任务信息"""

    rest {
        request {
			url "GET /v1/hosts/task-details"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIGetHostTaskMsg.class

            desc """"""
            
			params {

				column {
					name "hostUuids"
					enclosedIn ""
					desc "物理机UUIDs"
					location "query"
					type "List"
					optional false
					since "3.6.0"
					
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc ""
					location "query"
					type "List"
					optional true
					since "3.6.0"
					
				}
				column {
					name "userTags"
					enclosedIn ""
					desc ""
					location "query"
					type "List"
					optional true
					since "3.6.0"
					
				}
				column {
					name "syncSignatures"
					enclosedIn ""
					desc ""
					location "query"
					type "List"
					optional true
					since "4.6.0"
					
				}
			}
        }

        response {
            clz APIGetHostTaskReply.class
        }
    }
}
package org.zstack.header.network.l3

import org.zstack.header.network.l3.APIDeleteReservedIpRangeEvent

doc {
    title "DeleteReservedIpRange"

    category "network.l3"

    desc """在这里填写API描述"""

    rest {
        request {
			url "DELETE /v1/l3-networks/reserved-ip-ranges/{uuid}"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIDeleteReservedIpRangeMsg.class

            desc """"""
            
			params {

				column {
					name "uuid"
					enclosedIn ""
					desc "资源的UUID，唯一标示该资源"
					location "url"
					type "String"
					optional false
					since "5.1.0"
				}
				column {
					name "deleteMode"
					enclosedIn ""
					desc "删除模式(Permissive / Enforcing，Permissive)"
					location "body"
					type "String"
					optional true
					since "5.1.0"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "5.1.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "5.1.0"
				}
			}
        }

        response {
            clz APIDeleteReservedIpRangeEvent.class
        }
    }
}
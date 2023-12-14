package org.zstack.header.vm

import org.zstack.header.vm.APITakeVmProcessIdentifierCreateTimeEvent

doc {
    title "TakeVmProcessIdentifierCreateTime"

    category "vmInstance"

    desc """获取VM进程创建时间"""

    rest {
        request {
			url "GET /v1/vm-instances/{uuid}/process/identifier/createTime"

			header (Authorization: 'OAuth the-session-uuid')

            clz APITakeVmProcessIdentifierCreateTimeMsg.class

            desc """"""
            
			params {

				column {
					name "uuid"
					enclosedIn ""
					desc "虚拟机UUID"
					location "url"
					type "String"
					optional false
					since "4.1.0"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "query"
					type "List"
					optional true
					since "4.1.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "query"
					type "List"
					optional true
					since "4.1.0"
				}
			}
        }

        response {
            clz APITakeVmProcessIdentifierCreateTimeEvent.class
        }
    }
}
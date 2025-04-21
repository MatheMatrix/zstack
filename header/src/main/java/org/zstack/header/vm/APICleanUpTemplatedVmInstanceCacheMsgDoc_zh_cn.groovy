package org.zstack.header.vm

import org.zstack.header.vm.APICleanUpTemplatedVmInstanceCacheEvent

doc {
    title "CleanUpTemplatedVmInstanceCache"

    category "vmInstance"

    desc """清理模板缓存"""

    rest {
        request {
			url "DELETE /vm-instances/{uuid}/cache/actions"

			header (Authorization: 'OAuth the-session-uuid')

            clz APICleanUpTemplatedVmInstanceCacheMsg.class

            desc """"""
            
			params {

				column {
					name "uuid"
					enclosedIn ""
					desc "模板UUID"
					location "url"
					type "String"
					optional false
					since "4.10.10"

				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "4.10.10"

				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "4.10.10"

				}
			}
        }

        response {
            clz APIDeleteTemplatedVmInstanceEvent.class
        }
    }
}
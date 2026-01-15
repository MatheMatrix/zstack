package org.zstack.header.storage.primary

import org.zstack.header.storage.primary.APITakeoverPrimaryStorageEvent

doc {
	title "TakeoverPrimaryStorage"

	category "storage.primary"

	desc """接管主存储"""

	rest {
		request {
			url "PUT /v1/primary-storage/{uuid}/takeover"

			header (Authorization: 'OAuth the-session-uuid')

			clz APITakeoverPrimaryStorageMsg.class

			desc """"""

			params {

				column {
					name "uuid"
					enclosedIn "takeoverPrimaryStorage"
					desc "主存储的UUID"
					location "url"
					type "String"
					optional false
					since "5.0.0"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "5.0.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "5.0.0"
				}
			}
		}

		response {
			clz APITakeoverPrimaryStorageEvent.class
		}
	}
}
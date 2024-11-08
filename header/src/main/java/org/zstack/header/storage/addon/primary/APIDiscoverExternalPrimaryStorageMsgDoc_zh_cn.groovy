package org.zstack.header.storage.addon.primary

import org.zstack.header.storage.addon.primary.APIDiscoverExternalPrimaryStorageEvent

doc {
    title "DiscoverExternalPrimaryStorage"

    category "storage.primary"

    desc """发现外部存储"""

    rest {
        request {
			url "POST /v1/primary-storage/addon/discover"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIDiscoverExternalPrimaryStorageMsg.class

            desc """"""
            
			params {

				column {
					name "url"
					enclosedIn "params"
					desc ""
					location "body"
					type "String"
					optional false
					since "5.0.0"
				}
				column {
					name "identity"
					enclosedIn "params"
					desc "存储标识"
					location "body"
					type "String"
					optional true
					since "5.0.0"
				}
				column {
					name "config"
					enclosedIn "params"
					desc "配置"
					location "body"
					type "String"
					optional true
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
            clz APIDiscoverExternalPrimaryStorageEvent.class
        }
    }
}
package org.zstack.header.storage.addon.primary

doc {
    title "UpdateExternalPrimaryStorage"

    category "storage.primary"

    desc """更新外部存储"""

    rest {
        request {
			url "PUT /v1/primary-storage/addon/{uuid}/actions"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIUpdateExternalPrimaryStorageMsg.class

            desc """"""
            
			params {

				column {
					name "config"
					enclosedIn "updateExternalPrimaryStorage"
					desc "配置"
					location "body"
					type "String"
					optional true
					since "4.7.11"

				}
				column {
					name "defaultProtocol"
					enclosedIn "updateExternalPrimaryStorage"
					desc "默认协议"
					location "body"
					type "String"
					optional true
					since "4.7.11"

					values ("VHost","Scsi","Nvme","Curve","file")
				}
				column {
					name "uuid"
					enclosedIn "updateExternalPrimaryStorage"
					desc "资源的UUID，唯一标示该资源"
					location "url"
					type "String"
					optional false
					since "4.7.11"

				}
				column {
					name "name"
					enclosedIn "updateExternalPrimaryStorage"
					desc "资源名称"
					location "body"
					type "String"
					optional true
					since "4.7.11"

				}
				column {
					name "description"
					enclosedIn "updateExternalPrimaryStorage"
					desc "资源的详细描述"
					location "body"
					type "String"
					optional true
					since "4.7.11"

				}
				column {
					name "url"
					enclosedIn "updateExternalPrimaryStorage"
					desc ""
					location "body"
					type "String"
					optional true
					since "4.7.11"

				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "4.7.11"

				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "4.7.11"

				}
			}
        }

        response {
            clz APIUpdateExternalPrimaryStorageEvent.class
        }
    }
}
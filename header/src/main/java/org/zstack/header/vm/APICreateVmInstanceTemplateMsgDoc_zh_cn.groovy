package org.zstack.header.vm

import org.zstack.header.vm.APICreateVmInstanceTemplateEvent

doc {
    title "CreateVmInstanceTemplate"

    category "vmInstance"

    desc """在这里填写API描述"""

    rest {
        request {
			url "POST /v1/vm-instances/VmInstanceTemplate"

			header (Authorization: 'OAuth the-session-uuid')

            clz APICreateVmInstanceTemplateMsg.class

            desc """"""
            
			params {

				column {
					name "vmInstanceUuid"
					enclosedIn "params"
					desc "云主机UUID"
					location "body"
					type "String"
					optional false
					since "4.2.0"

				}
				column {
					name "name"
					enclosedIn "params"
					desc "资源名称"
					location "body"
					type "String"
					optional true
					since "4.2.0"

				}
				column {
					name "clone"
					enclosedIn "params"
					desc ""
					location "body"
					type "boolean"
					optional true
					since "4.2.0"

				}
				column {
					name "resourceUuid"
					enclosedIn "params"
					desc "资源UUID"
					location "body"
					type "String"
					optional true
					since "4.2.0"

				}
				column {
					name "tagUuids"
					enclosedIn "params"
					desc "标签UUID列表"
					location "body"
					type "List"
					optional true
					since "4.2.0"

				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "4.2.0"

				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "4.2.0"

				}
			}
        }

        response {
            clz APICreateVmInstanceTemplateEvent.class
        }
    }
}
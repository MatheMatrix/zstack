package org.zstack.header.vm

import org.zstack.header.vm.APICreateVmInstanceFromVmTemplateEvent

doc {
    title "CreateVmInstanceFromVmTemplate"

    category "vmInstance"

    desc """在这里填写API描述"""

    rest {
        request {
			url "POST /v1/vm-instances/VmInstanceTemplate/{VmInstanceTemplateUuid}"

			header (Authorization: 'OAuth the-session-uuid')

            clz APICreateVmInstanceFromVmTemplateMsg.class

            desc """"""
            
			params {

				column {
					name "vmInstanceTemplateUuid"
					enclosedIn "params"
					desc ""
					location "body"
					type "String"
					optional false
					since "zsv 4.2.0"
				}
				column {
					name "hostUuid"
					enclosedIn "params"
					desc "物理机UUID"
					location "body"
					type "String"
					optional false
					since "zsv 4.2.0"
				}
				column {
					name "resourceUuid"
					enclosedIn "params"
					desc "资源UUID"
					location "body"
					type "String"
					optional true
					since "zsv 4.2.0"
				}
				column {
					name "tagUuids"
					enclosedIn "params"
					desc "标签UUID列表"
					location "body"
					type "List"
					optional true
					since "zsv 4.2.0"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "zsv 4.2.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "zsv 4.2.0"
				}
			}
        }

        response {
            clz APICreateVmInstanceFromVmTemplateEvent.class
        }
    }
}
package org.zstack.header.storage.primary

doc {
	title "RegisterVmInstance"

	category "storage.primary"

	desc """注册虚拟机"""

	rest {
		request {
			url "POST /v1/vm-instances/register"

			header (Authorization: 'OAuth the-session-uuid')

			clz APIRegisterVmInstanceMsg.class

			desc """"""

			params {

				column {
					name "primaryStorageUuid"
					enclosedIn "params"
					desc "主存储UUID"
					location "body"
					type "String"
					optional false
					since "4.10.0"
				}
				column {
					name "clusterUuid"
					enclosedIn "params"
					desc "集群UUID"
					location "body"
					type "String"
					optional false
					since "4.10.0"
				}
				column {
					name "hostUuid"
					enclosedIn "params"
					desc "物理机UUID"
					location "body"
					type "String"
					optional true
					since "4.10.0"
				}
				column {
					name "metadataPath"
					enclosedIn "params"
					desc ""
					location "body"
					type "String"
					optional false
					since "4.10.0"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "4.10.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "4.10.0"
				}
			}
		}

		response {
			clz APIRegisterVmInstanceEvent.class
		}
	}
}
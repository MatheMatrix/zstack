package org.zstack.header.identity.role.api

import org.zstack.header.errorcode.ErrorCode
import org.zstack.header.identity.role.RoleInventory

doc {

	title "创建角色的结果"

	field {
		name "success"
		desc "创建角色是否成功"
		type "boolean"
		since "4.10.0"
	}
	ref {
		name "error"
		path "org.zstack.header.identity.role.api.APICreateRoleEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.10.0"
		clz ErrorCode.class
	}
	ref {
		name "inventory"
		path "org.zstack.header.identity.role.api.APICreateRoleEvent.inventory"
		desc "创建的角色信息, 附带角色权限条目"
		type "RoleInventory"
		since "4.10.0"
		clz RoleInventory.class
	}
}

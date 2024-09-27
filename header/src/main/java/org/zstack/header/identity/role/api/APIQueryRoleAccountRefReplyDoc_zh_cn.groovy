package org.zstack.header.identity.role.api

import org.zstack.header.identity.role.RoleAccountRefInventory
import org.zstack.header.errorcode.ErrorCode

doc {

	title "查询角色账户关系结果"

	ref {
		name "inventories"
		path "org.zstack.header.identity.role.api.APIQueryRoleAccountRefReply.inventories"
		desc "null"
		type "List"
		since "4.10.0"
		clz RoleAccountRefInventory.class
	}
	field {
		name "success"
		desc "查询是否成功"
		type "boolean"
		since "4.10.0"
	}
	ref {
		name "error"
		path "org.zstack.header.identity.role.api.APIQueryRoleAccountRefReply.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.10.0"
		clz ErrorCode.class
	}
}

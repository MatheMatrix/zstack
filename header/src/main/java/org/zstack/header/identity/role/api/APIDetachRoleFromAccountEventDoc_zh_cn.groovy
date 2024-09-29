package org.zstack.header.identity.role.api

import org.zstack.header.errorcode.ErrorCode

doc {

	title "解绑角色和账户结果"

	field {
		name "success"
		desc "解绑是否成功"
		type "boolean"
		since "4.10.0"
	}
	ref {
		name "error"
		path "org.zstack.header.identity.role.api.APIDetachRoleFromAccountEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.10.0"
		clz ErrorCode.class
	}
}

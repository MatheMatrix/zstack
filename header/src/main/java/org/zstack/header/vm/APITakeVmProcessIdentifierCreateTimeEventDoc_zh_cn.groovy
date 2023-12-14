package org.zstack.header.vm

import org.zstack.header.errorcode.ErrorCode

doc {

	title "获取vm进程创建时间"

	field {
		name "success"
		desc ""
		type "boolean"
		since "4.1.0"
	}
	ref {
		name "error"
		path "org.zstack.header.vm.APITakeVmProcessIdentifierCreateTimeEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.1.0"
		clz ErrorCode.class
	}
	field {
		name "createTime"
		desc ""
		type "String"
		since "4.1.0"
	}
}

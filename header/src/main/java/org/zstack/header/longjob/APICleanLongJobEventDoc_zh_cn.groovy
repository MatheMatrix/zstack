package org.zstack.header.longjob

import org.zstack.header.errorcode.ErrorCode

doc {

	title "清理Long Job的结果"

	field {
		name "success"
		desc ""
		type "boolean"
		since "4.3.0"
	}
	ref {
		name "error"
		path "org.zstack.header.longjob.APICleanLongJobEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.3.0"
		clz ErrorCode.class
	}
}

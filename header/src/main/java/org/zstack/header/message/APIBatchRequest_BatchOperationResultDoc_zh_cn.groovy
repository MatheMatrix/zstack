package org.zstack.header.message

import org.zstack.header.errorcode.ErrorCode

doc {

	title "批量操作接口的结果"

	field {
		name "uuid"
		desc "资源的UUID，唯一标示该资源"
		type "String"
		since "5.2.1"
	}
	field {
		name "success"
		desc ""
		type "boolean"
		since "5.2.1"
	}
	ref {
		name "error"
		path "org.zstack.header.message.APIBatchRequest.BatchOperationResult.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "5.2.1"
		clz ErrorCode.class
	}
}

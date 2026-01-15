package org.zstack.header.storage.primary

import org.zstack.header.errorcode.ErrorCode

doc {

	title "检查存储一致性返回"

	field {
		name "consistent"
		desc "是否一致"
		type "boolean"
		since "5.0.0"
	}
	field {
		name "success"
		desc ""
		type "boolean"
		since "5.0.0"
	}
	ref {
		name "error"
		path "org.zstack.header.storage.primary.APICheckPrimaryStorageConsistencyEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null"
		type "ErrorCode"
		since "5.0.0"
		clz ErrorCode.class
	}
}

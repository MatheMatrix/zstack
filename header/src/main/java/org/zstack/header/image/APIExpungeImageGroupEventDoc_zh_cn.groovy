package org.zstack.header.image

import org.zstack.header.errorcode.ErrorCode

doc {

	title "在这里输入结构的名称"

	field {
		name "success"
		desc ""
		type "boolean"
		since "5.3.20"
	}
	ref {
		name "error"
		path "org.zstack.header.image.APIExpungeImageGroupEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "5.3.20"
		clz ErrorCode.class
	}
}

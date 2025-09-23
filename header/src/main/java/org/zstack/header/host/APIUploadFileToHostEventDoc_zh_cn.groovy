package org.zstack.header.host

import org.zstack.header.errorcode.ErrorCode

doc {

	title "上传文件到主机返回"

	field {
		name "success"
		desc ""
		type "boolean"
		since "4.10.20"
	}
	ref {
		name "error"
		path "org.zstack.header.host.APIUploadFileToHostEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null"
		type "ErrorCode"
		since "4.10.20"
		clz ErrorCode.class
	}
}

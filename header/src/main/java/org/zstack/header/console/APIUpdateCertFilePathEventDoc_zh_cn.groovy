package org.zstack.header.console

import org.zstack.header.errorcode.ErrorCode

doc {

	title "更新证书(https)路径"

	field {
		name "certFilePath"
		desc "证书路径"
		type "String"
		since "4.8.0"
	}
	field {
		name "success"
		desc ""
		type "boolean"
		since "4.8.0"
	}
	ref {
		name "error"
		path "org.zstack.header.console.APIUpdateCertFilePathEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.8.0"
		clz ErrorCode.class
	}
}
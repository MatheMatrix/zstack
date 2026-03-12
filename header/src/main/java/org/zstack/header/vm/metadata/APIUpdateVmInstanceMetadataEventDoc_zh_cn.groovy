package org.zstack.header.vm.metadata

import org.zstack.header.errorcode.ErrorCode

doc {

	title "更新云主机元数据返回"

	field {
		name "success"
		desc ""
		type "boolean"
		since "5.0.0"
	}
	ref {
		name "error"
		path "org.zstack.header.vm.metadata.APIUpdateVmMetadataEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null"
		type "ErrorCode"
		since "5.0.0"
		clz ErrorCode.class
	}
}

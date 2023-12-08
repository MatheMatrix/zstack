package org.zstack.header.vo

import org.zstack.header.vo.ResourceInventory
import org.zstack.header.errorcode.ErrorCode

doc {

	title "资源清单"

	ref {
		name "inventories"
		path "org.zstack.header.vo.APIQueryResourceReply.inventories"
		desc "null"
		type "List"
		since "4.8.0"
		clz ResourceInventory.class
	}
	field {
		name "success"
		desc ""
		type "boolean"
		since "4.8.0"
	}
	ref {
		name "error"
		path "org.zstack.header.vo.APIQueryResourceReply.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.8.0"
		clz ErrorCode.class
	}
}

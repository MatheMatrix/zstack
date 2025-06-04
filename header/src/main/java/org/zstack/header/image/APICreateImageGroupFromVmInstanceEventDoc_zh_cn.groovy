package org.zstack.header.image

import org.zstack.header.image.ImageGroupInventory
import org.zstack.header.errorcode.ErrorCode

doc {

	title "在这里输入结构的名称"

	ref {
		name "inventory"
		path "org.zstack.header.image.APICreateImageGroupFromVmInstanceEvent.inventory"
		desc "null"
		type "ImageGroupInventory"
		since "5.3.20"
		clz ImageGroupInventory.class
	}
	field {
		name "success"
		desc ""
		type "boolean"
		since "5.3.20"
	}
	ref {
		name "error"
		path "org.zstack.header.image.APICreateImageGroupFromVmInstanceEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "5.3.20"
		clz ErrorCode.class
	}
}

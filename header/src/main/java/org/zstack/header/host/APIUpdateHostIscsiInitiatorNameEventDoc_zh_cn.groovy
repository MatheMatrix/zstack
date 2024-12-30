package org.zstack.header.host

import org.zstack.header.errorcode.ErrorCode

doc {

	title "更新主机iscsiInitiatorName消息回复"

	ref {
		name "inventory"
		path "org.zstack.header.host.APIUpdateHostIscsiInitiatorNameEvent.inventory"
		desc "null"
		type "HostInventory"
		since "4.10.0"
		clz HostInventory.class
	}
	field {
		name "success"
		desc ""
		type "boolean"
		since "4.10.0"
	}
	ref {
		name "error"
		path "org.zstack.header.host.APIUpdateHostIscsiInitiatorNameEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.10.0"
		clz ErrorCode.class
	}
}

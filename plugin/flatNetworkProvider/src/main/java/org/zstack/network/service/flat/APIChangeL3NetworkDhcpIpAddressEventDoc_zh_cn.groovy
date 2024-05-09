package org.zstack.network.service.flat

import org.zstack.header.errorcode.ErrorCode

doc {

	title "在这里输入结构的名称"

	field {
		name "dhcpServerIp"
		desc ""
		type "String"
		since "5.1.0"
	}
	field {
		name "dhcpv6ServerIp"
		desc ""
		type "String"
		since "5.1.0"
	}
	field {
		name "success"
		desc ""
		type "boolean"
		since "5.1.0"
	}
	ref {
		name "error"
		path "org.zstack.network.service.flat.APIChangeL3NetworkDhcpIpAddressEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "5.1.0"
		clz ErrorCode.class
	}
}

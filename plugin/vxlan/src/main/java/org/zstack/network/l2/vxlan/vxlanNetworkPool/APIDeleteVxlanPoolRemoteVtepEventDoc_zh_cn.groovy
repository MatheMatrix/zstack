package org.zstack.network.l2.vxlan.vxlanNetworkPool

import org.zstack.header.errorcode.ErrorCode

doc {

	title "操作范围结果"

	field {
		name "success"
		desc ""
		type "boolean"
		since "4.7.11"
	}
	ref {
		name "error"
		path "org.zstack.network.l2.vxlan.vxlanNetworkPool.APIDeleteVxlanPoolRemoteVtepEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "4.7.11"
		clz ErrorCode.class
	}
}

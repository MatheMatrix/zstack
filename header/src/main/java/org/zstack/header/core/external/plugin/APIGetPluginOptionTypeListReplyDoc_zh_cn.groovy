package org.zstack.header.core.external.plugin

import org.zstack.header.errorcode.ErrorCode
import org.zstack.abstraction.OptionType

doc {

	title "获取插件选项类型列表返回"

	field {
		name "success"
		desc ""
		type "boolean"
		since "5.3.0"
	}
	ref {
		name "error"
		path "org.zstack.header.core.external.plugin.APIGetPluginOptionTypeListReply.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "5.3.0"
		clz ErrorCode.class
	}
	ref {
		name "optionTypeList"
		path "org.zstack.header.core.external.plugin.APIGetPluginOptionTypeListReply.optionTypeList"
		desc "null"
		type "Collection"
		since "5.3.0"
		clz OptionType.class
	}
}

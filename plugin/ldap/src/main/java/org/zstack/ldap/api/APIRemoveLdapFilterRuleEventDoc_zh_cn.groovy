package org.zstack.ldap.api

import org.zstack.header.errorcode.ErrorCode

doc {

	title "删除LDAP用户同步过滤规则结果"

	field {
		name "ldapServerUuidList"
		desc "删除同步过滤规则相关的的LDAP服务器UUID列表"
		type "List"
		since "zsv 4.3.0"
	}
	field {
		name "success"
		desc ""
		type "boolean"
		since "zsv 4.3.0"
	}
	ref {
		name "error"
		path "org.zstack.ldap.api.APIRemoveLdapFilterRuleEvent.error"
		desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null",false
		type "ErrorCode"
		since "zsv 4.3.0"
		clz ErrorCode.class
	}
}

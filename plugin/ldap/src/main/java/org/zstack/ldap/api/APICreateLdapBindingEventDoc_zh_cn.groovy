package org.zstack.ldap.api

import org.zstack.header.errorcode.ErrorCode
import org.zstack.identity.imports.entity.ImportAccountRefInventory

doc {

    title "LDAP账户绑定关系清单"

	field {
		name "success"
		desc ""
		type "boolean"
		since "0.6"
	}
    ref {
        name "error"
        path "org.zstack.ldap.api.APICreateLdapBindingEvent.error"
        desc "错误码，若不为null，则表示操作失败, 操作成功时该字段为null", false
        type "ErrorCode"
        since "0.6"
        clz ErrorCode.class
    }
    ref {
        name "inventory"
        path "org.zstack.ldap.api.APICreateLdapBindingEvent.inventory"
        desc "LDAP账户绑定关系清单"
        type "ImportAccountRefInventory"
        since "0.6"
        clz ImportAccountRefInventory.class
    }
}

package org.zstack.ldap.entity

import java.sql.Timestamp

doc {

    title "LDAP用户同步过滤规则"

    field {
        name "uuid"
        desc "资源的UUID，唯一标示该资源"
        type "String"
        since "zsv 4.3.0"
    }
    field {
        name "ldapServerUuid"
        desc "LDAP服务器UUID"
        type "String"
        since "zsv 4.3.0"
    }
    field {
        name "rule"
        desc "LDAP用户同步过滤规则"
        type "String"
        since "zsv 4.3.0"
    }
    field {
        name "policy"
        desc "LDAP用户同步过滤对策，基于上面的规则是允许还是禁止"
        type "String"
        since "zsv 4.3.0"
    }
    field {
        name "target"
        desc "LDAP用户同步过滤启用目标，是添加新用户时启用该规则，还是删除失效用户时启用该规则"
        type "String"
        since "zsv 4.3.0"
    }
    field {
        name "createDate"
        desc "创建时间"
        type "Timestamp"
        since "zsv 4.3.0"
    }
    field {
        name "lastOpDate"
        desc "最后一次修改时间"
        type "Timestamp"
        since "zsv 4.3.0"
    }
}
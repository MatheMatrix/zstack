package org.zstack.core.jsonlabel

import java.sql.Timestamp

doc {

    title "JsonLabelInventory的数据结构"

    field {
        name "id"
        desc "唯一标识符"
        type "long"
        since "4.2.0"
    }
    field {
        name "labelKey"
        desc "标签键"
        type "String"
        since "4.2.0"
    }
    field {
        name "labelValue"
        desc "标签值"
        type "String"
        since "4.2.0"
    }
    field {
        name "resourceUuid"
        desc "资源UUID"
        type "String"
        since "4.2.0"
    }
    field {
        name "createDate"
        desc "创建时间"
        type "Timestamp"
        since "4.2.0"
    }
    field {
        name "lastOpDate"
        desc "最后操作时间"
        type "Timestamp"
        since "4.2.0"
    }
}


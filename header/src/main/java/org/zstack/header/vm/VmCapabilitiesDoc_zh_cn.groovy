package org.zstack.header.vm

import org.zstack.header.vm.VmCapabilities

doc {

    title "云主机能力"

    field {
        name "supportLiveMigration"
        desc "是否支持热迁移"
        type "boolean"
        since "3.1.0"
    }
    field {
        name "supportVolumeMigration"
        desc "是否支持卷迁移"
        type "boolean"
        since "3.1.0"
    }
    field {
        name "supportReimage"
        desc "是否支持重装"
        type "boolean"
        since "3.1.0"
    }
    field {
        name "supportMemorySnapshot"
        desc "是否支持内存快照"
        type "boolean"
        since "3.1.0"
    }
}

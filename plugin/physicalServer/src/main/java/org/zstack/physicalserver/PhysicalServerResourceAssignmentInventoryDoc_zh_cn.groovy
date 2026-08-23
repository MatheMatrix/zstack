package org.zstack.physicalserver

doc {
    title "物理服务器资源分配清单"

    field {
        name "uuid"
        desc "资源分配UUID"
        type "String"
        since "5.5.38"
    }
    field {
        name "serverUuid"
        desc "物理服务器UUID"
        type "String"
        since "5.5.38"
    }
    field {
        name "roleType"
        desc "资源使用角色，例如ZBS、MANAGEMENT或COMPUTE"
        type "String"
        since "5.5.38"
    }
    field {
        name "cpuSet"
        desc "分配给该Role的CPU集合，例如0-3,8-11"
        type "String"
        since "5.5.38"
    }
    field {
        name "memory"
        desc "应用到该Role每个服务Handle的内存上限，单位字节；0表示不限，空表示不控制内存"
        type "Long"
        since "5.5.38"
    }
    field {
        name "state"
        desc "资源分配状态：Unsynced表示尚未完成同步，Synced表示全部服务Handle已应用并校验成功"
        type "String"
        since "5.5.38"
    }
    field {
        name "createDate"
        desc "创建时间"
        type "Timestamp"
        since "5.5.38"
    }
    field {
        name "lastOpDate"
        desc "最后一次修改时间"
        type "Timestamp"
        since "5.5.38"
    }
}

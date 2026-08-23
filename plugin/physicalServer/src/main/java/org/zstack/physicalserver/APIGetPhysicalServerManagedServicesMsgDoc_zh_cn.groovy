package org.zstack.physicalserver

doc {
    title "查看物理服务器管控服务资源使用(GetPhysicalServerManagedServices)"
    category "物理服务器"
    desc """返回物理服务器上各Role Manifest登记服务的运行事实。cpuSet为服务当前允许运行的CPU集合；cpuTime为cgroup累计CPU时间，单位纳秒；memory与memoryLimit单位均为字节，memoryLimit为包含父Role Slice约束后的有效上限，0表示不限。"""

    rest {
        request {
            url "GET /v1/physical-servers/{serverUuid}/managed-services"
            header (Authorization: 'OAuth the-session-uuid')
            clz APIGetPhysicalServerManagedServicesMsg.class
            params {
                column {
                    name "serverUuid"
                    desc "物理服务器UUID"
                    location "url"
                    type "String"
                    optional false
                    since "5.5.38"
                }
            }
        }
        response {
            clz APIGetPhysicalServerManagedServicesReply.class
        }
    }
}

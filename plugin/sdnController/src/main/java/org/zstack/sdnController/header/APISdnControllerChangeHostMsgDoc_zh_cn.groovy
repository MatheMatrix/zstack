package org.zstack.sdnController.header

import org.zstack.sdnController.header.APISdnControllerChangeHostEvent

doc {
    title "SdnControllerChangeHost"

    category "SdnController"

    desc """SDN控制器修改物理机"""

    rest {
        request {
			url "PUT /v1/sdn-controllers/{sdnControllerUuid}/hosts/{hostUuid}/actions"

			header (Authorization: 'OAuth the-session-uuid')

            clz APISdnControllerChangeHostMsg.class

            desc """"""
            
			params {

				column {
					name "sdnControllerUuid"
					enclosedIn "sdnControllerChangeHost"
					desc "SDN控制器Uuid"
					location "url"
					type "String"
					optional false
					since "5.3.0"
				}
				column {
					name "hostUuid"
					enclosedIn "sdnControllerChangeHost"
					desc "物理机UUID"
					location "url"
					type "String"
					optional false
					since "5.3.0"
				}
				column {
					name "vSwitchType"
					enclosedIn "sdnControllerChangeHost"
					desc "虚拟交换机类型"
					location "body"
					type "String"
					optional true
					since "5.3.0"
					values ("OvnDpdk","OvnKernel")
				}
				column {
					name "nicNames"
					enclosedIn "sdnControllerChangeHost"
					desc "物理机网卡名称列表"
					location "body"
					type "List"
					optional true
					since "5.3.0"
				}
				column {
					name "vtepIp"
					enclosedIn "sdnControllerChangeHost"
					desc "物理机VTEP IP"
					location "body"
					type "String"
					optional true
					since "5.3.0"
				}
				column {
					name "netmask"
					enclosedIn "sdnControllerChangeHost"
					desc "物理机VTEP IP掩码"
					location "body"
					type "String"
					optional true
					since "5.3.0"
				}
				column {
					name "bondMode"
					enclosedIn "sdnControllerChangeHost"
					desc "物理机网卡bond模式"
					location "body"
					type "String"
					optional true
					since "5.3.0"
				}
				column {
					name "lacpMode"
					enclosedIn "sdnControllerChangeHost"
					desc "物理机网卡LACP模式"
					location "body"
					type "String"
					optional true
					since "5.3.0"
				}
				column {
					name "systemTags"
					enclosedIn ""
					desc "系统标签"
					location "body"
					type "List"
					optional true
					since "5.3.0"
				}
				column {
					name "userTags"
					enclosedIn ""
					desc "用户标签"
					location "body"
					type "List"
					optional true
					since "5.3.0"
				}
			}
        }

        response {
            clz APISdnControllerChangeHostEvent.class
        }
    }
}
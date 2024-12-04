package org.zstack.sdnController.header

import org.zstack.sdnController.header.APISdnControllerChangeHostEvent

doc {
    title "SdnControllerChangeHost"

    category "SdnController"

    desc """在这里填写API描述"""

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
					desc ""
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
					desc ""
					location "body"
					type "String"
					optional true
					since "5.3.0"
					values ("OvnDpdk","OvnKernel")
				}
				column {
					name "nicNames"
					enclosedIn "sdnControllerChangeHost"
					desc ""
					location "body"
					type "List"
					optional true
					since "5.3.0"
				}
				column {
					name "vtepIp"
					enclosedIn "sdnControllerChangeHost"
					desc ""
					location "body"
					type "String"
					optional true
					since "5.3.0"
				}
				column {
					name "netmask"
					enclosedIn "sdnControllerChangeHost"
					desc ""
					location "body"
					type "String"
					optional true
					since "5.3.0"
				}
				column {
					name "bondMode"
					enclosedIn "sdnControllerChangeHost"
					desc ""
					location "body"
					type "String"
					optional true
					since "5.3.0"
				}
				column {
					name "lacpMode"
					enclosedIn "sdnControllerChangeHost"
					desc ""
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
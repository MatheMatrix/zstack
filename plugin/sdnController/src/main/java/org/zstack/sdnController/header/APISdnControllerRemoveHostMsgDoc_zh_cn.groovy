package org.zstack.sdnController.header

import org.zstack.sdnController.header.APISdnControllerRemoveHostEvent

doc {
    title "SdnControllerRemoveHost"

    category "SdnController"

    desc """在这里填写API描述"""

    rest {
        request {
			url "DELETE /v1/sdn-controllers/{sdnControllerUuid}/hosts/{hostUuid}"

			header (Authorization: 'OAuth the-session-uuid')

            clz APISdnControllerRemoveHostMsg.class

            desc """"""
            
			params {

				column {
					name "sdnControllerUuid"
					enclosedIn ""
					desc ""
					location "url"
					type "String"
					optional false
					since "5.3.0"
				}
				column {
					name "hostUuid"
					enclosedIn ""
					desc "物理机UUID"
					location "url"
					type "String"
					optional false
					since "5.3.0"
				}
				column {
					name "vSwitchType"
					enclosedIn ""
					desc ""
					location "body"
					type "String"
					optional true
					since "5.3.0"
					values ("OvnDpdk","OvnKernel")
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
            clz APISdnControllerRemoveHostEvent.class
        }
    }
}
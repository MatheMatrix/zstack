package org.zstack.header.vm

import org.zstack.header.vm.APIQueryTemplateVmInstanceReply
import org.zstack.header.query.APIQueryMessage

doc {
    title "QueryTemplateVmInstance"

    category "vmInstance"

    desc """在这里填写API描述"""

    rest {
        request {
			url "GET /v1/vm-instances/vmTemplate"
			url "GET /v1/vm-instances/vmTemplate/{uuid}"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIQueryTemplateVmInstanceMsg.class

            desc """"""
            
			params APIQueryMessage.class
        }

        response {
            clz APIQueryTemplateVmInstanceReply.class
        }
    }
}
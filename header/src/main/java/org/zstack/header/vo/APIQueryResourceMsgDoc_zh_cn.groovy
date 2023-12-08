package org.zstack.header.vo

import org.zstack.header.vo.APIQueryResourceReply
import org.zstack.header.query.APIQueryMessage

doc {
    title "QueryResource"

    category "identity"

    desc """查询资源"""

    rest {
        request {
			url "GET /v1/resources"
			url "GET /v1/resources/{uuid}"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIQueryResourceMsg.class

            desc """"""
            
			params APIQueryMessage.class
        }

        response {
            clz APIQueryResourceReply.class
        }
    }
}
package org.zstack.header.image

import org.zstack.header.image.APIQueryImageGroupReply
import org.zstack.header.query.APIQueryMessage

doc {
    title "QueryImageGroup"

    category "image"

    desc """在这里填写API描述"""

    rest {
        request {
			url "GET /v1/imagegroups"
			url "GET /v1/imagegroups/{uuid}"

			header (Authorization: 'OAuth the-session-uuid')

            clz APIQueryImageGroupMsg.class

            desc """"""
            
			params APIQueryMessage.class
        }

        response {
            clz APIQueryImageGroupReply.class
        }
    }
}
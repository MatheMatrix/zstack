package org.zstack.kvm;

import org.zstack.header.errorcode.ErrorableValue;
import org.zstack.header.message.MessageReply;
import org.zstack.header.message.NoJsonSchema;
import org.zstack.kvm.KVMAgentCommands.AgentResponse;
import org.zstack.utils.gson.JSONObjectUtil;

import java.util.LinkedHashMap;

import static org.zstack.core.Platform.*;

/**
 */
public class KVMHostAsyncHttpCallReply extends MessageReply {
    @NoJsonSchema
    private LinkedHashMap response;

    public LinkedHashMap getResponse() {
        return response;
    }

    public void setResponse(LinkedHashMap response) {
        this.response = response;
    }

    public <T> T toResponse(Class<T> clz) {
        return JSONObjectUtil.rehashObject(response, clz);
    }

    public static ErrorableValue<AgentResponse> unwrap(MessageReply reply) {
        return unwrap(reply, AgentResponse.class);
    }

    public static <T extends AgentResponse> ErrorableValue<T> unwrap(MessageReply reply, Class<T> responseClass) {
        if (!reply.isSuccess()) {
            return ErrorableValue.ofErrorCode(reply.getError());
        }

        if (!(reply instanceof KVMHostAsyncHttpCallReply)) {
            return ErrorableValue.ofErrorCode(
                    inerr("reply[%s] is not a KVMHostAsyncHttpCallReply", reply.getClass().getSimpleName()));
        }

        final KVMHostAsyncHttpCallReply castReply = (KVMHostAsyncHttpCallReply) reply;
        if (castReply.response == null) {
            return ErrorableValue.ofErrorCode(
                    operr("reply[%s] return with empty response", reply.getClass().getSimpleName()));
        }

        final T response;
        try {
            response = castReply.toResponse(responseClass);
        } catch (RuntimeException e) {
            return ErrorableValue.ofErrorCode(
                    inerr("response of reply[%s] returned can not be parsed", reply.getClass().getSimpleName()));
        }

        if (!response.isSuccess()) {
            return ErrorableValue.ofErrorCode(
                    operr("%s operation failed: %s", response.getClass().getSimpleName(), reply.getError()));
        }
        return ErrorableValue.of(response);
    }
}

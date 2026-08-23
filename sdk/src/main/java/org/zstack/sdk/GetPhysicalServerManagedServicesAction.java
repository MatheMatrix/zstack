package org.zstack.sdk;

import java.util.HashMap;
import java.util.Map;

public class GetPhysicalServerManagedServicesAction extends AbstractAction {
    private static final HashMap<String, Parameter> parameterMap =
            new HashMap<>();
    private static final HashMap<String, Parameter> nonAPIParameterMap =
            new HashMap<>();

    public static class Result {
        public ErrorCode error;
        public GetPhysicalServerManagedServicesResult value;

        public Result throwExceptionIfError() {
            if (error != null) {
                throw new ApiException(String.format(
                        "error[code: %s, description: %s, details: %s, globalErrorCode: %s]",
                        error.code, error.description, error.details,
                        error.globalErrorCode));
            }
            return this;
        }
    }

    @Param(required = true, nonempty = false, nullElements = false,
            emptyString = true, noTrim = false)
    public String serverUuid;
    @Param(required = false)
    public java.util.List systemTags;
    @Param(required = false)
    public java.util.List userTags;
    @Param(required = false)
    public String sessionId;
    @Param(required = false)
    public String accessKeyId;
    @Param(required = false)
    public String accessKeySecret;
    @Param(required = false)
    public String requestIp;

    private Result makeResult(ApiResult res) {
        Result result = new Result();
        if (res.error != null) {
            result.error = res.error;
            return result;
        }
        GetPhysicalServerManagedServicesResult value = res.getResult(
                GetPhysicalServerManagedServicesResult.class);
        result.value = value == null
                ? new GetPhysicalServerManagedServicesResult() : value;
        return result;
    }

    public Result call() {
        return makeResult(ZSClient.call(this));
    }

    public void call(final Completion<Result> completion) {
        ZSClient.call(this, new InternalCompletion() {
            @Override
            public void complete(ApiResult res) {
                completion.complete(makeResult(res));
            }
        });
    }

    protected Map<String, Parameter> getParameterMap() {
        return parameterMap;
    }

    protected Map<String, Parameter> getNonAPIParameterMap() {
        return nonAPIParameterMap;
    }

    protected RestInfo getRestInfo() {
        RestInfo info = new RestInfo();
        info.httpMethod = "GET";
        info.path = "/physical-servers/{serverUuid}/managed-services";
        info.needSession = true;
        info.needPoll = false;
        info.parameterName = "";
        return info;
    }
}

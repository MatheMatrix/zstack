package org.zstack.expon.sdk.iscsi;

import org.springframework.http.HttpMethod;
import org.zstack.expon.sdk.ExponQueryRequest;
import org.zstack.expon.sdk.ExponRestRequest;

import java.util.HashMap;
import java.util.Map;

@ExponRestRequest(
        path = "/block/iscsi/clients",
        method = HttpMethod.GET,
        responseClass = QueryIscsiClientGroupResponse.class,
        sync = false
)
public class QueryIscsiClientGroupRequest extends ExponQueryRequest {
    private static final HashMap<String, Parameter> parameterMap = new HashMap<>();

    @Override
    protected Map<String, Parameter> getParameterMap() {
        return parameterMap;
    }
}

package org.zstack.expon.sdk.iscsi;

import org.springframework.http.HttpMethod;
import org.zstack.expon.sdk.ExponRequest;
import org.zstack.expon.sdk.ExponRestRequest;
import org.zstack.expon.sdk.Param;

import java.util.HashMap;
import java.util.Map;

@ExponRestRequest(
        path = "/block/iscsi/clients/{id}",
        method = HttpMethod.DELETE,
        responseClass = CreateIscsiClientGroupResponse.class,
        sync = false
)
public class DeleteIscsiClientGroupRequest extends ExponRequest {
    private static final HashMap<String, Parameter> parameterMap = new HashMap<>();

    @Param
    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    protected Map<String, Parameter> getParameterMap() {
        return parameterMap;
    }
}

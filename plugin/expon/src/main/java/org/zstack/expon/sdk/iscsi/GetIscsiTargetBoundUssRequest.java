package org.zstack.expon.sdk.iscsi;

import org.springframework.http.HttpMethod;
import org.zstack.expon.sdk.ExponRequest;
import org.zstack.expon.sdk.ExponRestRequest;
import org.zstack.expon.sdk.Param;
import org.zstack.expon.sdk.volume.GetVolumeResponse;

import java.util.HashMap;
import java.util.Map;

@ExponRestRequest(
        path = "/block/iscsi/gateways/{id}/nodes",
        method = HttpMethod.GET,
        responseClass = GetVolumeResponse.class,
        sync = false
)
public class GetIscsiTargetBoundUssRequest extends ExponRequest {
    private static final HashMap<String, Parameter> parameterMap = new HashMap<>();

    @Param
    private String id;

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    protected Map<String, Parameter> getParameterMap() {
        return parameterMap;
    }

}

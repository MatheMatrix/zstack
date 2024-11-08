package org.zstack.expon.sdk.iscsi;

import org.springframework.http.HttpMethod;
import org.zstack.expon.sdk.ExponAction;
import org.zstack.expon.sdk.ExponRequest;
import org.zstack.expon.sdk.ExponRestRequest;
import org.zstack.expon.sdk.Param;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExponRestRequest(
        path = "/block/iscsi/gateways/{id}/add_clients",
        method = HttpMethod.PUT,
        responseClass = AddIscsiClientGroupToIscsiTargetResponse.class,
        sync = false
)
public class AddIscsiClientGroupToIscsiTargetRequest extends ExponRequest {
    private static final HashMap<String, Parameter> parameterMap = new HashMap<>();

    @Param
    private String id;
    @Param
    private String action = ExponAction.add.name();
    @Param
    private List<String> clients;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public List<String> getClients() {
        return clients;
    }

    public void setClients(List<String> clients) {
        this.clients = clients;
    }

    @Override
    protected Map<String, Parameter> getParameterMap() {
        return parameterMap;
    }
}

package org.zstack.xinfini.sdk.volume;

import org.springframework.http.HttpMethod;
import org.zstack.externalStorage.sdk.Param;
import org.zstack.xinfini.XInfiniApiCategory;
import org.zstack.xinfini.sdk.XInfiniRequest;
import org.zstack.xinfini.sdk.XInfiniRestRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * @ Author : yh.w
 * @ Date   : Created in 17:36 2024/5/27
 */
@XInfiniRestRequest(
    path = "/bs-volumes/{id}",
    method = HttpMethod.PATCH,
    responseClass = GetVolumeResponse.class,
    category = XInfiniApiCategory.AFA
)
public class UpdateVolumeRequest extends XInfiniRequest {
    @Param(required = false)
    private String creator;

    @Param
    private int id;

    @Param(required = false)
    private long sizeMb;

    @Param(required = false)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public long getSizeMb() {
        return sizeMb;
    }

    public void setSizeMb(long sizeMb) {
        this.sizeMb = sizeMb;
    }

    private static final HashMap<String, Parameter> parameterMap = new HashMap<>();

    @Override
    public Map<String, Parameter> getParameterMap() {
        return parameterMap;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}

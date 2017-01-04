package org.zstack.rest;

import org.apache.commons.beanutils.PropertyUtils;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.message.APIEvent;
import org.zstack.utils.gson.JSONObjectUtil;

import java.util.*;

/**
 * Created by xing5 on 2017/1/2.
 */
public class ApiEventResult {
    private Object apiEvent;
    private Map<String, String> schema;
    private String apiEventClassName;

    static APIEvent fromJson(String jsonStr) {
        try {
            ApiEventResult res = JSONObjectUtil.toObject(jsonStr, ApiEventResult.class);
            Class<? extends APIEvent> clz = (Class<? extends APIEvent>) Class.forName(res.apiEventClassName);

            APIEvent evt = JSONObjectUtil.rehashObject(res.apiEvent, clz);

            List<String> paths = new ArrayList();
            paths.addAll(res.schema.keySet());
            Collections.sort(paths);

            for (String path : paths) {
                String clzName = res.schema.get(path);

                Object bean = PropertyUtils.getProperty(evt, path);
                if (bean.getClass().getName().equals(clzName)) {
                    // not an inherent object
                    continue;
                }

                Class fclz = Class.forName(clzName);
                Object val = JSONObjectUtil.rehashObject(PropertyUtils.getProperty(res.apiEvent, path), fclz);
                PropertyUtils.setProperty(evt, path, val);
            }

            return evt;
        } catch (Exception e) {
            throw new CloudRuntimeException(e);
        }
    }

    static String toJson(APIEvent evt) {
        ApiEventResult res = new ApiEventResult();
        res.apiEvent = evt;
        res.apiEventClassName = evt.getClass().getName();
        res.schema = new JsonSchemaBuilder(evt).build();

        return JSONObjectUtil.toJsonString(res);
    }
}

package org.zstack.header.storage.addon.primary;

import org.zstack.header.search.Inventory;
import org.zstack.header.storage.primary.PrimaryStorageInventory;
import org.zstack.utils.gson.JSONObjectUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Inventory(mappingVOClass = ExternalPrimaryStorageVO.class)
public class ExternalPrimaryStorageInventory extends PrimaryStorageInventory {
    private String identity;

    /**
     * @example {
     * "config": "{
     * "pools": [
     * {
     * "name": "pool1",
     * "aliasName": "pool-high",
     * },
     * {
     * "name": "pool2",
     * }
     * ]
     * }
     */
    private LinkedHashMap config;

    /**
     * @example {
     * "addonInfo": {
     * "pools": [
     * {
     * "name": "pool1",
     * "availableCapacity": 100,
     * "totalCapacity": 200
     * },
     * {
     * "name": "pool2",
     * "availableCapacity": 100,
     * "totalCapacity": 200
     * }
     * ]
     * }
     */
    private LinkedHashMap addonInfo;

    private List<String> outputProtocols;

    private String defaultProtocol;

    public ExternalPrimaryStorageInventory() {
        super();
    }

    public ExternalPrimaryStorageInventory(ExternalPrimaryStorageVO lvo) {
        super(lvo);
        identity = lvo.getIdentity();
        config = JSONObjectUtil.toObject(lvo.getConfig(), LinkedHashMap.class);
        desensitizeConfig(config);
        addonInfo = JSONObjectUtil.toObject(lvo.getAddonInfo(), LinkedHashMap.class);
        desensitizeAddonInfo(addonInfo);
        outputProtocols = lvo.getOutputProtocols().stream().map(PrimaryStorageOutputProtocolRefVO::getOutputProtocol).collect(Collectors.toList());
        defaultProtocol = lvo.getDefaultProtocol();
    }

    public static ExternalPrimaryStorageInventory valueOf(ExternalPrimaryStorageVO lvo) {
        return new ExternalPrimaryStorageInventory(lvo);
    }

    private static final String MASK = "******";

    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            "password", "sshpassword", "privatekey", "token", "secret",
            "accesskey", "apisecret", "credential"));

    private static boolean isSensitiveKey(Object key) {
        return SENSITIVE_KEYS.contains(String.valueOf(key).toLowerCase());
    }

    private static void desensitizeConfig(Map config) {
        if (config == null) return;
        desensitizeUrlItems(config.get("mdsUrls"));
        desensitizeUrlItems(config.get("mdsInfos"));
    }

    private static void desensitizeAddonInfo(Map addonInfo) {
        if (addonInfo == null) return;
        desensitizeMap(addonInfo);
    }

    private static void desensitizeUrlItems(Object val) {
        if (!(val instanceof List)) return;
        List list = (List) val;
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Map) {
                desensitizeUrlInMap((Map) item);
            } else if (item instanceof List) {
                desensitizeUrlItems(item);
            } else if (item instanceof String) {
                list.set(i, desensitizeUrl((String) item));
            }
        }
    }

    private static void desensitizeUrlInMap(Map map) {
        map.replaceAll((k, v) -> {
            if (v instanceof String) {
                return desensitizeUrl((String) v);
            } else if (v instanceof Map) {
                desensitizeUrlInMap((Map) v);
            } else if (v instanceof List) {
                desensitizeUrlItems(v);
            }
            return v;
        });
    }

    private static void desensitizeMap(Map map) {
        map.replaceAll((k, v) -> {
            if (isSensitiveKey(k)) {
                return MASK;
            }
            if (v instanceof Map) {
                desensitizeMap((Map) v);
            } else if (v instanceof List) {
                desensitizeList((List) v);
            }
            return v;
        });
    }

    private static void desensitizeList(List list) {
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Map) {
                desensitizeMap((Map) item);
            } else if (item instanceof List) {
                desensitizeList((List) item);
            }
        }
    }

    private static String desensitizeUrl(String url) {
        int atIndex = url.lastIndexOf('@');
        if (atIndex <= 0) {
            return url;
        }
        int schemeIndex = url.indexOf("://");
        int credentialCheckStart;
        if (schemeIndex >= 0 && schemeIndex < atIndex) {
            credentialCheckStart = schemeIndex + 3;
        } else {
            credentialCheckStart = 0;
        }
        int colonIndex = url.indexOf(':', credentialCheckStart);
        if (colonIndex < 0 || colonIndex >= atIndex) {
            return url;
        }
        if (schemeIndex >= 0) {
            return url.substring(0, schemeIndex + 3) + "***" + url.substring(atIndex);
        }
        return "***" + url.substring(atIndex);
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public LinkedHashMap getConfig() {
        return config;
    }

    public void setConfig(LinkedHashMap config) {
        this.config = config;
    }

    public List<String> getOutputProtocols() {
        return outputProtocols;
    }

    public void setOutputProtocols(List<String> outputProtocols) {
        this.outputProtocols = outputProtocols;
    }

    public String getDefaultProtocol() {
        return defaultProtocol;
    }

    public void setDefaultProtocol(String defaultProtocol) {
        this.defaultProtocol = defaultProtocol;
    }

    public LinkedHashMap getAddonInfo() {
        return addonInfo;
    }

    public void setAddonInfo(LinkedHashMap addonInfo) {
        this.addonInfo = addonInfo;
    }
}

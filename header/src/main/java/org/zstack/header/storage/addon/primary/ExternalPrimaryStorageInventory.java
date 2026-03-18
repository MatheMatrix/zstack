package org.zstack.header.storage.addon.primary;

import org.zstack.header.search.Inventory;
import org.zstack.header.storage.primary.PrimaryStorageInventory;
import org.zstack.utils.BeanUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Inventory(mappingVOClass = ExternalPrimaryStorageVO.class)
public class ExternalPrimaryStorageInventory extends PrimaryStorageInventory {
    private static final CLogger logger = Utils.getLogger(ExternalPrimaryStorageInventory.class);
    private static final Map<String, Class<? extends ExternalPrimaryStorageConfig>> configClassRegistry = new ConcurrentHashMap<>();

    static {
        for (Class<? extends ExternalPrimaryStorageConfig> clz : BeanUtils.reflections.getSubTypesOf(ExternalPrimaryStorageConfig.class)) {
            if (clz.isInterface()) {
                continue;
            }
            try {
                ExternalPrimaryStorageConfig instance = clz.newInstance();
                configClassRegistry.put(instance.getIdentity(), clz);
            } catch (Exception e) {
                logger.warn(String.format("failed to register ExternalPrimaryStorageConfig: %s", clz.getName()), e);
            }
        }
    }

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
        addonInfo = JSONObjectUtil.toObject(lvo.getAddonInfo(), LinkedHashMap.class);
        outputProtocols = lvo.getOutputProtocols().stream().map(PrimaryStorageOutputProtocolRefVO::getOutputProtocol).collect(Collectors.toList());
        defaultProtocol = lvo.getDefaultProtocol();

        Class<? extends ExternalPrimaryStorageConfig> configClass = configClassRegistry.get(identity);
        if (configClass != null) {
            ExternalPrimaryStorageConfig typedConfig = JSONObjectUtil.toObject(lvo.getConfig(), configClass);
            ExternalPrimaryStorageConfig desensitized = typedConfig.desensitize();
            config = JSONObjectUtil.toObject(JSONObjectUtil.toJsonString(desensitized), LinkedHashMap.class);
        } else {
            config = JSONObjectUtil.toObject(lvo.getConfig(), LinkedHashMap.class);
        }
    }

    public static ExternalPrimaryStorageInventory valueOf(ExternalPrimaryStorageVO lvo) {
        return new ExternalPrimaryStorageInventory(lvo);
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

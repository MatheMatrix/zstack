package org.zstack.storage.primary;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.Q;
import org.zstack.header.Component;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageVO;
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageVO_;
import org.zstack.header.storage.primary.PrimaryStorageConstant;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.header.storage.primary.PrimaryStorageVO_;
import org.zstack.resourceconfig.ResourceConfig;
import org.zstack.resourceconfig.ResourceConfigFacade;
import org.zstack.resourceconfig.ResourceConfigInventory;

import java.util.ArrayList;
import java.util.List;

public class PrimaryStorageIothreadMaxResolver implements Component {
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private ResourceConfigFacade rcf;

    private List<PrimaryStorageIothreadMaxProvider> providers = new ArrayList<>();
    private ResourceConfig resourceConfig;

    public int resolve(String primaryStorageUuid) {
        Integer resourceValue = resolveResourceValue(primaryStorageUuid);
        if (resourceValue != null) {
            return resourceValue;
        }

        PrimaryStorageIdentity identity = resolveIdentity(primaryStorageUuid);
        for (PrimaryStorageIothreadMaxProvider provider : providers) {
            if (provider.match(primaryStorageUuid, identity.type, identity.identity)) {
                return provider.getIothreadMax(primaryStorageUuid);
            }
        }

        return PrimaryStorageGlobalConfig.IOTHREAD_MAX.value(Integer.class);
    }

    public String resolvePrimaryStorageIdentity(String primaryStorageUuid) {
        return resolveIdentity(primaryStorageUuid).identity;
    }

    private Integer resolveResourceValue(String primaryStorageUuid) {
        List<ResourceConfigInventory> configs = resourceConfig.getEffectiveResourceConfigs(primaryStorageUuid);
        return configs.isEmpty() ? null : Integer.valueOf(configs.get(0).getValue());
    }

    private PrimaryStorageIdentity resolveIdentity(String primaryStorageUuid) {
        String type = Q.New(PrimaryStorageVO.class)
                .select(PrimaryStorageVO_.type)
                .eq(PrimaryStorageVO_.uuid, primaryStorageUuid)
                .findValue();
        if (type == null) {
            throw new CloudRuntimeException(String.format("primary storage[uuid:%s] may have been deleted", primaryStorageUuid));
        }

        String identity = type;
        if (PrimaryStorageConstant.EXTERNAL_PRIMARY_STORAGE_TYPE.equals(type)) {
            identity = Q.New(ExternalPrimaryStorageVO.class)
                    .select(ExternalPrimaryStorageVO_.identity)
                    .eq(ExternalPrimaryStorageVO_.uuid, primaryStorageUuid)
                    .findValue();
            if (identity == null) {
                throw new CloudRuntimeException(String.format("external primary storage[uuid:%s] identity is missing", primaryStorageUuid));
            }
        }

        return new PrimaryStorageIdentity(type, identity);
    }

    @Override
    public boolean start() {
        resourceConfig = rcf.getResourceConfig(PrimaryStorageGlobalConfig.IOTHREAD_MAX.getIdentity());
        providers = new ArrayList<>(pluginRgty.getExtensionList(PrimaryStorageIothreadMaxProvider.class));
        return true;
    }

    @Override
    public boolean stop() {
        providers.clear();
        return true;
    }

    private static class PrimaryStorageIdentity {
        private final String type;
        private final String identity;

        private PrimaryStorageIdentity(String type, String identity) {
            this.type = type;
            this.identity = identity;
        }
    }
}

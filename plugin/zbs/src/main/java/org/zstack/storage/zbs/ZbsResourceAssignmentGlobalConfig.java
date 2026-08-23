package org.zstack.storage.zbs;

import org.zstack.core.config.GlobalConfig;
import org.zstack.core.config.GlobalConfigDef;
import org.zstack.core.config.GlobalConfigDefinition;
import org.zstack.core.config.GlobalConfigValidation;

@GlobalConfigDefinition
public class ZbsResourceAssignmentGlobalConfig {
    public static final String CATEGORY = "physicalServer";

    @GlobalConfigDef(type = Long.class, defaultValue = "60",
            description = "ZBS CPU isolation Provider call timeout in seconds")
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig PROVIDER_CALL_TIMEOUT =
            new GlobalConfig(CATEGORY, "zbsCpuIsolation.providerCallTimeout");
}

package org.zstack.core;

import org.zstack.core.config.GlobalConfig;
import org.zstack.core.config.GlobalConfigDefinition;
import org.zstack.core.config.GlobalConfigValidation;

/**
 * Created by shixin.ruan on 06/12/2024.
 */
@GlobalConfigDefinition
public class CoreGlobalConfig {
    public static final String CATEGORY = "core";

    @GlobalConfigValidation(validValues = {"zh_CN", "en_US"})
    public static GlobalConfig LOCALE = new GlobalConfig(CATEGORY, "locale");
}

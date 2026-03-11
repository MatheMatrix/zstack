package org.zstack.core.resnotify;

import org.zstack.core.config.GlobalConfig;
import org.zstack.core.config.GlobalConfigDef;
import org.zstack.core.config.GlobalConfigDefinition;
import org.zstack.core.config.GlobalConfigValidation;

@GlobalConfigDefinition
public class ResNotifyGlobalConfig {
    public static final String CATEGORY = "resNotify";

    @GlobalConfigValidation(numberGreaterThan = 0)
    @GlobalConfigDef(defaultValue = "3", type = Integer.class,
            description = "Maximum retry attempts for webhook delivery")
    public static GlobalConfig WEBHOOK_MAX_RETRIES = new GlobalConfig(CATEGORY, "webhook.maxRetries");

    @GlobalConfigValidation(numberGreaterThan = 0)
    @GlobalConfigDef(defaultValue = "5", type = Integer.class,
            description = "Base retry interval in seconds for webhook delivery (exponential backoff)")
    public static GlobalConfig WEBHOOK_RETRY_INTERVAL_SECS = new GlobalConfig(CATEGORY, "webhook.retryIntervalSecs");

    @GlobalConfigValidation(numberGreaterThan = 0)
    @GlobalConfigDef(defaultValue = "30", type = Integer.class,
            description = "Timeout in seconds for webhook HTTP delivery")
    public static GlobalConfig WEBHOOK_DELIVERY_TIMEOUT_SECS = new GlobalConfig(CATEGORY, "webhook.deliveryTimeoutSecs");
}

package org.zstack.compute.vm;

import org.zstack.core.config.GlobalConfig;
import org.zstack.core.config.GlobalConfigDefinition;
import org.zstack.core.config.GlobalConfigValidation;

/**
 * GlobalConfig for VmNicLifecycleExtensionPoint routing (F-010).
 *
 * Defaults are declared in {@code conf/globalConfig/vmNicLifecycle.xml}.
 */
@GlobalConfigDefinition
public class VmNicLifecycleGlobalConfig {
    public static final String CATEGORY = "vmNicLifecycle";

    /**
     * Per-implementation timeout (seconds) for {@code reconcileOnHost} during host heartbeat
     * reconciliation. Default is 30s (see XML).
     */
    @GlobalConfigValidation(numberGreaterThan = 0)
    public static GlobalConfig RECONCILE_TIMEOUT =
            new GlobalConfig(CATEGORY, "reconcileOnHost.timeout");
}

package org.zstack.compute.legacy;

import org.zstack.core.GlobalProperty;
import org.zstack.core.GlobalPropertyDefinition;

@GlobalPropertyDefinition
public class ComputeLegacyGlobalProperty {
    /**
     * true when first boot after upgrade to version ZSphere 4.10.0
     */
    @GlobalProperty(name="legacyCpuTopologyFix", defaultValue = "false")
    public static boolean cpuTopologyFix;
}

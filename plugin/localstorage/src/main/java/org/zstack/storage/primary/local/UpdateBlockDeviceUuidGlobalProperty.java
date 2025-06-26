package org.zstack.storage.primary.local;

import org.zstack.core.GlobalProperty;
import org.zstack.core.GlobalPropertyDefinition;

@GlobalPropertyDefinition
public class UpdateBlockDeviceUuidGlobalProperty {
    @GlobalProperty(name = "deduplicateVolumeGc", defaultValue = "false")
    public static boolean DEDUPLICATEVOLUMEGC;
}

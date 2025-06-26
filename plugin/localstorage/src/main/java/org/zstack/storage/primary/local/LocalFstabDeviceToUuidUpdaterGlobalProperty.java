package org.zstack.storage.primary.local;


import org.zstack.core.GlobalProperty;
import org.zstack.core.GlobalPropertyDefinition;

@GlobalPropertyDefinition
public class LocalFstabDeviceToUuidUpdaterGlobalProperty {
    @GlobalProperty(name = "localFstabDeviceToUuidUpdater", defaultValue = "false")
    public static boolean LOCAL_FSTAB_DEVICE_TO_UUID_UPDATER;
}
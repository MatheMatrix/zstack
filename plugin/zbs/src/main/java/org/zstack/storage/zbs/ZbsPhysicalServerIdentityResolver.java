package org.zstack.storage.zbs;

import java.util.Set;

public interface ZbsPhysicalServerIdentityResolver {
    Set<String> resolveServerUuids(
            String primaryStorageUuid, AddonInfo addonInfo);

    void enrichPhysicalServerSerialNumbers(
            String primaryStorageUuid, AddonInfo addonInfo);
}

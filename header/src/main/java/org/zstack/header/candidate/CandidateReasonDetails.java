package org.zstack.header.candidate;

import org.zstack.utils.DebugUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CandidateReasonDetails {
    private static final Set<String> ALLOWED_KEYS = new HashSet<>(Arrays.asList(
            "stage",
            "checker",
            "extension",
            "requiredCpu",
            "availableCpu",
            "requiredMemory",
            "availableMemory",
            "totalPhysicalMemory",
            "requiredCapacity",
            "availableCapacity",
            "zoneUuid",
            "clusterUuid",
            "hostUuid",
            "primaryStorageUuid",
            "l3NetworkUuid",
            "avoidHostUuids",
            "expected",
            "actual"
    ));

    public static void checkAllowed(String key, Object value) {
        DebugUtils.Assert(ALLOWED_KEYS.contains(key), String.format("candidate reason detail key[%s] is not allowed", key));
    }

    public static boolean isAllowed(String key) {
        return ALLOWED_KEYS.contains(key);
    }
}

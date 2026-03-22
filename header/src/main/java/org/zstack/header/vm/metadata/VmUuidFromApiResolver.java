package org.zstack.header.vm.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a field value (e.g. volumeUuid, snapshotUuid, nicUuid) into VM UUID(s).
 *
 * <p>Implementations are pure converters — they receive the already-extracted field
 * value and resolve it to VM UUIDs. The interceptor handles field extraction from
 * the API message via reflection based on {@link MetadataImpact#field()}.</p>
 */
public interface VmUuidFromApiResolver {

    /**
     * Resolve a single field value to VM UUID(s).
     *
     * @param fieldValue the value extracted from the API message field
     * @return list of VM UUIDs, never null
     */
    List<String> resolveVmUuids(String fieldValue);

    /**
     * Resolve multiple field values (batch) to VM UUID(s).
     * Default implementation iterates and delegates to {@link #resolveVmUuids(String)}.
     */
    default List<String> batchResolveVmUuids(List<String> fieldValues) {
        List<String> result = new ArrayList<>();
        if (fieldValues == null || fieldValues.isEmpty()) {
            return result;
        }
        for (String v : fieldValues) {
            if (v == null) {
                continue;
            }
            List<String> resolved = resolveVmUuids(v);
            if (resolved != null) {
                result.addAll(resolved);
            }
        }
        return result;
    }
}

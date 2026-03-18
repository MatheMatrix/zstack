package org.zstack.header.storage.addon.primary;

/**
 * Interface for external primary storage config desensitization.
 *
 * Each external primary storage plugin should implement this on its Config class.
 * ZStack will call {@link #desensitize()} before outputting config to API responses or logs,
 * so plugin developers only need to implement this method to mask sensitive fields.
 */
public interface ExternalPrimaryStorageConfig {
    /**
     * The identity of the external primary storage plugin (e.g. "zbs").
     */
    String getIdentity();

    /**
     * Return a desensitized copy of this config with sensitive fields masked.
     * The original object must not be modified.
     */
    ExternalPrimaryStorageConfig desensitize();
}

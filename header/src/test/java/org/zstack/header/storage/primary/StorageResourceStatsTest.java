package org.zstack.header.storage.primary;

import org.junit.Test;

import static org.junit.Assert.assertNull;

public class StorageResourceStatsTest {
    @Test
    public void setSizeAllowsNull() {
        StorageResourceStats stats = new StorageResourceStats();

        stats.setSize(null);

        assertNull(stats.getSize());
    }
}

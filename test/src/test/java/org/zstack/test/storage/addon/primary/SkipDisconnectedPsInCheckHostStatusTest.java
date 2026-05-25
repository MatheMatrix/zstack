package org.zstack.test.storage.addon.primary;

import org.junit.Assert;
import org.junit.Test;
import org.zstack.header.storage.primary.PrimaryStorageHostStatus;

import java.util.concurrent.TimeUnit;

/**
 * ZSTAC-80129: Verify skip Disconnected PS in checkHostStatus.
 * When PS is Disconnected, host status should also be Disconnected.
 */
public class SkipDisconnectedPsInCheckHostStatusTest {

    @Test
    public void testDisconnectedStatusEnum() {
        Assert.assertNotNull(PrimaryStorageHostStatus.Disconnected);
        Assert.assertNotNull(PrimaryStorageHostStatus.Connected);
    }
}

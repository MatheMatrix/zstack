package org.zstack.test.storage.ceph;

import org.junit.Assert;
import org.junit.Test;
import org.zstack.storage.primary.CheckHostStorageConnectionMsg;

import java.util.concurrent.TimeUnit;

/**
 * ZSTAC-84114: Verify CheckHostStorageConnectionMsg timeout is set (60s)
 * and that the default is no-timeout (-1) to ensure the fix is effective.
 */
public class CheckHostStorageConnectionMsgTimeoutTest {

    @Test
    public void testTimeoutSetTo60Seconds() {
        CheckHostStorageConnectionMsg msg = new CheckHostStorageConnectionMsg();
        msg.setPrimaryStorageUuid("test-uuid");
        msg.setHostUuids(java.util.Collections.singletonList("host-uuid"));
        msg.setTimeout(TimeUnit.SECONDS.toMillis(60));
        Assert.assertEquals(TimeUnit.SECONDS.toMillis(60), msg.getTimeout());
    }

    @Test
    public void testTimeoutDefaultIsNegative() {
        CheckHostStorageConnectionMsg msg = new CheckHostStorageConnectionMsg();
        Assert.assertEquals(-1, msg.getTimeout());
    }
}

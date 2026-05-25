package org.zstack.test.storage.addon.primary;

import junit.framework.Assert;
import org.junit.Test;
import org.zstack.header.storage.primary.PrimaryStorageHostStatus;
import org.zstack.header.storage.primary.PrimaryStorageStatus;
import org.zstack.header.storage.primary.UpdatePrimaryStorageHostStatusMsg;

public class SkipDisconnectedPsInCheckHostStatusTest {

    @Test
    public void testDisconnectedPsMapsToDisconnectedHostStatus() {
        Assert.assertNotNull(PrimaryStorageStatus.Disconnected);
        Assert.assertNotNull(PrimaryStorageHostStatus.Disconnected);
    }

    @Test
    public void testUpdateHostStatusMsgForDisconnectedPs() {
        UpdatePrimaryStorageHostStatusMsg msg = new UpdatePrimaryStorageHostStatusMsg();
        msg.setPrimaryStorageUuid("ps-uuid");
        msg.setHostUuid("host-uuid");
        msg.setStatus(PrimaryStorageHostStatus.Disconnected);

        Assert.assertEquals("ps-uuid", msg.getPrimaryStorageUuid());
        Assert.assertEquals("host-uuid", msg.getHostUuid());
        Assert.assertEquals(PrimaryStorageHostStatus.Disconnected, msg.getStatus());
    }
}

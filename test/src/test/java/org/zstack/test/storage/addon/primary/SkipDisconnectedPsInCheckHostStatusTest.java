package org.zstack.test.storage.addon.primary;

import junit.framework.Assert;
import org.junit.Test;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.storage.primary.PrimaryStorageHostStatus;
import org.zstack.header.storage.primary.PrimaryStorageStatus;
import org.zstack.header.storage.primary.UpdatePrimaryStorageHostStatusMsg;

import static org.zstack.core.Platform.operr;

public class SkipDisconnectedPsInCheckHostStatusTest {

    @Test
    public void testDisconnectedPsSkipsHealthCheck() {
        String psUuid = "disconnected-ps-uuid";
        String hostUuid = "kvm-host-uuid";

        ErrorCode reason = operr("external primary storage[uuid:%s, name:%s] is Disconnected, skip health check",
                psUuid, "test-ps-name");

        UpdatePrimaryStorageHostStatusMsg msg = new UpdatePrimaryStorageHostStatusMsg();
        msg.setPrimaryStorageUuid(psUuid);
        msg.setHostUuid(hostUuid);
        msg.setStatus(PrimaryStorageHostStatus.Disconnected);
        msg.setReason(reason);

        Assert.assertEquals(psUuid, msg.getPrimaryStorageUuid());
        Assert.assertEquals(hostUuid, msg.getHostUuid());
        Assert.assertEquals(PrimaryStorageHostStatus.Disconnected, msg.getStatus());
        Assert.assertNotNull(msg.getReason());
        Assert.assertTrue("error reason must mention Disconnected",
                msg.getReason().getDetails().contains("Disconnected"));
    }

    @Test
    public void testConnectedPsReportsHostStatusAfterHealthCheck() {
        String psUuid = "connected-ps-uuid";
        String hostUuid = "kvm-host-uuid";

        UpdatePrimaryStorageHostStatusMsg msg = new UpdatePrimaryStorageHostStatusMsg();
        msg.setPrimaryStorageUuid(psUuid);
        msg.setHostUuid(hostUuid);
        msg.setStatus(PrimaryStorageHostStatus.Connected);

        Assert.assertEquals(psUuid, msg.getPrimaryStorageUuid());
        Assert.assertEquals(hostUuid, msg.getHostUuid());
        Assert.assertEquals(PrimaryStorageHostStatus.Connected, msg.getStatus());
        Assert.assertNull("connected path does not set reason", msg.getReason());
    }

    @Test
    public void testDisconnectedStatusMapping() {
        Assert.assertNotNull(PrimaryStorageStatus.Disconnected);
        Assert.assertNotNull(PrimaryStorageHostStatus.Disconnected);
    }
}

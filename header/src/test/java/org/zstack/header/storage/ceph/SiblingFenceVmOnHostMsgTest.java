package org.zstack.header.storage.ceph;

import org.junit.Test;
import static org.junit.Assert.*;

public class SiblingFenceVmOnHostMsgTest {
    @Test
    public void testFields() {
        SiblingFenceVmOnHostMsg msg = new SiblingFenceVmOnHostMsg();
        msg.setFailedHostUuid("h-failed");
        msg.setVmUuid("vm-1");
        msg.setClusterUuid("c-1");
        msg.setHaTargetHostUuid("h-target");
        assertEquals("h-failed", msg.getFailedHostUuid());
        assertEquals("vm-1", msg.getVmUuid());
        assertEquals("c-1", msg.getClusterUuid());
        assertEquals("h-target", msg.getHaTargetHostUuid());
    }

    @Test
    public void testReplyFields() {
        SiblingFenceVmOnHostReply r = new SiblingFenceVmOnHostReply();
        r.setAlive(true);
        r.setKilled(true);
        r.setExecutorHostUuid("h-sibling");
        r.setExecutorRole("sibling");
        r.setSshReachable(true);
        r.setQemuFound(true);
        r.setReason("");
        assertTrue(r.isKilled());
        assertEquals("sibling", r.getExecutorRole());
    }
}

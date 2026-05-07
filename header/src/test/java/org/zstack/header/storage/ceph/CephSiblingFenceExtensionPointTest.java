package org.zstack.header.storage.ceph;

import org.junit.Test;
import org.zstack.header.core.ReturnValueCompletion;
import static org.junit.Assert.*;

public class CephSiblingFenceExtensionPointTest {
    @Test
    public void testInterfaceShape() {
        CephSiblingFenceExtensionPoint ext = new CephSiblingFenceExtensionPoint() {
            @Override
            public void fenceVmOnFailedHost(String failedHostUuid, String vmUuid,
                                            String clusterUuid, String haTargetHostUuid,
                                            ReturnValueCompletion<SiblingFenceVmOnHostReply> completion) {
                SiblingFenceVmOnHostReply r = new SiblingFenceVmOnHostReply();
                r.setAlive(false);
                completion.success(r);
            }
        };
        final boolean[] hit = {false};
        ext.fenceVmOnFailedHost("a", "b", "c", "d",
            new ReturnValueCompletion<SiblingFenceVmOnHostReply>(null) {
                public void success(SiblingFenceVmOnHostReply r) { hit[0] = true; }
                public void fail(org.zstack.header.errorcode.ErrorCode e) {}
            });
        assertTrue(hit[0]);
    }
}

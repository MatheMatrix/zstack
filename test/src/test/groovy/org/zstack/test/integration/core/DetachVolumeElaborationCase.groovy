package org.zstack.test.integration.core

import org.zstack.core.Platform
import org.zstack.header.errorcode.ErrorCode
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.string.ElaborationSearchMethod
import org.zstack.utils.string.ErrorCodeElaboration
import org.zstack.utils.string.StringSimilarity

class DetachVolumeElaborationCase extends SubCase {
    EnvSpec env

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        INCLUDE_CORE_SERVICES = false
    }

    @Override
    void environment() {
        env = new EnvSpec()
    }

    @Override
    void test() {
        testElaborationEntryStructure()
        testDistanceMatchWithRealKvmagentError()
    }

    void testElaborationEntryStructure() {
        List<ErrorCodeElaboration> elaborations = StringSimilarity.getElaborations()
        ErrorCodeElaboration psDetach = elaborations.find { e ->
            e.category == "PS" && e.code == "1000" && e.method == ElaborationSearchMethod.distance
        }
        assert psDetach != null : "PS detach elaboration entry (PS.1000, distance) not found"

        assert psDetach.regex.contains("volume[uuid:%s]") : "must contain volume[uuid:%s] to match kvmagent error format"
        assert psDetach.regex.contains("vm[uuid:%s]") : "must contain vm[uuid:%s] to match kvmagent error format"
        assert psDetach.regex.contains("it's still attached after 5 seconds") : "must use it's (apostrophe) matching kvmagent vm_plugin.py:3756"
        assert psDetach.regex.contains("failed to detach data volume[uuid:%s, installPath:%s]") : "must match KVMHost.java:3246 error prefix"
        assert psDetach.message_cn == "由于云主机操作系统限制可能会导致卸载云盘失败，请尝试对云主机关机后再次卸载。"
        assert psDetach.message_en == "Detaching volume may fail due to limitations of the VM operating system. Please try again after VM shutdown."
    }

    /**
     * Verify that the real kvmagent error format (KVMHost.java:3246 prefix +
     * vm_plugin.py:3757 suffix) matches the PS-1000 distance regex.
     *
     * The error is constructed with literal UUIDs/names — NOT by filling the
     * regex template's %s placeholders — so the test independently validates
     * that the regex handles [uuid:...] substrings in the "because" clause.
     */
    void testDistanceMatchWithRealKvmagentError() {
        // Simulate the exact error KVMHost.java:3246 produces:
        //   operr("failed to detach data volume[uuid:%s, installPath:%s] from vm[uuid:%s, name:%s]
        //          on kvm host[uuid:%s, ip:%s], because %s",
        //         volUuid, installPath, vmUuid, vmName, hostUuid, hostIp, kvmagentError)
        // where kvmagentError = vm_plugin.py:3757 exception text with real UUIDs.
        def kvmagentError = "unable to detach the volume[uuid:vol-abc-123] from the vm[uuid:vm-def-456];" +
                "it's still attached after 5 seconds"
        def err = Platform.operr(
                "failed to detach data volume[uuid:%s, installPath:%s] from vm[uuid:%s, name:%s] on kvm host[uuid:%s, ip:%s], because %s",
                "vol-abc-123", "/var/lib/nova/instances/vol-abc-123",
                "vm-def-456", "test-instance",
                "host-ghi-789", "192.168.1.100",
                kvmagentError
        ) as ErrorCode

        assert err.messages != null
        assert err.messages.method == ElaborationSearchMethod.distance
        assert err.messages.message_cn == "由于云主机操作系统限制可能会导致卸载云盘失败，请尝试对云主机关机后再次卸载。"
    }
}

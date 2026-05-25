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
        testDistanceMatchWithFullFormatString()
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

    void testDistanceMatchWithFullFormatString() {
        def err = Platform.operr(
                "failed to detach data volume[uuid:%s, installPath:%s] from vm[uuid:%s, name:%s] on kvm host[uuid:%s, ip:%s], because unable to detach the volume[uuid:%s] from the vm[uuid:%s];it's still attached after 5 seconds",
                "vol-123", "/path/vol", "vm-456", "my-vm", "host-789", "10.0.0.1", "vol-123", "vm-456"
        ) as ErrorCode

        assert err.messages != null
        assert err.messages.method == ElaborationSearchMethod.distance
        assert err.messages.message_cn == "由于云主机操作系统限制可能会导致卸载云盘失败，请尝试对云主机关机后再次卸载。"
    }
}

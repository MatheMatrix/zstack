package org.zstack.test.unittest.core

import org.junit.Test
import org.zstack.core.CoreGlobalProperty
import org.zstack.core.Platform

/**
 * Test cases for Platform.getManagementPid() and the pid validation
 * logic used in ManagementNodePrometheusNamespace.progressStatusMetrics().
 *
 * Related: ZSTAC-82977
 */
class ManagementPidCase {
    @Test
    void testGetManagementPidReturnsNonNull() {
        // getManagementPid() should never return null regardless of mode
        String pid = Platform.getManagementPid()
        assert pid != null : "getManagementPid() should never return null"
    }

    @Test
    void testGetManagementPidInUnitTestMode() {
        // In unit test mode, getManagementPid() returns empty string
        boolean originalValue = CoreGlobalProperty.UNIT_TEST_ON
        try {
            CoreGlobalProperty.UNIT_TEST_ON = true
            String pid = Platform.getManagementPid()
            assert pid != null : "pid should not be null even in unit test mode"
            assert pid.isEmpty() : "pid should be empty string in unit test mode"
        } finally {
            CoreGlobalProperty.UNIT_TEST_ON = originalValue
        }
    }

    @Test
    void testGetManagementPidInNormalMode() {
        // In normal mode, getManagementPid() returns a numeric PID from RuntimeMXBean
        boolean originalValue = CoreGlobalProperty.UNIT_TEST_ON
        try {
            CoreGlobalProperty.UNIT_TEST_ON = false
            String pid = Platform.getManagementPid()
            assert pid != null : "pid should not be null in normal mode"
            assert !pid.isEmpty() : "pid should not be empty in normal mode"
            assert pid.matches("\\d+") : "pid should be a numeric string, got: ${pid}"
        } finally {
            CoreGlobalProperty.UNIT_TEST_ON = originalValue
        }
    }

    @Test
    void testPidValidationWithEdgeCases() {
        // Test the pidValid logic: pid != null && !pid.isEmpty()
        // This is the guard condition used in progressStatusMetrics()

        // null pid → invalid
        String nullPid = null
        boolean valid = nullPid != null && !nullPid.isEmpty()
        assert !valid : "null pid should be invalid"

        // empty string pid (unit test mode) → invalid
        String emptyPid = ""
        valid = emptyPid != null && !emptyPid.isEmpty()
        assert !valid : "empty pid should be invalid"

        // valid numeric pid → valid
        String numericPid = "12345"
        valid = numericPid != null && !numericPid.isEmpty()
        assert valid : "numeric pid should be valid"

        // pid extracted from standard RuntimeMXBean format "pid@hostname"
        String mxBeanName = "12345@myhost"
        String extractedPid = mxBeanName.split("@")[0]
        assert extractedPid == "12345" : "should extract pid from MXBean name format"
        valid = extractedPid != null && !extractedPid.isEmpty()
        assert valid : "extracted pid should be valid"

        // edge case: MXBean name starting with @ → empty pid
        String edgeName = "@hostname"
        String edgePid = edgeName.split("@")[0]
        assert edgePid == "" : "edge case '@hostname' produces empty pid"
        valid = edgePid != null && !edgePid.isEmpty()
        assert !valid : "empty extracted pid should be invalid"
    }
}

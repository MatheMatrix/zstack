package org.zstack.kvm;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KvmHostIothreadVqMappingCapabilityTest {
    @Test
    public void supportsBaselineVersions() {
        assertTrue(KvmHostIothreadVqMappingCapability.supported("6.2.0-451.g623f2a5caf.el8", "8.0.0-163.gd30ff15b84"));
        assertTrue(KvmHostIothreadVqMappingCapability.supported("6.2.0-546.g7638079ce5.el8", "8.0.0-164.gd30ff15b84"));
        assertTrue(KvmHostIothreadVqMappingCapability.supported("6.2.0-451.module+el8.10.0+12345", "8.0.0-163.fc39"));
    }

    @Test
    public void rejectsUnsupportedQemuKvmPackageVersion() {
        assertFalse(KvmHostIothreadVqMappingCapability.supported("6.2.0-450.g623f2a5caf.el8", "8.0.0-163.gd30ff15b84"));
    }

    @Test
    public void rejectsUnsupportedLibvirtPackageVersion() {
        assertFalse(KvmHostIothreadVqMappingCapability.supported("6.2.0-451.g623f2a5caf.el8", "8.0.0-162.gd30ff15b84"));
    }

    @Test
    public void rejectsMissingQemuKvmPackageVersion() {
        assertFalse(KvmHostIothreadVqMappingCapability.supported(null, "8.0.0-163.gd30ff15b84"));
        assertFalse(KvmHostIothreadVqMappingCapability.supported("", "8.0.0-163.gd30ff15b84"));
    }

    @Test
    public void rejectsMissingLibvirtPackageVersion() {
        assertFalse(KvmHostIothreadVqMappingCapability.supported("6.2.0-451.g623f2a5caf.el8", null));
        assertFalse(KvmHostIothreadVqMappingCapability.supported("6.2.0-451.g623f2a5caf.el8", ""));
    }
}

package org.zstack.storage.addon.primary;

import org.junit.Assert;
import org.junit.Test;
import org.zstack.header.storage.addon.primary.BackupStorageSelector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Regression test for ZSTAC-80789.
 *
 * BackupStorageSelector implementations (e.g. XInfiniStorageFactory, ExponStorageFactory)
 * are Spring beans whose getPreferBackupStorageTypes() returns a direct reference to an
 * internal field. If the caller mutates that list (e.g. via retainAll), the bean's state
 * is permanently corrupted, causing all subsequent SelectBackupStorageMsg handling to fail.
 *
 * The fix in ExternalPrimaryStorage wraps the returned list with {@code new ArrayList<>(...)}.
 * This test verifies that pattern.
 */
public class ExternalPrimaryStorageSelectBsTest {

    /**
     * Simulates a BackupStorageSelector that returns its internal field directly,
     * just like XInfiniStorageFactory and ExponStorageFactory do.
     */
    private static class FakeBackupStorageSelector implements BackupStorageSelector {
        private List<String> preferBackupStorageTypes;

        FakeBackupStorageSelector(List<String> types) {
            this.preferBackupStorageTypes = types;
        }

        @Override
        public List<String> getPreferBackupStorageTypes() {
            return preferBackupStorageTypes;
        }

        @Override
        public String getIdentity() {
            return "fake";
        }
    }

    @Test
    public void testDefensiveCopyPreventsBeaMutation() {
        // Setup: simulate a Spring bean with mutable internal state
        List<String> beanInternalList = new ArrayList<>(Arrays.asList("ImageStore", "Ceph", "FusionStor"));
        FakeBackupStorageSelector selector = new FakeBackupStorageSelector(beanInternalList);

        List<String> originalTypes = new ArrayList<>(beanInternalList);

        // First call: simulate handle(SelectBackupStorageMsg) with requiredBackupStorageTypes = ["ImageStore"]
        // The fix: defensive copy before retainAll
        List<String> preferBsTypes = new ArrayList<>(selector.getPreferBackupStorageTypes());
        List<String> requiredTypes = Arrays.asList("ImageStore");
        preferBsTypes.retainAll(requiredTypes);

        // After retainAll, the working copy should be filtered
        Assert.assertEquals(Arrays.asList("ImageStore"), preferBsTypes);

        // The bean's internal state must remain unchanged
        Assert.assertEquals(originalTypes, selector.getPreferBackupStorageTypes());
        Assert.assertEquals(3, selector.getPreferBackupStorageTypes().size());

        // Second call: simulate another handle(SelectBackupStorageMsg) with requiredBackupStorageTypes = ["Ceph"]
        List<String> preferBsTypes2 = new ArrayList<>(selector.getPreferBackupStorageTypes());
        List<String> requiredTypes2 = Arrays.asList("Ceph");
        preferBsTypes2.retainAll(requiredTypes2);

        Assert.assertEquals(Arrays.asList("Ceph"), preferBsTypes2);

        // Bean's internal state still unchanged after two calls
        Assert.assertEquals(originalTypes, selector.getPreferBackupStorageTypes());
    }

    @Test
    public void testWithoutDefensiveCopyBeanIsMutated() {
        // This test demonstrates the original bug: without defensive copy, retainAll mutates the bean
        List<String> beanInternalList = new ArrayList<>(Arrays.asList("ImageStore", "Ceph", "FusionStor"));
        FakeBackupStorageSelector selector = new FakeBackupStorageSelector(beanInternalList);

        // Without defensive copy (the old buggy code path)
        List<String> preferBsTypes = selector.getPreferBackupStorageTypes();
        preferBsTypes.retainAll(Arrays.asList("ImageStore"));

        // The bean's internal list is now corrupted
        Assert.assertEquals(1, selector.getPreferBackupStorageTypes().size());
        Assert.assertEquals(Arrays.asList("ImageStore"), selector.getPreferBackupStorageTypes());

        // Second call with "Ceph" now fails because the bean only has "ImageStore" left
        List<String> preferBsTypes2 = selector.getPreferBackupStorageTypes();
        preferBsTypes2.retainAll(Arrays.asList("Ceph"));

        // The list is now empty - this is the bug that caused clone VM to fail
        Assert.assertTrue(selector.getPreferBackupStorageTypes().isEmpty());
    }
}

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
 * BackupStorageSelector.getPreferBackupStorageTypes() must return a defensive
 * copy so callers using retainAll() cannot corrupt the bean's internal state.
 */
public class ExternalPrimaryStorageSelectBsTest {

    /**
     * Simulates the FIXED selector that returns a defensive copy.
     */
    private static class FixedSelector implements BackupStorageSelector {
        private final List<String> preferBackupStorageTypes;

        FixedSelector(List<String> types) {
            this.preferBackupStorageTypes = types;
        }

        @Override
        public List<String> getPreferBackupStorageTypes() {
            return new ArrayList<>(preferBackupStorageTypes); // fixed: defensive copy
        }

        @Override
        public String getIdentity() { return "fixed"; }

        List<String> getInternalList() { return preferBackupStorageTypes; }
    }

    /**
     * Simulates the BUGGY selector that returns a direct reference.
     */
    private static class BuggySelector implements BackupStorageSelector {
        private final List<String> preferBackupStorageTypes;

        BuggySelector(List<String> types) {
            this.preferBackupStorageTypes = types;
        }

        @Override
        public List<String> getPreferBackupStorageTypes() {
            return preferBackupStorageTypes; // buggy: direct reference
        }

        @Override
        public String getIdentity() { return "buggy"; }
    }

    @Test
    public void testFixedSelectorRetainAllDoesNotCorruptBean() {
        List<String> internal = new ArrayList<>(Arrays.asList("ImageStoreBackupStorage"));
        FixedSelector selector = new FixedSelector(internal);

        // First call: retainAll with ["CephBackupStorage"] => empty intersection
        List<String> result1 = selector.getPreferBackupStorageTypes();
        result1.retainAll(Arrays.asList("CephBackupStorage"));
        Assert.assertTrue("retainAll result should be empty", result1.isEmpty());

        // Bean internal state must be intact
        Assert.assertEquals(1, selector.getInternalList().size());
        Assert.assertEquals("ImageStoreBackupStorage", selector.getInternalList().get(0));

        // Second call: should still return the full list
        List<String> result2 = selector.getPreferBackupStorageTypes();
        Assert.assertEquals(Arrays.asList("ImageStoreBackupStorage"), result2);
    }

    @Test
    public void testBuggySelectorRetainAllCorruptsBean() {
        List<String> internal = new ArrayList<>(Arrays.asList("ImageStoreBackupStorage"));
        BuggySelector selector = new BuggySelector(internal);

        // First call: retainAll with ["CephBackupStorage"] corrupts the bean
        List<String> result1 = selector.getPreferBackupStorageTypes();
        result1.retainAll(Arrays.asList("CephBackupStorage"));

        // Bean's internal list is now permanently empty - this is the bug
        Assert.assertTrue("Bean is corrupted: list is empty", selector.getPreferBackupStorageTypes().isEmpty());
    }
}

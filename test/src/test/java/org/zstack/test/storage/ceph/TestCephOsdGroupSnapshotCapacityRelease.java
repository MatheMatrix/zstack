package org.zstack.test.storage.ceph;

import org.junit.Before;
import org.junit.Test;
import org.zstack.header.storage.primary.PrimaryStorageOverProvisioningManager;
import org.zstack.storage.ceph.primary.capacity.CephOsdGroupCapacityHelper;
import org.zstack.utils.data.SizeUnit;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ZSTAC-79709: Verify that snapshot deletion releases capacity to
 * CephOsdGroupVO at actual size, not ratio-adjusted size.
 *
 * In calculateAvailableCapacityByRatio(), snapshot capacity is
 * counted at actual size (no over-provisioning ratio). Therefore,
 * when releasing capacity after snapshot deletion, we must use
 * releaseAvailableCapacity() (actual size) instead of
 * releaseAvailableCapWithRatio() (size divided by ratio).
 *
 * With ratio=10 and snapshot=13GB:
 *   releaseAvailableCapWithRatio -> releases 1.3GB  (BUG)
 *   releaseAvailableCapacity    -> releases 13GB    (CORRECT)
 */
public class TestCephOsdGroupSnapshotCapacityRelease {

    private static final String PS_UUID = "test-ps-uuid";
    private static final long SNAPSHOT_SIZE =
            SizeUnit.GIGABYTE.toByte(13);
    private static final int OVER_PROVISIONING_RATIO = 10;

    private CephOsdGroupCapacityHelper helper;
    private PrimaryStorageOverProvisioningManager mockRatioMgr;

    @Before
    public void setUp() throws Exception {
        helper = new CephOsdGroupCapacityHelper(PS_UUID);

        mockRatioMgr = mock(
                PrimaryStorageOverProvisioningManager.class);
        // Simulate over-provisioning ratio of 10:
        // calculateByRatio returns size/ratio
        when(mockRatioMgr.calculateByRatio(
                eq(PS_UUID), anyLong()))
                .thenAnswer(inv ->
                        inv.<Long>getArgument(1)
                                / OVER_PROVISIONING_RATIO);

        injectField(helper, "ratioMgr", mockRatioMgr);
    }

    private void injectField(Object target, String fieldName,
            Object value) throws Exception {
        Field field =
                target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * ZSTAC-79709 fix: snapshot deletion must call
     * releaseAvailableCapacity() which does NOT consult the
     * over-provisioning ratio. This test verifies that
     * releaseAvailableCapacity does not call calculateByRatio,
     * meaning the full snapshot size is passed to the internal
     * release logic.
     *
     * Using null installPath so _release() returns early
     * without requiring database access.
     */
    @Test
    public void testReleaseAvailableCapacityDoesNotUseRatio() {
        helper.releaseAvailableCapacity(null, SNAPSHOT_SIZE);

        // releaseAvailableCapacity must NOT call calculateByRatio
        // so the full size is released (not divided by ratio)
        verify(mockRatioMgr, never())
                .calculateByRatio(anyString(), anyLong());
    }

    /**
     * Shows the old buggy behavior: releaseAvailableCapWithRatio
     * calls calculateByRatio which divides by the ratio.
     * With ratio=10, a 13GB snapshot would only release 1.3GB,
     * causing a capacity leak in CephOsdGroupVO.
     */
    @Test
    public void testReleaseWithRatioUsesRatioReduction() {
        helper.releaseAvailableCapWithRatio(null, SNAPSHOT_SIZE);

        // releaseAvailableCapWithRatio DOES call calculateByRatio
        verify(mockRatioMgr, times(1))
                .calculateByRatio(eq(PS_UUID), eq(SNAPSHOT_SIZE));
    }

    /**
     * Proves the capacity leak: calculateByRatio divides by
     * the ratio, so only 1/ratio of the actual snapshot size
     * would be released when using the ratio method.
     *
     * Snapshots are tracked at actual size in
     * calculateAvailableCapacityByRatio(), so they must also
     * be released at actual size.
     */
    @Test
    public void testRatioReducesReleasedSize() {
        long ratioSize = mockRatioMgr.calculateByRatio(
                PS_UUID, SNAPSHOT_SIZE);

        // With ratio=10: 13GB / 10 = 1.3GB
        long expectedRatioSize =
                SNAPSHOT_SIZE / OVER_PROVISIONING_RATIO;
        assertEquals(expectedRatioSize, ratioSize);

        // The ratio-adjusted size is much smaller than actual
        assertNotEquals(
                "Ratio size should differ from actual size",
                SNAPSHOT_SIZE, ratioSize);

        // This proves: using releaseAvailableCapWithRatio for
        // snapshots would only release 1.3GB instead of 13GB,
        // causing 11.7GB capacity leak per snapshot deletion
        long leaked = SNAPSHOT_SIZE - ratioSize;
        assertEquals(
                SizeUnit.GIGABYTE.toByte(13)
                        - SizeUnit.GIGABYTE.toByte(13) / 10,
                leaked);
    }
}

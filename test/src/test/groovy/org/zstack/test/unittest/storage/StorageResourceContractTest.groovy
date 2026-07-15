package org.zstack.test.unittest.storage

import org.junit.Test
import org.zstack.header.storage.addon.StorageResource
import org.zstack.header.storage.snapshot.VolumeSnapshotStats
import org.zstack.header.volume.VolumeStats

class StorageResourceContractTest {
    @Test
    void storageResourceKeepsNullableFieldsAndPublicDescriptors() {
        Class sizeType = StorageResource.getDeclaredField("size").type
        Class actualSizeType = StorageResource.getDeclaredField("actualSize").type

        assert sizeType == Long.class && actualSizeType == Long.class :
                "StorageResource size fields must preserve nullable Long values: " +
                        "expectedSize=${Long.class.name} actualSize=${sizeType.name}, " +
                        "expectedActualSize=${Long.class.name} actualActualSize=${actualSizeType.name}"

        Map<String, Class> actualGetterTypes = [
                "StorageResource.getSize"               : StorageResource.getDeclaredMethod("getSize").returnType,
                "StorageResource.getResourceActualSize" : StorageResource.getDeclaredMethod("getResourceActualSize").returnType,
                "VolumeStats.getActualSize"             : VolumeStats.getDeclaredMethod("getActualSize").returnType,
                "VolumeSnapshotStats.getActualSize"     : VolumeSnapshotStats.getDeclaredMethod("getActualSize").returnType,
        ]
        Map<String, Class> expectedGetterTypes = [
                "StorageResource.getSize"               : Long.class,
                "StorageResource.getResourceActualSize" : Long.class,
                "VolumeStats.getActualSize"             : Long.class,
                "VolumeSnapshotStats.getActualSize"     : Long.TYPE,
        ]

        assert actualGetterTypes == expectedGetterTypes :
                "Storage stats public getter descriptors changed: expected=${expectedGetterTypes} actual=${actualGetterTypes}"

        assert StorageResource.getDeclaredMethod("setSize", Long.TYPE).returnType == Void.TYPE :
                "StorageResource.setSize(long) descriptor must return void"
        assert StorageResource.getDeclaredMethod("setActualSize", Long.class).returnType == Void.TYPE :
                "StorageResource.setActualSize(Long) descriptor must return void"
        assert StorageResource.getDeclaredMethod("setActualSize", Long.TYPE).returnType == Void.TYPE :
                "StorageResource.setActualSize(long) descriptor must return void"
        assert VolumeStats.getDeclaredConstructor(String.class, Long.class).parameterTypes.toList() ==
                [String.class, Long.class] : "VolumeStats(String, Long) constructor descriptor changed"
        assert VolumeStats.getDeclaredConstructor(String.class, Long.class, Long.class).parameterTypes.toList() ==
                [String.class, Long.class, Long.class] : "VolumeStats(String, Long, Long) constructor descriptor changed"
    }

    @Test
    void volumeStatsPreservesMissingSizesAsNull() {
        VolumeStats defaultStats = new VolumeStats()
        VolumeStats constructorStats = new VolumeStats("path", null, null)

        assert defaultStats.getSize() == null :
                "unset volume size must remain nullable: expected=null actual=${defaultStats.getSize()}"
        assert defaultStats.getActualSize() == null :
                "unset volume actualSize must remain nullable: expected=null actual=${defaultStats.getActualSize()}"
        assert constructorStats.getSize() == null :
                "boxed constructor size must preserve null: expected=null actual=${constructorStats.getSize()}"
        assert constructorStats.getActualSize() == null :
                "boxed constructor actualSize must preserve null: expected=null actual=${constructorStats.getActualSize()}"

        constructorStats.setActualSize((Long) null)
        assert constructorStats.getActualSize() == null :
                "boxed actualSize setter must preserve null: expected=null actual=${constructorStats.getActualSize()}"
    }

    @Test
    void snapshotStatsKeepsPrimitiveGetterNullFallback() {
        VolumeSnapshotStats snapshotStats = new VolumeSnapshotStats()

        assert snapshotStats.getResourceActualSize() == null :
                "unset snapshot resource actualSize must remain nullable: expected=null actual=${snapshotStats.getResourceActualSize()}"
        assert snapshotStats.getActualSize() == 0L :
                "snapshot primitive getter must map missing actualSize to zero: expected=0 actual=${snapshotStats.getActualSize()}"

        snapshotStats.setActualSize((Long) null)
        assert snapshotStats.getResourceActualSize() == null :
                "boxed snapshot setter must preserve null: expected=null actual=${snapshotStats.getResourceActualSize()}"
        assert snapshotStats.getActualSize() == 0L :
                "snapshot primitive getter must keep its null fallback: expected=0 actual=${snapshotStats.getActualSize()}"
    }
}

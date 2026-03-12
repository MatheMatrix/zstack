package org.zstack.header.vm.metadata;

import java.sql.Timestamp;

/**
 * 元数据注册时资源持久化后的扩展点。
 *
 * 各存储插件（如 LocalStorage）实现此接口，在主干代码 persist volume/snapshot 之后
 * 执行存储类型相关的额外持久化操作（如创建 LocalStorageResourceRefVO）。
 *
 * 主干注册代码只通过 getPrimaryStorageType() 匹配扩展点并调用回调，不感知具体存储类型。
 */
public interface VmMetadataResourcePersistExtensionPoint {
    /**
     * 判断本扩展是否处理指定存储类型
     */
    String getPrimaryStorageType();

    /**
     * volume persist 完成后回调。
     *
     * @param primaryStorageUuid 目标主存储 UUID
     * @param resourceUuid       volume UUID
     * @param resourceType       resource type simple name (e.g. "VolumeVO")
     * @param hostUuid           注册请求中指定的 host UUID（可能为 null）
     * @param size               volume size
     * @param now                当前时间戳
     */
    void afterVolumePersist(String primaryStorageUuid, String resourceUuid,
                            String resourceType, String hostUuid, long size, Timestamp now);

    /**
     * snapshot persist 完成后回调。
     *
     * @param primaryStorageUuid 目标主存储 UUID
     * @param resourceUuid       snapshot UUID
     * @param resourceType       resource type simple name (e.g. "VolumeSnapshotVO")
     * @param hostUuid           注册请求中指定的 host UUID（可能为 null）
     * @param size               snapshot size
     * @param now                当前时间戳
     */
    void afterSnapshotPersist(String primaryStorageUuid, String resourceUuid,
                              String resourceType, String hostUuid, long size, Timestamp now);
}

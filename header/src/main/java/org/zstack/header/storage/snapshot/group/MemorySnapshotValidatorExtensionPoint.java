package org.zstack.header.storage.snapshot.group;

import org.zstack.header.errorcode.ErrorCode;

/**
 * 内存快照验证扩展点
 *
 * <p>触发时机：创建内存快照前，验证 VM 是否符合条件</p>
 * <p>调用位置：VolumeSnapshotGroupCreator.validate()</p>
 *
 * <h3>使用场景：</h3>
 * <p>各模块通过此扩展点检查 VM 的外部设备或网络引用
 * 是否与内存快照兼容。</p>
 *
 * @since 5.0.0
 */
public interface MemorySnapshotValidatorExtensionPoint {
    default ErrorCode checkVmWhereMemorySnapshotExistExternalDevices(String VmInstanceUuid) {
        return null;
    }

    default ErrorCode checkL3IfReferencedByMemorySnapshot(String l3Uuid) {
        return null;
    }
}

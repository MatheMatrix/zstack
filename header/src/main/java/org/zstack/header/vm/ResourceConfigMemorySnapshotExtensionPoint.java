package org.zstack.header.vm;

import java.util.List;

/**
 * 内存快照资源配置归档扩展点
 *
 * <p>触发时机：创建内存快照时，归档与 VM 相关的资源配置</p>
 * <p>调用位置：MemorySnapshotManager.archiveResourceConfig()</p>
 *
 * <h3>使用场景：</h3>
 * <p>当 VM 执行内存快照时，各模块通过此扩展点提供需要归档的资源配置，
 * 以便恢复快照时可以同时恢复相关配置状态。</p>
 *
 * @since 5.0.0
 */
public interface ResourceConfigMemorySnapshotExtensionPoint {
    List<ArchiveResourceConfigBundle.ResourceConfigBundle> getNeedToArchiveResourceConfig(String resourceUuid);
}

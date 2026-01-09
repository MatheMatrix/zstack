package org.zstack.compute.vm.metadata;

public enum MetadataImpactLevel {
    /**
     * 配置变更：仅更新数据库中的元数据，VM 运行时不感知，适用于大多数场景
     */
    CONFIG,

    /**
     * 运行时变更：除了更新数据库，还需要通过消息通知 VM 实时更新元数据，适用于需要即时生效的场景
     */
    STORAGE,

    NONE
}

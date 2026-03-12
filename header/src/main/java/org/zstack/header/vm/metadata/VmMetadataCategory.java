package org.zstack.header.vm.metadata;

/**
 * 虚拟机元数据分类�?
 *
 * <p>用于区分元数据所属的 VM 类型，注册恢复时按不同分类执行不同的恢复逻辑�?/p>
 *
 * <ul>
 *   <li>{@link #REGULAR} �?普通云主机</li>
 *   <li>{@link #TEMPLATE} �?模板虚拟�?/li>
 *   <li>{@link #TEMPLATE_CACHE} �?模板虚拟机缓�?/li>
 * </ul>
 */
public enum VmMetadataCategory {
    REGULAR,
    TEMPLATE,
    TEMPLATE_CACHE
}

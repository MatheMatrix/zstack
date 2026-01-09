package org.zstack.header.vm;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 API 消息对虚拟机元数据的影响类型。
 *
 * <h3>opt-out 策略</h3>
 * <p>不标注时默认行为等同于 {@link Impact#CONFIG}。
 * 明确不影响元数据的 API 应标注 {@link Impact#NONE}。</p>
 *
 * <h3>vmUuid 解析</h3>
 * <p>不涉及 VM 的 API（如 APICreateZoneMsg）即使默认 CONFIG，
 * 也不会触发元数据更新——因为 {@link VmUuidFromApiResolver} 无法解析出 vmUuid，
 * 不会产生 {@link UpdateVmInstanceMetadataMsg}。</p>
 *
 * @see VmUuidFromApiResolver
 * @see UpdateVmInstanceMetadataMsg
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MetadataImpact {

    /**
     * 影响类型。
     */
    Impact value();

    /**
     * API 失败时是否也需要更新元数据。
     *
     * <p>默认 false：仅在 API 成功后触发元数据更新。
     * 设为 true 时，API 执行失败也会触发 markDirty。
     * 适用于 API 可能部分成功、需要同步最新状态的场景。</p>
     */
    boolean updateOnFailure() default false;

    /**
     * API 对虚拟机元数据的影响类型枚举。
     */
    enum Impact {
        /**
         * 不影响虚拟机元数据，明确跳过。
         *
         * <p>用于标注与 VM 无关或虽关联 VM 但不影响元数据内容的 API，
         * 如 APIQueryVmInstanceMsg、APIGetVmConsoleAddressMsg 等。</p>
         */
        NONE,

        /**
         * 影响虚拟机配置，触发元数据更新。
         *
         * <p>如修改 CPU/内存、增删 SystemTag/ResourceConfig 等。
         * 这是未标注 {@link MetadataImpact} 注解时的默认行为。</p>
         */
        CONFIG,

        /**
         * 影响存储结构，触发元数据更新。
         *
         * <p>如存储迁移、快照操作、删除云盘等涉及存储结构变更的 API。
         * 在 sblk 场景下会设置 pending_op=2 以标记存储结构变更。</p>
         */
        STORAGE
    }
}
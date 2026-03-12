package org.zstack.header.vm.metadata;

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
 * <p>每个 {@code @MetadataImpact}（除 {@link Impact#NONE}）必须通过 {@link #resolver()}
 * 指定 Spring bean name，用于从 API 消息中提取 vmUuid。
 * Interceptor 启动时通过 bean name 从 Spring 容器查找对应的
 * {@link VmUuidFromApiResolver} 实例。</p>
 *
 * <p>常用 resolver bean name：</p>
 * <ul>
 *   <li>{@code "DefaultVmUuidFromApiResolver"} — 用于实现了 VmInstanceMessage 的 API</li>
 *   <li>{@code "VolumeBasedVmUuidFromApiResolver"} — 从 volumeUuid 反查 vmUuid</li>
 *   <li>{@code "NicBasedVmUuidFromApiResolver"} — 从 vmNicUuid 反查 vmUuid</li>
 *   <li>{@code "SnapshotBasedVmUuidFromApiResolver"} — 从 snapshotUuid 反查 vmUuid</li>
 * </ul>
 *
 * <h3>updateOnFailure</h3>
 * <p>默认 API 失败时不触发元数据更新。若设为 true，则 API 失败后仍触发更新
 * （适用于有副作用的操作，如部分完成的批量操作）。</p>
 *
 * @see VmUuidFromApiResolver
 * @see UpdateVmInstanceMetadataMsg
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MetadataImpact {
    Impact value();

    /**
     * Spring bean name of the {@link VmUuidFromApiResolver} that extracts vmUuid(s)
     * from this API message. Must be specified for {@link Impact#CONFIG} and
     * {@link Impact#STORAGE}; ignored for {@link Impact#NONE}.
     *
     * <p>The bean must be registered in Spring XML. Interceptor looks it up at
     * startup via {@code ComponentLoader.getComponentByBeanName()}.</p>
     */
    String resolver() default "";

    boolean updateOnFailure() default false;

    enum Impact {
        NONE,
        CONFIG,
        STORAGE
    }
}
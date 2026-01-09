package org.zstack.header.vm;

import org.zstack.header.message.NeedJsonSchema;

/**
 * 虚拟机元数据相关 CanonicalEvent 定义。
 *
 * <p>通过 {@code EventFacade.fire()} 发布，供监控系统和巡检机制消费。</p>
 */
public class VmMetadataCanonicalEvents {

    /**
     * GC 放弃后的 stale 事件路径。
     *
     * <p>当 {@code UpdateVmInstanceMetadataGC} 超过最大重试次数后发布此事件，
     * {@code MetadataHealthCheckJob} 监听此事件将 VM 加入优先刷新队列。</p>
     */
    public static final String VM_METADATA_STALE_PATH = "/vm/metadata/stale";

    @NeedJsonSchema
    public static class MetadataStaleData {
        public String vmInstanceUuid;

        public MetadataStaleData() {
        }

        public MetadataStaleData(String vmInstanceUuid) {
            this.vmInstanceUuid = vmInstanceUuid;
        }
    }
}

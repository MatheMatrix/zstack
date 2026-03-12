package org.zstack.header.vm;

import org.zstack.header.message.NeedReplyMessage;

import java.util.List;

/**
 * 批量检查多个 VM 元数据 Header 状态的内部消息（健康巡检）。
 *
 * <p>由管理平面发送给主存储 handler，Agent 端仅读取 Header（不读 Slot），
 * 返回每个 VM 的 readStatus 和 PendingOp 信息。</p>
 *
 * <p>路由：{@code makeLocalServiceId} → 主存储 handler → Agent HTTP 调用</p>
 *
 * @see BatchCheckMetadataStatusReply
 * @see MetadataStatusResult
 */
public class BatchCheckMetadataStatusMsg extends NeedReplyMessage {

    private String primaryStorageUuid;

    private List<String> vmUuids;

    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    public List<String> getVmUuids() {
        return vmUuids;
    }

    public void setVmUuids(List<String> vmUuids) {
        this.vmUuids = vmUuids;
    }
}

package org.zstack.header.vm;

import org.zstack.header.message.NeedReplyMessage;

/**
 * 修复 sblk 元数据 Header 的内部消息。
 *
 * <p>由管理平面发送给主存储 handler，用于完成未完成的 Phase 3、
 * 清除 PendingOp、重建 Header 或触发全量刷写。</p>
 *
 * <p>路由：{@code makeLocalServiceId} → 主存储 handler → Agent HTTP 调用</p>
 *
 * @see BatchCheckMetadataStatusMsg
 */
public class RepairMetadataMsg extends NeedReplyMessage {

    private String vmUuid;

    private String primaryStorageUuid;

    /**
     * 修复动作。
     *
     * <p>可选值：{@code complete_phase3} / {@code clear_pending_op} /
     * {@code rebuild_header} / {@code full_refresh}</p>
     */
    private String repairAction;

    public String getVmUuid() {
        return vmUuid;
    }

    public void setVmUuid(String vmUuid) {
        this.vmUuid = vmUuid;
    }

    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    public String getRepairAction() {
        return repairAction;
    }

    public void setRepairAction(String repairAction) {
        this.repairAction = repairAction;
    }
}

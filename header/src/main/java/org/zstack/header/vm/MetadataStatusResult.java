package org.zstack.header.vm;

import java.io.Serializable;

/**
 * 单个 VM 的元数据 Header 状态结果（用于健康巡检）。
 *
 * @see BatchCheckMetadataStatusReply
 */
public class MetadataStatusResult implements Serializable {

    /**
     * 读取状态：OK / NEED_REPAIR / RECOVERED / DEGRADED /
     * STORAGE_CHANGE_INCOMPLETE / CORRUPTED
     */
    private String readStatus;

    /**
     * 可为 null。NEED_REPAIR/RECOVERED 时提示的修复动作
     * （如 "complete_phase3" / "rebuild_header" / "full_refresh"）。
     */
    private String repairAction;

    /**
     * 最后更新时间戳（epoch ms）。
     */
    private Long lastUpdateTime;

    /**
     * 当前 PendingOp 值（0/1/2）。
     */
    private Integer pendingOp;

    public String getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(String readStatus) {
        this.readStatus = readStatus;
    }

    public String getRepairAction() {
        return repairAction;
    }

    public void setRepairAction(String repairAction) {
        this.repairAction = repairAction;
    }

    public Long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public Integer getPendingOp() {
        return pendingOp;
    }

    public void setPendingOp(Integer pendingOp) {
        this.pendingOp = pendingOp;
    }
}

package org.zstack.header.vm.metadata;

import org.zstack.header.vm.VmInstanceEO;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * 元数据指纹：记录每台 VM 上次成功刷写元数据时的完整快照。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>metadataSnapshot</b>：完整元数据 JSON（确定性序列化），
 *       由 {@code MetadataContentDriftDetector} 每 6 小时低频对比，
 *       发现漂移则触发 markDirty 重新刷写。</li>
 *   <li><b>lastFlushFailed</b>：Poller 重试耗尽时置 true（C-SR-05），
 *       仅由 {@code MetadataStaleRecoveryTask} 重置为 false（C-02B-8）。</li>
 *   <li><b>staleRecoveryCount</b>：熔断计数器，{@code MetadataStaleRecoveryTask} 每次
 *       重入队递增，达到上限（默认 10 × 5 小时）后停止自动恢复。
 *       管理员可通过 {@code APIUpdateVmMetadataMsg} 手动重置为 0。</li>
 *   <li><b>vmInstanceUuid 作 PK</b>：一台 VM 最多一行，
 *       FK CASCADE 保证 VM 物理删除时自动清理。</li>
 * </ul>
 */
@Entity
@Table(name = "VmMetadataFingerprintVO")
public class VmMetadataFingerprintVO {

    @Id
    @Column
    @ForeignKey(parentEntityClass = VmInstanceEO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String vmInstanceUuid;

    @Column
    private Timestamp lastFlushTime;

    @Column
    private boolean lastFlushFailed;

    @Column
    private int staleRecoveryCount;

    @Column
    @Lob
    private String metadataSnapshot;

    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public Timestamp getLastFlushTime() {
        return lastFlushTime;
    }

    public void setLastFlushTime(Timestamp lastFlushTime) {
        this.lastFlushTime = lastFlushTime;
    }

    public boolean isLastFlushFailed() {
        return lastFlushFailed;
    }

    public void setLastFlushFailed(boolean lastFlushFailed) {
        this.lastFlushFailed = lastFlushFailed;
    }

    public int getStaleRecoveryCount() {
        return staleRecoveryCount;
    }

    public void setStaleRecoveryCount(int staleRecoveryCount) {
        this.staleRecoveryCount = staleRecoveryCount;
    }

    public String getMetadataSnapshot() {
        return metadataSnapshot;
    }

    public void setMetadataSnapshot(String metadataSnapshot) {
        this.metadataSnapshot = metadataSnapshot;
    }
}

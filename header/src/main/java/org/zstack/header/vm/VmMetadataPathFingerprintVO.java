package org.zstack.header.vm;

import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * 路径指纹：记录每个 VM 上次成功刷写元数据时的存储拓扑路径快照。
 *
 * <h3>设计要点（Part 02b §8.2.3）</h3>
 * <ul>
 *   <li><b>pathSnapshot</b>：JSON 格式的 volumes/snapshots installPath 列表，
 *       按 uuid ASC 排序保证确定性，用于纯 DB 侧路径漂移检测（零存储 I/O）。</li>
 *   <li><b>lastFlushFailed</b>：Poller 重试耗尽时置 true（C-SR-05），
 *       仅由 {@code MetadataStaleRecoveryTask} 重置为 false（C-02B-8）。</li>
 *   <li><b>staleRecoveryCount</b>：熔断计数器，{@code MetadataStaleRecoveryTask} 每次
 *       重入队递增，达到上限（默认 10 ≈ 5 小时）后停止自动恢复。
 *       管理员可通过 {@code APIUpdateVmMetadataMsg} 手动重置为 0。</li>
 *   <li><b>vmInstanceUuid 做 PK</b>：一个 VM 最多一行。
 *       FK CASCADE 保证 VM 物理删除时自动清理。</li>
 * </ul>
 */
@Entity
@Table(name = "VmMetadataPathFingerprintVO")
public class VmMetadataPathFingerprintVO {

    @Id
    @Column
    @ForeignKey(parentEntityClass = VmInstanceEO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String vmInstanceUuid;

    @Column
    @Lob
    private String pathSnapshot;

    @Column
    private Timestamp lastFlushTime;

    @Column
    private boolean lastFlushFailed;

    @Column
    private int staleRecoveryCount;

    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public String getPathSnapshot() {
        return pathSnapshot;
    }

    public void setPathSnapshot(String pathSnapshot) {
        this.pathSnapshot = pathSnapshot;
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
}

package org.zstack.header.vm;

import org.zstack.header.managementnode.ManagementNodeVO;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * 记录 VM 元数据的"脏标记"，表示该 VM 的元数据需要写入主存储。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>vmInstanceUuid 做主键</b>：一个 VM 最多一行，天然去重。
 *       100 个 API 只产生 1 行，不是 100 行。</li>
 *   <li><b>managementNodeUuid FK SET_NULL</b>：MN 宕机后 DB 约束自动释放认领，
 *       无需额外孤儿扫描。</li>
 *   <li><b>vmInstanceUuid FK CASCADE</b>：VM 销毁时自动删除脏标记，无残留。</li>
 *   <li><b>dirtyVersion</b>：每次 markDirty +1，刷写前快照 version，
 *       成功后比较——检测刷写期间是否有新变更。语义比时间戳比较更明确，无精度问题。</li>
 *   <li><b>nextRetryTime</b>：退避控制，失败后不立刻重试，等到下次重试时间。</li>
 * </ul>
 *
 * <h3>行语义</h3>
 * <ul>
 *   <li>行存在 = VM 元数据是脏的（需要刷写）</li>
 *   <li>行不存在 = VM 元数据已是最新（或 VM 不存在）</li>
 *   <li>managementNodeUuid != null = 该行已被某个 MN 认领，正在处理</li>
 *   <li>managementNodeUuid == null = 该行未被认领，可被 Poller 或 triggerFlush 认领</li>
 * </ul>
 */
@Entity
@Table(name = "VmMetadataDirtyVO")
public class VmMetadataDirtyVO {

    @Id
    @Column
    @ForeignKey(parentEntityClass = VmInstanceEO.class, onDeleteAction = ReferenceOption.CASCADE)
    private String vmInstanceUuid;

    @Column
    @ForeignKey(parentEntityClass = ManagementNodeVO.class, onDeleteAction = ReferenceOption.SET_NULL)
    private String managementNodeUuid;

    @Column
    private long dirtyVersion;

    @Column
    private boolean storageStructureChange;

    @Column
    private int retryCount;

    @Column
    private Timestamp nextRetryTime;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public String getManagementNodeUuid() {
        return managementNodeUuid;
    }

    public void setManagementNodeUuid(String managementNodeUuid) {
        this.managementNodeUuid = managementNodeUuid;
    }

    public long getDirtyVersion() {
        return dirtyVersion;
    }

    public void setDirtyVersion(long dirtyVersion) {
        this.dirtyVersion = dirtyVersion;
    }

    public boolean isStorageStructureChange() {
        return storageStructureChange;
    }

    public void setStorageStructureChange(boolean storageStructureChange) {
        this.storageStructureChange = storageStructureChange;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Timestamp getNextRetryTime() {
        return nextRetryTime;
    }

    public void setNextRetryTime(Timestamp nextRetryTime) {
        this.nextRetryTime = nextRetryTime;
    }

    public Timestamp getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Timestamp createDate) {
        this.createDate = createDate;
    }

    public Timestamp getLastOpDate() {
        return lastOpDate;
    }

    public void setLastOpDate(Timestamp lastOpDate) {
        this.lastOpDate = lastOpDate;
    }
}

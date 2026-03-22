package org.zstack.header.vm.metadata;

import org.zstack.header.vm.VmInstanceEO;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table
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

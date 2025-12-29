package org.zstack.header.vm;

import org.zstack.header.vo.BaseResource;
import org.zstack.header.vo.EntityGraph;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table
@EntityGraph(
    friends = {
        @EntityGraph.Neighbour(type = VmInstanceVO.class, myField = "vmInstanceUuid", targetField = "uuid")
    }
)
public class VmGpuPciMappingVO extends BaseResource {
    @Column
    @ForeignKey(parentEntityClass = VmInstanceVO.class, parentKey = "uuid", onDeleteAction = ReferenceOption.CASCADE)
    private String vmInstanceUuid;

    @Column
    private String vmPciAddress;

    @Column
    private String hostPciAddress;

    @Column
    private String gpuSerial;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = new Timestamp(System.currentTimeMillis());
    }

    public String getVmInstanceUuid() {
        return vmInstanceUuid;
    }

    public void setVmInstanceUuid(String vmInstanceUuid) {
        this.vmInstanceUuid = vmInstanceUuid;
    }

    public String getVmPciAddress() {
        return vmPciAddress;
    }

    public void setVmPciAddress(String vmPciAddress) {
        this.vmPciAddress = vmPciAddress;
    }

    public String getHostPciAddress() {
        return hostPciAddress;
    }

    public void setHostPciAddress(String hostPciAddress) {
        this.hostPciAddress = hostPciAddress;
    }

    public String getGpuSerial() {
        return gpuSerial;
    }

    public void setGpuSerial(String gpuSerial) {
        this.gpuSerial = gpuSerial;
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
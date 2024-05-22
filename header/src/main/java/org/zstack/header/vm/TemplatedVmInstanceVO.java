package org.zstack.header.vm;

import org.zstack.header.identity.AccountVO;
import org.zstack.header.identity.OwnedByAccount;
import org.zstack.header.vo.*;
import org.zstack.header.vo.EntityGraph;
import org.zstack.header.vo.ForeignKey;
import org.zstack.header.vo.ForeignKey.ReferenceOption;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table
@BaseResource
@EntityGraph(
        parents = {
                @EntityGraph.Neighbour(type = VmInstanceVO.class, myField = "uuid", targetField = "uuid")
        }
)
public class TemplatedVmInstanceVO extends ResourceVO implements OwnedByAccount {
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uuid")
    @NoView
    private VmInstanceVO vm;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @Column
    @ForeignKey(parentEntityClass = AccountVO.class, parentKey = "uuid", onDeleteAction = ReferenceOption.CASCADE)
    private String accountUuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public VmInstanceVO getVm() {
        return vm;
    }

    public void setVm(VmInstanceVO vm) {
        this.vm = vm;
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

    public String getAccountUuid() {
        return accountUuid;
    }

    public void setAccountUuid(String accountUuid) {
        this.accountUuid = accountUuid;
    }
}

package org.zstack.identity.imports.entity;

import org.zstack.header.identity.AccountVO;
import org.zstack.header.tag.AutoDeleteTag;
import org.zstack.header.vo.ForeignKey;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.sql.Timestamp;

/**
 * Created by Wenhao.Zhang on 2024/05/31
 */
@Entity
@Table
@AutoDeleteTag
public class ImportAccountRefVO {
    @Id
    @Column
    private String uuid;

    @Column
    private String keyFromImportSource;

    @Column
    @ForeignKey(parentEntityClass = AccountImportSourceVO.class, parentKey = "uuid", onDeleteAction = ForeignKey.ReferenceOption.CASCADE)
    private String importSourceUuid;

    @Column
    @ForeignKey(parentEntityClass = AccountVO.class, parentKey = "uuid", onDeleteAction = ForeignKey.ReferenceOption.CASCADE)
    private String accountUuid;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getKeyFromImportSource() {
        return keyFromImportSource;
    }

    public void setKeyFromImportSource(String keyFromImportSource) {
        this.keyFromImportSource = keyFromImportSource;
    }

    public String getImportSourceUuid() {
        return importSourceUuid;
    }

    public void setImportSourceUuid(String importSourceUuid) {
        this.importSourceUuid = importSourceUuid;
    }

    public String getAccountUuid() {
        return accountUuid;
    }

    public void setAccountUuid(String accountUuid) {
        this.accountUuid = accountUuid;
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

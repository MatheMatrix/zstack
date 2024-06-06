package org.zstack.identity.imports.entity;

import org.zstack.header.tag.AutoDeleteTag;
import org.zstack.header.vo.ResourceVO;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.sql.Timestamp;

/**
 * Extract from LdapServerVO
 *
 * Created by Wenhao.Zhang on 2024/05/31
 */
@Entity
@Table
@AutoDeleteTag
public class AccountImportSourceVO extends ResourceVO {
    @Column
    private String description;

    /**
     * ex: "LDAP"
     */
    @Column
    private String type;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

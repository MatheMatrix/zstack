package org.zstack.identity.imports.entity;

import org.zstack.header.configuration.PythonClassInventory;
import org.zstack.header.search.Inventory;
import org.zstack.utils.CollectionUtils;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

@Inventory(mappingVOClass = ImportAccountRefVO.class)
@PythonClassInventory
public class ImportAccountRefInventory {
    private String uuid;
    private String keyFromImportSource;
    private String importSourceUuid;
    private String accountUuid;
    private Timestamp createDate;
    private Timestamp lastOpDate;

    public static ImportAccountRefInventory valueOf(ImportAccountRefVO vo) {
        ImportAccountRefInventory inv = new ImportAccountRefInventory();
        inv.setUuid(vo.getUuid());
        inv.setImportSourceUuid(vo.getImportSourceUuid());
        inv.setKeyFromImportSource(vo.getKeyFromImportSource());
        inv.setAccountUuid(vo.getAccountUuid());
        inv.setCreateDate(vo.getCreateDate());
        inv.setLastOpDate(vo.getLastOpDate());
        return inv;
    }

    public static List<ImportAccountRefInventory> valueOf(Collection<ImportAccountRefVO> vos) {
        return CollectionUtils.transform(vos, ImportAccountRefInventory::valueOf);
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

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getAccountUuid() {
        return accountUuid;
    }

    public void setAccountUuid(String accountUuid) {
        this.accountUuid = accountUuid;
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
}

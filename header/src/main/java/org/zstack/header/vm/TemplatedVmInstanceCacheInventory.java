package org.zstack.header.vm;

import org.zstack.header.configuration.PythonClassInventory;
import org.zstack.header.search.Inventory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@PythonClassInventory
@Inventory(mappingVOClass = TemplatedVmInstanceVO.class)
public class TemplatedVmInstanceCacheInventory implements Serializable {
    private long id;
    private String templatedVmInstanceUuid;
    private String cacheVmInstanceUuid;

    public static TemplatedVmInstanceCacheInventory valueOf(TemplatedVmInstanceCacheVO vo) {
        TemplatedVmInstanceCacheInventory inv = new TemplatedVmInstanceCacheInventory();
        inv.setId(vo.getId());
        inv.setTemplatedVmInstanceUuid(vo.getTemplatedVmInstanceUuid());
        inv.setCacheVmInstanceUuid(vo.getCacheVmInstanceUuid());
        return inv;
    }

    public static List<TemplatedVmInstanceCacheInventory> valueOf(Collection<TemplatedVmInstanceCacheVO> vos) {
        List<TemplatedVmInstanceCacheInventory> invs = new ArrayList<TemplatedVmInstanceCacheInventory>();
        for (TemplatedVmInstanceCacheVO vo : vos) {
            invs.add(valueOf(vo));
        }
        return invs;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTemplatedVmInstanceUuid() {
        return templatedVmInstanceUuid;
    }

    public void setTemplatedVmInstanceUuid(String templatedVmInstanceUuid) {
        this.templatedVmInstanceUuid = templatedVmInstanceUuid;
    }

    public String getCacheVmInstanceUuid() {
        return cacheVmInstanceUuid;
    }

    public void setCacheVmInstanceUuid(String cacheVmInstanceUuid) {
        this.cacheVmInstanceUuid = cacheVmInstanceUuid;
    }
}

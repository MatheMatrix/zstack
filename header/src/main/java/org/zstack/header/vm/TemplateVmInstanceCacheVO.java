package org.zstack.header.vm;

public class TemplateVmInstanceCacheVO {
    private String templateVmInstanceUuid;
    private String cacheVmInstanceUuid;

    public String getTemplateVmInstanceUuid() {
        return templateVmInstanceUuid;
    }

    public void setTemplateVmInstanceUuid(String templateVmInstanceUuid) {
        this.templateVmInstanceUuid = templateVmInstanceUuid;
    }

    public String getCacheVmInstanceUuid() {
        return cacheVmInstanceUuid;
    }

    public void setCacheVmInstanceUuid(String cacheVmInstanceUuid) {
        this.cacheVmInstanceUuid = cacheVmInstanceUuid;
    }
}

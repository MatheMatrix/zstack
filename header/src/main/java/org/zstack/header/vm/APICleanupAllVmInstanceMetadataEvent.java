package org.zstack.header.vm;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

import java.util.List;

@RestResponse(fieldsTo = {"all"})
public class APICleanupAllVmInstanceMetadataEvent extends APIEvent {
    private Integer totalCleaned;
    private Integer totalFailed;
    private List<String> failedPrimaryStorageUuids;

    public APICleanupAllVmInstanceMetadataEvent() {
        super(null);
    }

    public APICleanupAllVmInstanceMetadataEvent(String apiId) {
        super(apiId);
    }

    public Integer getTotalCleaned() {
        return totalCleaned;
    }

    public void setTotalCleaned(Integer totalCleaned) {
        this.totalCleaned = totalCleaned;
    }

    public Integer getTotalFailed() {
        return totalFailed;
    }

    public void setTotalFailed(Integer totalFailed) {
        this.totalFailed = totalFailed;
    }

    public List<String> getFailedPrimaryStorageUuids() {
        return failedPrimaryStorageUuids;
    }

    public void setFailedPrimaryStorageUuids(List<String> failedPrimaryStorageUuids) {
        this.failedPrimaryStorageUuids = failedPrimaryStorageUuids;
    }

    public static APICleanupAllVmInstanceMetadataEvent __example__() {
        APICleanupAllVmInstanceMetadataEvent evt = new APICleanupAllVmInstanceMetadataEvent();
        evt.totalCleaned = 42;
        evt.totalFailed = 0;
        evt.failedPrimaryStorageUuids = java.util.Collections.emptyList();
        return evt;
    }
}

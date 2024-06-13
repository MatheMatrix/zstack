package org.zstack.header.storage.snapshot.group;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.Action;
import org.zstack.header.message.*;
import org.zstack.header.other.APIAuditor;
import org.zstack.header.other.APIMultiAuditor;
import org.zstack.header.rest.APINoSee;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.storage.snapshot.SnapshotBackendOperation;
import org.zstack.header.storage.snapshot.VolumeSnapshotConstant;
import org.zstack.header.vm.VmInstanceVO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Action(category = VolumeSnapshotConstant.ACTION_CATEGORY)
@RestRequest(
        path = "/volume-snapshots/group/{uuid}",
        method = HttpMethod.DELETE,
        responseClass = APIBatchDeleteVolumeSnapshotGroupEvent.class
)
@DefaultTimeout(timeunit = TimeUnit.HOURS, value = 3)
public class APIBatchDeleteVolumeSnapshotGroupMsg extends APIDeleteMessage implements VolumeSnapshotGroupMessage {
    @APIParam(resourceType = VolumeSnapshotGroupVO.class, successIfResourceNotExisting = true)
    private List<String> uuids;

    @APINoSee
    private List<VolumeSnapshotGroupVO> volumeSnapshotGroups;

    @Override
    public SnapshotBackendOperation getBackendOperation() {
        return SnapshotBackendOperation.FILE_DELETION;
    }

    @Override
    public List<String> getGroupUuids() {
        return uuids;
    }

    @Override
    public String getGroupUuid() {
        return uuids.isEmpty() ? null : uuids.get(0);
    }

    public List<String> getUuids() {
        return uuids;
    }

    public void setUuids(List<String> uuids) {
        this.uuids = uuids;
    }

    public List<VolumeSnapshotGroupVO> getVolumeSnapshotGroups() {
        return volumeSnapshotGroups;
    }

    public void setVolumeSnapshotGroups(List<VolumeSnapshotGroupVO> volumeSnapshotGroups) {
        this.volumeSnapshotGroups = volumeSnapshotGroups;
    }

    public static APIBatchDeleteVolumeSnapshotGroupMsg __example__() {
        APIBatchDeleteVolumeSnapshotGroupMsg result = new APIBatchDeleteVolumeSnapshotGroupMsg();
        result.uuids = Collections.singletonList(uuid());
        return result;
    }
}

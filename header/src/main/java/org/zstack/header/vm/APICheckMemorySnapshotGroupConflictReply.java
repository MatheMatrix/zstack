package org.zstack.header.vm;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

@RestResponse(fieldsTo = {"all"})
public class APICheckMemorySnapshotGroupConflictReply extends APIReply {
    private List<NetworkConflict> networksConflict;

    public APICheckMemorySnapshotGroupConflictReply() {
    }

    public List<NetworkConflict> getNetworksConflict() {
        return networksConflict;
    }

    public void setNetworksConflict(List<NetworkConflict> networksConflict) {
        this.networksConflict = networksConflict;
    }

    public static APICheckMemorySnapshotGroupConflictReply __example__() {
        APICheckMemorySnapshotGroupConflictReply reply = new APICheckMemorySnapshotGroupConflictReply();
        NetworkConflict inv = new NetworkConflict();
        inv.ip = "127.0.0.1";
        inv.mac = "00:16:3e:00:00:01";
        inv.vmInstanceUuid = uuid(VolumeSnapshotGroupVO.class);
        inv.vmInstanceName = "vmInstanceName";
        VmNicInventory nic = new VmNicInventory();
        nic.setVmInstanceUuid(uuid(VolumeSnapshotGroupVO.class));
        nic.setCreateDate(Timestamp.valueOf("2025-08-05 11:44:59"));
        nic.setLastOpDate(Timestamp.valueOf("2025-08-05 11:44:59"));
        nic.setDeviceId(0);
        nic.setGateway("192.168.1.1");
        nic.setIp("127.0.0.1");
        nic.setL3NetworkUuid(uuid(VolumeSnapshotGroupVO.class));
        nic.setNetmask("255.255.255.0");
        nic.setMac("00:16:3e:00:00:01");
        nic.setHypervisorType("KVM");
        nic.setUsedIpUuid(uuid(VolumeSnapshotGroupVO.class));
        nic.setUuid(uuid(VolumeSnapshotGroupVO.class));
        inv.vmNicInventory = nic;
        reply.setNetworksConflict(Collections.singletonList(inv));
        return reply;
    }
}

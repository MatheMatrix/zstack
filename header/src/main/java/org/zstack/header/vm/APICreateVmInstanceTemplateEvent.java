package org.zstack.header.vm;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;
import org.zstack.header.volume.VolumeTemplateInventory;
import org.zstack.header.volume.VolumeType;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

@RestResponse(fieldsTo = {"inventory", "volumeTemplates"})
public class APICreateVmInstanceTemplateEvent extends APIEvent {

    VmInstanceTemplateInventory inventory;

    List<VolumeTemplateInventory> volumeTemplates;

    public APICreateVmInstanceTemplateEvent() {
        super(null);
    }

    public APICreateVmInstanceTemplateEvent(String apiId) {
        super(apiId);
    }

    public VmInstanceTemplateInventory getInventory() {
        return inventory;
    }

    public void setInventory(VmInstanceTemplateInventory inventory) {
        this.inventory = inventory;
    }

    public List<VolumeTemplateInventory> getVolumeTemplates() {
        return volumeTemplates;
    }

    public void setVolumeTemplates(List<VolumeTemplateInventory> volumeTemplates) {
        this.volumeTemplates = volumeTemplates;
    }

    public static APICreateVmInstanceTemplateEvent __example__() {
        APICreateVmInstanceTemplateEvent event = new APICreateVmInstanceTemplateEvent();

        VmInstanceTemplateInventory vmTemplate = new VmInstanceTemplateInventory();
        vmTemplate.setUuid(uuid());
        vmTemplate.setName("test-vm-template");
        vmTemplate.setVmInstanceUuid(uuid());
        vmTemplate.setCreateDate(new Timestamp(org.zstack.header.message.DocUtils.date));
        vmTemplate.setLastOpDate(new Timestamp(org.zstack.header.message.DocUtils.date));

        VolumeTemplateInventory volumeTemplate = new VolumeTemplateInventory();
        volumeTemplate.setUuid(uuid());
        volumeTemplate.setVolumeUuid(uuid());
        volumeTemplate.setOriginalType(VolumeType.Root.toString());
        volumeTemplate.setCreateDate(new Timestamp(org.zstack.header.message.DocUtils.date));
        volumeTemplate.setLastOpDate(new Timestamp(org.zstack.header.message.DocUtils.date));

        event.setInventory(vmTemplate);
        event.setVolumeTemplates(Collections.singletonList(volumeTemplate));
        return event;
    }
}

package org.zstack.header.image;

import org.zstack.header.message.MessageReply;

public class CreateTemplateFromTemplateVolumeReply extends MessageReply {
    private ImageInventory inventory;
    private String locateHostUuid;
    private String locatePsUuid;
    private long imageCacheId;

    public ImageInventory getInventory() {
        return inventory;
    }

    public void setInventory(ImageInventory inventory) {
        this.inventory = inventory;
    }

    public String getLocateHostUuid() {
        return locateHostUuid;
    }

    public void setLocateHostUuid(String locateHostUuid) {
        this.locateHostUuid = locateHostUuid;
    }

    public String getLocatePsUuid() {
        return locatePsUuid;
    }

    public void setLocatePsUuid(String locatePsUuid) {
        this.locatePsUuid = locatePsUuid;
    }

    public long getImageCacheId() {
        return imageCacheId;
    }

    public void setImageCacheId(long imageCacheId) {
        this.imageCacheId = imageCacheId;
    }
}

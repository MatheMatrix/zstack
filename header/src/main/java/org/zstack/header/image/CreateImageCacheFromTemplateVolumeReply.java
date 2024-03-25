package org.zstack.header.image;

import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.primary.ImageCacheVolumeRefInventory;

public class CreateImageCacheFromTemplateVolumeReply extends MessageReply {
    private String locateHostUuid;
    private String locatePsUuid;
    private ImageInventory inventory;
    private ImageCacheVolumeRefInventory imageCacheVolumeRefInventory;

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

    public ImageCacheVolumeRefInventory getImageCacheVolumeRefInventory() {
        return imageCacheVolumeRefInventory;
    }

    public void setImageCacheVolumeRefInventory(ImageCacheVolumeRefInventory imageCacheVolumeRefInventory) {
        this.imageCacheVolumeRefInventory = imageCacheVolumeRefInventory;
    }
}

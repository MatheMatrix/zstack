package org.zstack.header.storage.primary;

import org.zstack.header.image.ImageInventory;

public class InstantiateVolumeFromImageCacheOnPrimaryStorageMsg extends InstantiateVolumeOnPrimaryStorageMsg implements PrimaryStorageMessage {
    private long size;
    private String hostUuid;
    private ImageInventory image;
    private ImageCacheInventory imageCache;

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public ImageInventory getImage() {
        return image;
    }

    public void setImage(ImageInventory image) {
        this.image = image;
    }

    public ImageCacheInventory getImageCache() {
        return imageCache;
    }

    public void setImageCache(ImageCacheInventory imageCache) {
        this.imageCache = imageCache;
    }
}

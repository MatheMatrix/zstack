package org.zstack.header.storage.primary;

import org.zstack.header.message.MessageReply;

/**
 * Created by MaJin on 2020/9/14.
 */
public class CreateImageCacheFromVolumeOnPrimaryStorageReply extends MessageReply {
    private String locateHostUuid;
    private long actualSize;
    private long imageCacheId;
    private boolean created = false;

    public String getLocateHostUuid() {
        return locateHostUuid;
    }

    public void setLocateHostUuid(String locateHostUuid) {
        this.locateHostUuid = locateHostUuid;
    }

    public long getActualSize() {
        return actualSize;
    }

    public void setActualSize(long actualSize) {
        this.actualSize = actualSize;
    }

    public long getImageCacheId() {
        return imageCacheId;
    }

    public void setImageCacheId(long imageCacheId) {
        this.imageCacheId = imageCacheId;
    }

    public boolean isCreated() {
        return created;
    }

    public void setCreated(boolean created) {
        this.created = created;
    }
}

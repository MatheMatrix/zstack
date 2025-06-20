package org.zstack.sdk;

import org.zstack.sdk.ImageStatus;
import org.zstack.sdk.ImageState;
import org.zstack.sdk.ImagePlatform;
import org.zstack.sdk.ImageMediaType;

public class ImageGroupRefInventory  {

    public java.lang.String imageUuid;
    public void setImageUuid(java.lang.String imageUuid) {
        this.imageUuid = imageUuid;
    }
    public java.lang.String getImageUuid() {
        return this.imageUuid;
    }

    public java.lang.String imageGroupUuid;
    public void setImageGroupUuid(java.lang.String imageGroupUuid) {
        this.imageGroupUuid = imageGroupUuid;
    }
    public java.lang.String getImageGroupUuid() {
        return this.imageGroupUuid;
    }

    public java.lang.String name;
    public void setName(java.lang.String name) {
        this.name = name;
    }
    public java.lang.String getName() {
        return this.name;
    }

    public java.lang.String description;
    public void setDescription(java.lang.String description) {
        this.description = description;
    }
    public java.lang.String getDescription() {
        return this.description;
    }

    public ImageStatus status;
    public void setStatus(ImageStatus status) {
        this.status = status;
    }
    public ImageStatus getStatus() {
        return this.status;
    }

    public ImageState state;
    public void setState(ImageState state) {
        this.state = state;
    }
    public ImageState getState() {
        return this.state;
    }

    public long size;
    public void setSize(long size) {
        this.size = size;
    }
    public long getSize() {
        return this.size;
    }

    public long actualSize;
    public void setActualSize(long actualSize) {
        this.actualSize = actualSize;
    }
    public long getActualSize() {
        return this.actualSize;
    }

    public java.lang.String md5Sum;
    public void setMd5Sum(java.lang.String md5Sum) {
        this.md5Sum = md5Sum;
    }
    public java.lang.String getMd5Sum() {
        return this.md5Sum;
    }

    public ImagePlatform platform;
    public void setPlatform(ImagePlatform platform) {
        this.platform = platform;
    }
    public ImagePlatform getPlatform() {
        return this.platform;
    }

    public java.lang.String type;
    public void setType(java.lang.String type) {
        this.type = type;
    }
    public java.lang.String getType() {
        return this.type;
    }

    public java.lang.String format;
    public void setFormat(java.lang.String format) {
        this.format = format;
    }
    public java.lang.String getFormat() {
        return this.format;
    }

    public java.lang.String url;
    public void setUrl(java.lang.String url) {
        this.url = url;
    }
    public java.lang.String getUrl() {
        return this.url;
    }

    public java.lang.Boolean system;
    public void setSystem(java.lang.Boolean system) {
        this.system = system;
    }
    public java.lang.Boolean getSystem() {
        return this.system;
    }

    public ImageMediaType mediaType;
    public void setMediaType(ImageMediaType mediaType) {
        this.mediaType = mediaType;
    }
    public ImageMediaType getMediaType() {
        return this.mediaType;
    }

    public java.sql.Timestamp createDate;
    public void setCreateDate(java.sql.Timestamp createDate) {
        this.createDate = createDate;
    }
    public java.sql.Timestamp getCreateDate() {
        return this.createDate;
    }

    public java.sql.Timestamp lastOpDate;
    public void setLastOpDate(java.sql.Timestamp lastOpDate) {
        this.lastOpDate = lastOpDate;
    }
    public java.sql.Timestamp getLastOpDate() {
        return this.lastOpDate;
    }

    public java.lang.String guestOsType;
    public void setGuestOsType(java.lang.String guestOsType) {
        this.guestOsType = guestOsType;
    }
    public java.lang.String getGuestOsType() {
        return this.guestOsType;
    }

    public java.lang.String architecture;
    public void setArchitecture(java.lang.String architecture) {
        this.architecture = architecture;
    }
    public java.lang.String getArchitecture() {
        return this.architecture;
    }

    public java.lang.Boolean virtio;
    public void setVirtio(java.lang.Boolean virtio) {
        this.virtio = virtio;
    }
    public java.lang.Boolean getVirtio() {
        return this.virtio;
    }

}

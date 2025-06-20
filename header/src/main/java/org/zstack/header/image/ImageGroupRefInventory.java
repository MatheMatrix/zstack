package org.zstack.header.image;

import org.zstack.header.configuration.PythonClassInventory;
import org.zstack.header.search.Inventory;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@PythonClassInventory
@Inventory(mappingVOClass = ImageGroupRefVO.class, collectionValueOfMethod = "valueOf1")
public class ImageGroupRefInventory implements Serializable {
    private String imageUuid;
    private String imageGroupUuid;
    private String name;
    private String description;
    private ImageStatus status;
    private ImageState state;
    private long size;
    private long actualSize;
    private String md5Sum;
    private ImagePlatform platform;
    private String type;
    private String format;
    private String url;
    private Boolean system;
    private ImageConstant.ImageMediaType mediaType;
    private Timestamp createDate;
    private Timestamp lastOpDate;
    private String guestOsType;
    private String architecture;
    private Boolean virtio;

    protected ImageGroupRefInventory(ImageGroupRefVO vo) {
        this.setImageUuid(vo.getImageUuid());
        this.setImageGroupUuid(vo.getImageGroupUuid());
        this.setName(vo.getName());
        this.setDescription(vo.getDescription());
        this.setStatus(vo.getStatus());
        this.setState(vo.getState());
        this.setSize(vo.getSize());
        this.setActualSize(vo.getActualSize());
        this.setMd5Sum(vo.getMd5Sum());
        this.setPlatform(vo.getPlatform());
        this.setType(vo.getType());
        this.setFormat(vo.getFormat());
        this.setUrl(vo.getUrl());
        this.setSystem(vo.isSystem());
        this.setMediaType(vo.getMediaType());
        this.setCreateDate(vo.getCreateDate());
        this.setLastOpDate(vo.getLastOpDate());
        this.setGuestOsType(vo.getGuestOsType());
        this.setArchitecture(vo.getArchitecture());
        this.setVirtio(vo.getVirtio());
    }

    public static ImageGroupRefInventory valueOf(ImageGroupRefVO vo) {
        return new ImageGroupRefInventory(vo);
    }

    public static List<ImageGroupRefInventory> valueOf1(Collection<ImageGroupRefVO> vos) {
        List<ImageGroupRefInventory> invs = new ArrayList<ImageGroupRefInventory>(vos.size());
        for (ImageGroupRefVO vo : vos) {
            invs.add(ImageGroupRefInventory.valueOf(vo));
        }
        return invs;
    }

    public String getImageUuid() {
        return imageUuid;
    }

    public void setImageUuid(String imageUuid) {
        this.imageUuid = imageUuid;
    }

    public String getImageGroupUuid() {
        return imageGroupUuid;
    }

    public void setImageGroupUuid(String imageGroupUuid) {
        this.imageGroupUuid = imageGroupUuid;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ImageStatus getStatus() {
        return status;
    }

    public void setStatus(ImageStatus status) {
        this.status = status;
    }

    public ImageState getState() {
        return state;
    }

    public void setState(ImageState state) {
        this.state = state;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getActualSize() {
        return actualSize;
    }

    public void setActualSize(long actualSize) {
        this.actualSize = actualSize;
    }

    public String getMd5Sum() {
        return md5Sum;
    }

    public void setMd5Sum(String md5Sum) {
        this.md5Sum = md5Sum;
    }

    public ImagePlatform getPlatform() {
        return platform;
    }

    public void setPlatform(ImagePlatform platform) {
        this.platform = platform;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean getSystem() {
        return system;
    }

    public void setSystem(Boolean system) {
        this.system = system;
    }

    public ImageConstant.ImageMediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(ImageConstant.ImageMediaType mediaType) {
        this.mediaType = mediaType;
    }

    public Timestamp getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Timestamp createDate) {
        this.createDate = createDate;
    }

    public Timestamp getLastOpDate() {
        return lastOpDate;
    }

    public void setLastOpDate(Timestamp lastOpDate) {
        this.lastOpDate = lastOpDate;
    }

    public String getGuestOsType() {
        return guestOsType;
    }

    public void setGuestOsType(String guestOsType) {
        this.guestOsType = guestOsType;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public Boolean getVirtio() {
        return virtio;
    }

    public void setVirtio(Boolean virtio) {
        this.virtio = virtio;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

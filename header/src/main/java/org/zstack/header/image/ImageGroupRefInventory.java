package org.zstack.header.image;

import org.zstack.header.query.ExpandedQueries;
import org.zstack.header.query.ExpandedQuery;
import org.zstack.header.search.Inventory;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Inventory(mappingVOClass = ImageGroupRefVO.class)
@ExpandedQueries({
        @ExpandedQuery(expandedField = "imageGroup", inventoryClass = ImageGroupRefInventory.class,
                foreignKey = "imageGroupUuid", expandedInventoryKey = "uuid"),
        @ExpandedQuery(expandedField = "image", inventoryClass = ImageInventory.class,
                foreignKey = "imageUuid", expandedInventoryKey = "uuid")
})
public class ImageGroupRefInventory {
    private String imageUuid;
    private String imageGroupUuid;
    private String description;
    private String state;
    private String status;
    private Long size;
    private Long actualSize;
    private String md5Sum;
    private String url;
    private String mediaType;
    private String guestOsType;
    private String type;
    private String platform;
    private String architecture;
    private String format;
    private Boolean system;
    private Boolean virtio;
    private Timestamp createDate;
    private Timestamp lastOpDate;

    public static ImageGroupRefInventory valueOf(ImageGroupRefVO imageGroupRef) {
        ImageGroupRefInventory inv = new ImageGroupRefInventory();
        inv.setImageUuid(imageGroupRef.getImageUuid());
        inv.setImageGroupUuid(imageGroupRef.getImageGroupUuid());
        inv.setDescription(imageGroupRef.getDescription());
        inv.setState(imageGroupRef.getState().toString());
        inv.setStatus(imageGroupRef.getStatus().toString());
        inv.setSize(imageGroupRef.getSize());
        inv.setActualSize(imageGroupRef.getActualSize());
        inv.setMd5Sum(imageGroupRef.getMd5Sum());
        inv.setUrl(imageGroupRef.getUrl());
        inv.setMediaType(imageGroupRef.getMediaType().name());
        inv.setGuestOsType(imageGroupRef.getGuestOsType());
        inv.setType(imageGroupRef.getType());
        inv.setPlatform(imageGroupRef.getPlatform().toString());
        inv.setArchitecture(imageGroupRef.getArchitecture());
        inv.setSystem(imageGroupRef.isSystem());
        inv.setVirtio(imageGroupRef.getVirtio());
        inv.setCreateDate(imageGroupRef.getCreateDate());
        inv.setLastOpDate(imageGroupRef.getLastOpDate());
       return inv;
    }

    public static List<ImageGroupRefInventory> valueOf(Set<ImageGroupRefVO> vos) {
        return vos.stream().map(ImageGroupRefInventory::valueOf).collect(Collectors.toList());
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

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Boolean getVirtio() {
        return virtio;
    }

    public void setVirtio(Boolean virtio) {
        this.virtio = virtio;
    }

    public Boolean getSystem() {
        return system;
    }

    public void setSystem(Boolean system) {
        this.system = system;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGuestOsType() {
        return guestOsType;
    }

    public void setGuestOsType(String guestOsType) {
        this.guestOsType = guestOsType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMd5Sum() {
        return md5Sum;
    }

    public void setMd5Sum(String md5Sum) {
        this.md5Sum = md5Sum;
    }

    public Long getActualSize() {
        return actualSize;
    }

    public void setActualSize(Long actualSize) {
        this.actualSize = actualSize;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
}

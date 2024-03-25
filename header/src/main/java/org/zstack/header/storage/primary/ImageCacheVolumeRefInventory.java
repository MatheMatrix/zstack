package org.zstack.header.storage.primary;

import org.zstack.header.rest.APINoSee;
import org.zstack.header.search.Inventory;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Inventory(mappingVOClass = ImageCacheVolumeRefVO.class)
public class ImageCacheVolumeRefInventory implements Serializable {
    @APINoSee
    private long id;
    private long imageCacheId;
    private String volumeUuid;
    private String primaryStorageUuid;
    private String imageUuid;
    private Timestamp createDate;
    private Timestamp lastOpDate;


    public static ImageCacheVolumeRefInventory valueOf(ImageCacheVolumeRefVO vo) {
        ImageCacheVolumeRefInventory inv = new ImageCacheVolumeRefInventory();
        inv.setImageCacheId(vo.getImageCacheId());
        inv.setVolumeUuid(vo.getVolumeUuid());
        inv.setPrimaryStorageUuid(vo.getPrimaryStorageUuid());
        inv.setImageUuid(vo.getImageUuid());
        inv.setCreateDate(vo.getCreateDate());
        inv.setLastOpDate(vo.getLastOpDate());
        inv.setId(vo.getId());
        return inv;
    }

    public static List<ImageCacheVolumeRefInventory> valueOf(Collection<ImageCacheVolumeRefVO> vos) {
        List<ImageCacheVolumeRefInventory> invs = new ArrayList<ImageCacheVolumeRefInventory>();
        for (ImageCacheVolumeRefVO vo : vos) {
            invs.add(valueOf(vo));
        }
        return invs;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    public long getImageCacheId() {
        return imageCacheId;
    }

    public void setImageCacheId(long imageCacheId) {
        this.imageCacheId = imageCacheId;
    }

    public String getVolumeUuid() {
        return volumeUuid;
    }

    public void setVolumeUuid(String volumeUuid) {
        this.volumeUuid = volumeUuid;
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

    public String getImageUuid() {
        return imageUuid;
    }

    public void setImageUuid(String imageUuid) {
        this.imageUuid = imageUuid;
    }
}

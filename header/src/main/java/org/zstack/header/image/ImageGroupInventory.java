package org.zstack.header.image;

import org.zstack.header.query.*;
import org.zstack.header.search.Inventory;

import javax.persistence.JoinColumn;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Inventory(mappingVOClass = ImageGroupVO.class)
@ExpandedQueries({
        @ExpandedQuery(expandedField = "imageRef", inventoryClass = ImageGroupRefInventory.class,
                foreignKey = "uuid", expandedInventoryKey = "imageGroupUuid", hidden = true),
})
@ExpandedQueryAliases({
        @ExpandedQueryAlias(alias = "image", expandedField = "imageRef.image")
})
public class ImageGroupInventory {
    private String uuid;
    private Integer imageCount;
    private String name;
    private String description;
    private Timestamp createDate;
    private Timestamp lastOpDate;

    @Queryable(mappingClass = ImageGroupRefInventory.class,
            joinColumn = @JoinColumn(name = "imageGroupUuid"))
    private List<ImageGroupRefInventory> imageGroupRefs;
    public static ImageGroupInventory valueOf(ImageGroupVO imageGroupVO) {
        ImageGroupInventory inv = new ImageGroupInventory();
        inv.setUuid(imageGroupVO.getUuid());
        inv.setImageCount(imageGroupVO.getImageCount());
        inv.setName(imageGroupVO.getName());
        inv.setDescription(imageGroupVO.getDescription());
        inv.setCreateDate(imageGroupVO.getCreateDate());
        inv.setLastOpDate(imageGroupVO.getLastOpDate());
        inv.setImageGroupRefs(ImageGroupRefInventory.valueOf(imageGroupVO.getImageGroupRefs()));
        return inv;
    }

    public static List<ImageGroupRefInventory>  valueOf(Collection<ImageGroupRefVO> vos) {
        List<ImageGroupRefInventory> invs = new ArrayList<ImageGroupRefInventory>();
        for (ImageGroupRefVO vo : vos) {
            invs.add(ImageGroupRefInventory.valueOf(vo));
        }
        return invs;
    }


    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Integer getImageCount() {
        return imageCount;
    }

    public void setImageCount(Integer imageCount) {
        this.imageCount = imageCount;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public List<ImageGroupRefInventory> getImageGroupRefs() {
        return imageGroupRefs;
    }

    public void setImageGroupRefs(List<ImageGroupRefInventory> imageGroupRefs) {
        this.imageGroupRefs = imageGroupRefs;
    }

}

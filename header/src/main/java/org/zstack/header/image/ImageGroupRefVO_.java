package org.zstack.header.image;

import javax.persistence.metamodel.SingularAttribute;
import java.sql.Timestamp;

public class ImageGroupRefVO_ {
    public static volatile SingularAttribute<ImageGroupRefVO, String> imageUuid;
    public static volatile SingularAttribute<ImageGroupRefVO, String> imageGroupUuid;
    public static volatile SingularAttribute<ImageGroupRefVO, String> name;
    public static volatile SingularAttribute<ImageGroupRefVO, String> description;
    public static volatile SingularAttribute<ImageGroupRefVO, ImageStatus> status;
    public static volatile SingularAttribute<ImageGroupRefVO, ImageState> state;
    public static volatile SingularAttribute<ImageGroupRefVO, Long> size;
    public static volatile SingularAttribute<ImageGroupRefVO, Long> actualSize;
    public static volatile SingularAttribute<ImageGroupRefVO, String> md5Sum;
    public static volatile SingularAttribute<ImageGroupRefVO, ImagePlatform> platform;
    public static volatile SingularAttribute<ImageGroupRefVO, String> type;
    public static volatile SingularAttribute<ImageGroupRefVO, String> format;
    public static volatile SingularAttribute<ImageGroupRefVO, String> url;
    public static volatile SingularAttribute<ImageGroupRefVO, Boolean> system;
    public static volatile SingularAttribute<ImageGroupRefVO, ImageConstant.ImageMediaType> mediaType;
    public static volatile SingularAttribute<ImageGroupRefVO, Timestamp> createDate;
    public static volatile SingularAttribute<ImageGroupRefVO, Timestamp> lastOpDate;
    public static volatile SingularAttribute<ImageGroupRefVO, String> guestOsType;
    public static volatile SingularAttribute<ImageGroupRefVO, String> architecture;
    public static volatile SingularAttribute<ImageGroupRefVO, ImageAO> shadow;
    public static volatile SingularAttribute<ImageGroupRefVO, Boolean> virtio;
}

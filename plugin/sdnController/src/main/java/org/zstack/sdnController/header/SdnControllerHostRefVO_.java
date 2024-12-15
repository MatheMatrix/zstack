package org.zstack.sdnController.header;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(SdnControllerHostRefVO.class)
public class SdnControllerHostRefVO_ {
    public static volatile SingularAttribute<SdnControllerHostRefVO, Long> id;
    public static volatile SingularAttribute<SdnControllerHostRefVO, String> hostUuid;
    public static volatile SingularAttribute<SdnControllerHostRefVO, String> sdnControllerUuid;
    public static volatile SingularAttribute<SdnControllerHostRefVO, String> vswitchType;
    public static volatile SingularAttribute<SdnControllerHostRefVO, String> vtepIp;
    public static volatile SingularAttribute<SdnControllerHostRefVO, String> physicalNics;
}
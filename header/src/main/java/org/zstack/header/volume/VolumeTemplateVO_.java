package org.zstack.header.volume;

import org.zstack.header.vm.VmInstanceTemplateVO;
import org.zstack.header.vo.ResourceVO_;

import javax.persistence.metamodel.SingularAttribute;
import java.sql.Timestamp;

public class VolumeTemplateVO_ extends ResourceVO_ {
    public static volatile SingularAttribute<VmInstanceTemplateVO, String> volumeUuid;
    public static volatile SingularAttribute<VmInstanceTemplateVO, VolumeType> originalType;
    public static volatile SingularAttribute<VmInstanceTemplateVO, Timestamp> createDate;
    public static volatile SingularAttribute<VmInstanceTemplateVO, Timestamp> lastOpDate;
}

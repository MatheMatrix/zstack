package org.zstack.header.vm;

import org.zstack.header.vo.ResourceVO_;

import javax.persistence.metamodel.SingularAttribute;
import java.sql.Timestamp;

public class VmInstanceTemplateVO_ extends ResourceVO_ {
    public static volatile SingularAttribute<VmInstanceTemplateVO, String> name;
    public static volatile SingularAttribute<VmInstanceTemplateVO, String> vmInstanceUuid;
    public static volatile SingularAttribute<VmInstanceTemplateVO, String> originalType;
    public static volatile SingularAttribute<VmInstanceTemplateVO, Timestamp> createDate;
    public static volatile SingularAttribute<VmInstanceTemplateVO, Timestamp> lastOpDate;
}

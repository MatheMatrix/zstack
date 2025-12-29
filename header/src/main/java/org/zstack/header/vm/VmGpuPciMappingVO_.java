package org.zstack.header.vm;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(VmGpuPciMappingVO.class)
public class VmGpuPciMappingVO_ {
    public static volatile SingularAttribute<VmGpuPciMappingVO, String> uuid;
    public static volatile SingularAttribute<VmGpuPciMappingVO, String> vmInstanceUuid;
    public static volatile SingularAttribute<VmGpuPciMappingVO, String> vmPciAddress;
    public static volatile SingularAttribute<VmGpuPciMappingVO, String> hostPciAddress;
    public static volatile SingularAttribute<VmGpuPciMappingVO, String> gpuSerial;
    public static volatile SingularAttribute<VmGpuPciMappingVO, java.sql.Timestamp> createDate;
    public static volatile SingularAttribute<VmGpuPciMappingVO, java.sql.Timestamp> lastOpDate;
}
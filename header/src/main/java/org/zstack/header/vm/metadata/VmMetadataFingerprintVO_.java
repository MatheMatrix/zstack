package org.zstack.header.vm.metadata;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;

@StaticMetamodel(VmMetadataFingerprintVO.class)
public class VmMetadataFingerprintVO_ {
    public static volatile SingularAttribute<VmMetadataFingerprintVO, String> vmInstanceUuid;
    public static volatile SingularAttribute<VmMetadataFingerprintVO, Timestamp> lastFlushTime;
    public static volatile SingularAttribute<VmMetadataFingerprintVO, Boolean> lastFlushFailed;
    public static volatile SingularAttribute<VmMetadataFingerprintVO, Integer> staleRecoveryCount;
    public static volatile SingularAttribute<VmMetadataFingerprintVO, String> metadataSnapshot;
    public static volatile SingularAttribute<VmMetadataFingerprintVO, Timestamp> createDate;
    public static volatile SingularAttribute<VmMetadataFingerprintVO, Timestamp> lastOpDate;
}

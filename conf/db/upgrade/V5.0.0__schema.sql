CREATE TABLE IF NOT EXISTS `zstack`.`VmMetadataDirtyVO` (
    `vmInstanceUuid` VARCHAR(32) NOT NULL,
    `managementNodeUuid` VARCHAR(32) DEFAULT NULL,
    `dirtyVersion` BIGINT NOT NULL DEFAULT 1,
    `lastClaimTime` TIMESTAMP NULL DEFAULT NULL,
    `storageStructureChange` TINYINT(1) NOT NULL DEFAULT 0,
    `retryCount` INT NOT NULL DEFAULT 0,
    `nextRetryTime` TIMESTAMP NULL DEFAULT NULL,
    `lastOpDate` timestamp on update CURRENT_TIMESTAMP,
    `createDate` timestamp,
    PRIMARY KEY (`vmInstanceUuid`),
    CONSTRAINT `fkVmMetadataDirtyVOVmInstanceEO` FOREIGN KEY (`vmInstanceUuid`)  REFERENCES `VmInstanceEO` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkVmMetadataDirtyVOManagementNodeVO` FOREIGN KEY (`managementNodeUuid`) REFERENCES `ManagementNodeVO` (`uuid`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`VmMetadataFingerprintVO` (
    `vmInstanceUuid` VARCHAR(32) NOT NULL,
    `metadataSnapshot` LONGTEXT,
    `lastFlushTime` TIMESTAMP NULL DEFAULT NULL,
    `lastFlushFailed` TINYINT(1) NOT NULL DEFAULT 0,
    `staleRecoveryCount` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`vmInstanceUuid`),
    CONSTRAINT `fkVmMetadataFingerprintVOVmInstanceEO` FOREIGN KEY (`vmInstanceUuid`)  REFERENCES `VmInstanceEO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

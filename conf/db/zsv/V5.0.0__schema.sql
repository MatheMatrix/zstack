CREATE TABLE IF NOT EXISTS `zstack`.`VmMetadataDirtyVO` (
     `vmInstanceUuid` VARCHAR(32) NOT NULL,
    `managementNodeUuid` VARCHAR(32) DEFAULT NULL,
    `dirtyVersion` BIGINT NOT NULL DEFAULT 1,
    `lastClaimTime` TIMESTAMP NULL DEFAULT NULL,
    `storageStructureChange` TINYINT(1) NOT NULL DEFAULT 0,
    `retryCount` INT NOT NULL DEFAULT 0,
    `nextRetryTime` TIMESTAMP NULL DEFAULT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`vmInstanceUuid`),
    INDEX `idx_VmMetadataDirtyVO_unclaimed` (`managementNodeUuid`, `nextRetryTime`, `lastOpDate`),
    CONSTRAINT `fkVmMetadataDirtyVOVmInstanceEO` FOREIGN KEY (`vmInstanceUuid`)  REFERENCES `VmInstanceEO` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkVmMetadataDirtyVOManagementNodeVO` FOREIGN KEY (`managementNodeUuid`) REFERENCES `ManagementNodeVO` (`uuid`) ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`VmMetadataFlushStateVO` (
    `vmInstanceUuid` VARCHAR(32) NOT NULL,
    `metadataSnapshot` LONGTEXT,
    `lastFlushFinishTime` TIMESTAMP NULL DEFAULT NULL,
    `pendingStaleRecovery` TINYINT(1) NOT NULL DEFAULT 0,
    `staleRecoveryCount` INT NOT NULL DEFAULT 0,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`vmInstanceUuid`),
    CONSTRAINT `fkVmMetadataFlushStateVOVmInstanceEO` FOREIGN KEY (`vmInstanceUuid`)  REFERENCES `VmInstanceEO` (`uuid`) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

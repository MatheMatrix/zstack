-- Feature: VM Metadata Dirty Mark + Poller (replaces GC-based approach)

CREATE TABLE IF NOT EXISTS `zstack`.`VmMetadataDirtyVO` (
    `vmInstanceUuid` VARCHAR(32) NOT NULL,
    `managementNodeUuid` VARCHAR(32) DEFAULT NULL,
    `dirtyVersion` BIGINT NOT NULL DEFAULT 1,
    `storageStructureChange` TINYINT(1) NOT NULL DEFAULT 0,
    `retryCount` INT NOT NULL DEFAULT 0,
    `nextRetryTime` TIMESTAMP NULL DEFAULT NULL,
    `createDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`vmInstanceUuid`),
    CONSTRAINT `fkVmMetadataDirtyVOVmInstanceEO` FOREIGN KEY (`vmInstanceUuid`)
        REFERENCES `VmInstanceEO` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkVmMetadataDirtyVOManagementNodeVO` FOREIGN KEY (`managementNodeUuid`)
        REFERENCES `ManagementNodeVO` (`uuid`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Poller CAS claim query optimization: WHERE managementNodeUuid IS NULL AND nextRetryTime <= NOW()
CREATE INDEX `idxVmMetadataDirtyUnclaimed` ON `VmMetadataDirtyVO` (`managementNodeUuid`, `nextRetryTime`);

-- Clean up any old GC rows for vm metadata (from previous GC-based implementation)
DELETE FROM `GarbageCollectorVO` WHERE `name` LIKE 'update-vm-%-metadata-gc';

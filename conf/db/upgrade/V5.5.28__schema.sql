-- Do not filter by architecture here. The upgrade preserves previous Windows VM behavior across all architectures;
-- current kvmagent consumption is still gated by host CPU architecture at start time.
INSERT INTO `zstack`.`ResourceConfigVO` (`uuid`, `name`, `description`, `category`, `value`, `resourceUuid`, `resourceType`, `lastOpDate`, `createDate`)
SELECT REPLACE(UUID(), '-', ''), 'vm.cpu.hardwareVirtualization', 'enable or disable hardware virtualization feature in Windows guest cpuid',
       'kvm', 'true', vm.`uuid`, 'VmInstanceVO', NOW(), NOW()
FROM `zstack`.`VmInstanceVO` vm
WHERE (vm.`platform` IN ('Windows', 'WindowsVirtio')
    OR LOWER(IFNULL(vm.`guestOsType`, '')) LIKE '%windows%')
  AND NOT EXISTS (
      SELECT 1
      FROM `zstack`.`ResourceConfigVO` rc
      WHERE rc.`resourceUuid` = vm.`uuid`
        AND rc.`category` = 'kvm'
        AND rc.`name` = 'vm.cpu.hardwareVirtualization'
  );

UPDATE `zstack`.`PciDeviceMdevSpecRefVO` keepRef
JOIN (
    SELECT `pciDeviceUuid`, `mdevSpecUuid`, MAX(`id`) AS `keepId`, MAX(`effective`) AS `effective`
    FROM `zstack`.`PciDeviceMdevSpecRefVO`
    GROUP BY `pciDeviceUuid`, `mdevSpecUuid`
) groupedRef ON keepRef.`id` = groupedRef.`keepId`
SET keepRef.`effective` = groupedRef.`effective`;

DELETE duplicateRef FROM `zstack`.`PciDeviceMdevSpecRefVO` duplicateRef
JOIN `zstack`.`PciDeviceMdevSpecRefVO` keepRef
  ON duplicateRef.`pciDeviceUuid` = keepRef.`pciDeviceUuid`
 AND duplicateRef.`mdevSpecUuid` = keepRef.`mdevSpecUuid`
 AND duplicateRef.`id` < keepRef.`id`;

UPDATE `zstack`.`PciDeviceMdevSpecRefVO` ref
JOIN (
    SELECT activeRef.`id`
    FROM `zstack`.`PciDeviceMdevSpecRefVO` activeRef
    JOIN (
        SELECT `pciDeviceUuid`
        FROM `zstack`.`PciDeviceMdevSpecRefVO`
        WHERE `effective` = 1
        GROUP BY `pciDeviceUuid`
        HAVING COUNT(*) > 1
    ) duplicatedPci ON activeRef.`pciDeviceUuid` = duplicatedPci.`pciDeviceUuid`
    WHERE activeRef.`effective` = 1
      AND NOT EXISTS (
          SELECT 1
          FROM `zstack`.`MdevDeviceVO` mdev
          WHERE mdev.`parentUuid` = activeRef.`pciDeviceUuid`
            AND mdev.`mdevSpecUuid` = activeRef.`mdevSpecUuid`
      )
) staleRef ON ref.`id` = staleRef.`id`
SET ref.`effective` = 0;

UPDATE `zstack`.`PciDeviceMdevSpecRefVO` oldRef
JOIN `zstack`.`PciDeviceMdevSpecRefVO` newRef
  ON oldRef.`pciDeviceUuid` = newRef.`pciDeviceUuid`
 AND oldRef.`effective` = 1
 AND newRef.`effective` = 1
 AND oldRef.`id` < newRef.`id`
SET oldRef.`effective` = 0;

DROP PROCEDURE IF EXISTS addPciDeviceMdevSpecRefUniqueKey;
DELIMITER $$
CREATE PROCEDURE addPciDeviceMdevSpecRefUniqueKey()
BEGIN
    DECLARE index_count INT DEFAULT 0;

    SELECT COUNT(*) INTO index_count
    FROM information_schema.statistics
    WHERE table_schema = 'zstack'
      AND table_name = 'PciDeviceMdevSpecRefVO'
      AND index_name = 'ukPciDeviceMdevSpecRefVOPciUuidMdevSpecUuid';

    IF index_count < 1 THEN
        ALTER TABLE `zstack`.`PciDeviceMdevSpecRefVO`
            ADD UNIQUE KEY `ukPciDeviceMdevSpecRefVOPciUuidMdevSpecUuid` (`pciDeviceUuid`, `mdevSpecUuid`);
    END IF;

    SELECT CURTIME();
END $$
DELIMITER ;
CALL addPciDeviceMdevSpecRefUniqueKey();
DROP PROCEDURE IF EXISTS addPciDeviceMdevSpecRefUniqueKey;

-- vTPM / KMS / NKP schema upgrade for cloud 5.5.28
-- Migrated from zsv V5.0.0__schema.sql. post-5.0.0 snapshot rollback key backup is not part of this default upgrade.

-- 1. KeyProviderVO (referenced by EncryptedResourceKeyRefVO)
CREATE TABLE IF NOT EXISTS `zstack`.`KeyProviderVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `name` varchar(255) NOT NULL,
    `description` varchar(2048) DEFAULT NULL,
    `type` varchar(32) NOT NULL,
    `connected` boolean NOT NULL DEFAULT FALSE,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`uuid`),
    UNIQUE KEY `ukKeyProviderVOName` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 2. KmsVO
CREATE TABLE IF NOT EXISTS `zstack`.`KmsVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `endpoint` varchar(255) NOT NULL,
    `port` int unsigned NOT NULL,
    `kmipVersion` varchar(32) DEFAULT NULL,
    `username` varchar(255) DEFAULT NULL,
    `password` varchar(255) DEFAULT NULL,
    `trustState` varchar(32) NOT NULL DEFAULT 'MUTUAL_UNTRUSTED',
    `activeIdentityUuid` varchar(32) DEFAULT NULL,
    `serverCertExpiredDate` timestamp NULL DEFAULT NULL,
    `serverCertPem` text DEFAULT NULL,
    PRIMARY KEY (`uuid`),
    INDEX `idxKmsVOActiveIdentityUuid` (`activeIdentityUuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 3. KmsIdentityVO (references KmsVO)
CREATE TABLE IF NOT EXISTS `zstack`.`KmsIdentityVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `kmsUuid` varchar(32) NOT NULL,
    `identityType` varchar(32) NOT NULL,
    `clientCertPem` text DEFAULT NULL,
    `clientKeyPem` text DEFAULT NULL,
    `csrPem` text DEFAULT NULL,
    `certExpiredDate` timestamp NULL DEFAULT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`uuid`),
    UNIQUE KEY `ukKmsIdentityVOKmsUuidType` (`kmsUuid`, `identityType`),
    INDEX `idxKmsIdentityVOKmsUuid` (`kmsUuid`),
    CONSTRAINT `fkKmsIdentityVOKmsVO` FOREIGN KEY (`kmsUuid`) REFERENCES `KmsVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 4. NkpVO
CREATE TABLE IF NOT EXISTS `zstack`.`NkpVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `kdf` varchar(64) NOT NULL,
    `saltPolicy` varchar(64) NOT NULL,
    `backedUp` boolean NOT NULL DEFAULT FALSE,
    `currentVersion` int unsigned DEFAULT NULL,
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 5. EncryptedResourceKeyRefVO (references KeyProviderVO)
-- This table does not exist in cloud 5.5.28, so do not run orphan cleanup before creation.
CREATE TABLE IF NOT EXISTS `zstack`.`EncryptedResourceKeyRefVO` (
    `id` bigint unsigned NOT NULL UNIQUE AUTO_INCREMENT,
    `resourceType` varchar(255) NOT NULL,
    `resourceUuid` varchar(32) NOT NULL,
    `providerUuid` varchar(32) DEFAULT NULL,
    `providerName` varchar(255) NOT NULL,
    `keyVersion` int unsigned DEFAULT NULL,
    `kekRef` varchar(255) DEFAULT NULL,
    `wrappedDek` text NOT NULL,
    `algorithm` varchar(64) DEFAULT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`id`),
    INDEX `idxEncryptedResourceKeyRefVOResource` (`resourceType`, `resourceUuid`),
    INDEX `idxEncryptedResourceKeyRefVOProviderUuid` (`providerUuid`),
    INDEX `idxEncryptedResourceKeyRefVOProviderName` (`providerName`),
    CONSTRAINT `fkEncryptedResourceKeyRefVOProviderUuid` FOREIGN KEY (`providerUuid`) REFERENCES `KeyProviderVO` (`uuid`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 6. TpmVO (references VmInstanceEO)
CREATE TABLE IF NOT EXISTS `zstack`.`TpmVO` (
    `uuid` char(32) NOT NULL UNIQUE,
    `vmInstanceUuid` char(32) NOT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkTpmVOVmInstanceVO` FOREIGN KEY (`vmInstanceUuid`) REFERENCES `VmInstanceEO` (`uuid`) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 7. VmHostFileVO
CREATE TABLE IF NOT EXISTS `zstack`.`VmHostFileVO` (
    `uuid` char(32) NOT NULL UNIQUE,
    `vmInstanceUuid` char(32) NOT NULL,
    `hostUuid` char(32) NOT NULL,
    `type` varchar(64) NOT NULL COMMENT 'NvRam, TpmState',
    `path` varchar(1024) NOT NULL COMMENT 'Absolute path of the file on the host',
    `lastSyncReason` varchar(255) DEFAULT NULL COMMENT 'The reason for the last sync operation',
    `changeDate` timestamp NULL DEFAULT NULL COMMENT 'Timestamp when file was reported changed, null after sync',
    `lastSyncDate` timestamp NULL DEFAULT NULL COMMENT 'Timestamp of the last successful sync',
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`uuid`),
    INDEX `idxVmHostFileVOVmInstanceUuid` (`vmInstanceUuid`),
    INDEX `idxVmHostFileVOHostUuid` (`hostUuid`),
    UNIQUE KEY `ukVmHostFileVO` (`vmInstanceUuid`, `hostUuid`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 8. VmHostBackupFileVO
CREATE TABLE IF NOT EXISTS `zstack`.`VmHostBackupFileVO` (
    `uuid` char(32) NOT NULL UNIQUE,
    `resourceUuid` char(32) NOT NULL,
    `type` varchar(64) NOT NULL COMMENT 'NvRam, TpmState',
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`uuid`),
    UNIQUE KEY `ukVmHostBackupFileVO` (`resourceUuid`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 9. VmHostFileContentVO (references ResourceVO)
CREATE TABLE IF NOT EXISTS `zstack`.`VmHostFileContentVO` (
    `uuid` char(32) NOT NULL UNIQUE COMMENT 'VmHostFileVO.uuid or VmHostBackupFileVO.uuid',
    `content` MEDIUMBLOB DEFAULT NULL,
    `format` varchar(64) NOT NULL COMMENT 'Raw, TarballGzip',
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkVmHostFileContentVOResourceVO` FOREIGN KEY (`uuid`) REFERENCES `ResourceVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 10. HostKeyIdentityVO (references HostEO)
CREATE TABLE IF NOT EXISTS `zstack`.`HostKeyIdentityVO` (
    `hostUuid` varchar(32) NOT NULL UNIQUE,
    `publicKey` text NOT NULL,
    `fingerprint` varchar(128) NOT NULL,
    `verified` boolean NOT NULL DEFAULT FALSE,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`hostUuid`),
    CONSTRAINT `fkHostKeyIdentityVOHostEO` FOREIGN KEY (`hostUuid`) REFERENCES `HostEO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

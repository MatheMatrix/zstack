CREATE TABLE IF NOT EXISTS `zstack`.`PluginDriverVO` (
    `uuid` char(32) NOT NULL UNIQUE,
    `name` varchar(64) NOT NULL,
    `type` varchar(64) NOT NULL,
    `vendor` varchar(64) NOT NULL,
    `features` varchar(1024) NOT NULL,
    `optionTypes` text DEFAULT NULL,
    `license` varchar(1024) DEFAULT NULL,
    `version` varchar(1024) DEFAULT NULL,
    `description` text DEFAULT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59' ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    `deleted` varchar(255) DEFAULT NULL,
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`VolumeCbtBackupRecordVO` (
    `id` bigint unsigned NOT NULL UNIQUE AUTO_INCREMENT,
    `taskUuid` varchar(32) NOT NULL,
    `volumeUuid` varchar(32) NOT NULL,
    `mode` varchar(255) NOT NULL,
    `target` varchar(2048) NOT NULL,
    `scratchNodeName` varchar(255) NOT NULL,
    `bitmapName` varchar(255) NOT NULL,
    `lastBitmapName` varchar(255),
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE IF NOT EXISTS `zstack`.`GuestVmScriptEO` (
    `uuid` VARCHAR(32) NOT NULL UNIQUE,
    `name` VARCHAR(256) NOT NULL,
    `description` VARCHAR(256),
    `platform` VARCHAR(255) NOT NULL,
    `scriptContent` MEDIUMTEXT,
    `renderParams` MEDIUMTEXT,
    `scriptType` VARCHAR(32) NOT NULL,
    `scriptTimeout` INT UNSIGNED NOT NULL,
    `version` INT UNSIGNED NOT NULL,
    `deleted` VARCHAR(255) DEFAULT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59' ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP VIEW IF EXISTS `zstack`.`GuestVmScriptVO`;
CREATE VIEW `zstack`.`GuestVmScriptVO` AS SELECT uuid, name, description, platform, scriptContent, renderParams, scriptType, scriptTimeout, version, createDate, lastOpDate FROM `zstack`.`GuestVmScriptEO` WHERE deleted IS NULL;

CREATE TABLE IF NOT EXISTS `zstack`.`GuestVmScriptExecutedRecordVO` (
    `uuid` VARCHAR(32) NOT NULL UNIQUE,
    `recordName` VARCHAR(255) NOT NULL,
    `scriptUuid` VARCHAR(32) NOT NULL,
    `scriptTimeout` INT UNSIGNED NOT NULL,
    `status` VARCHAR(256) NOT NULL,
    `version` INT UNSIGNED NOT NULL,
    `Executor` VARCHAR(256) NOT NULL ,
    `ExecutionCount` INT UNSIGNED NOT NULL,
    `scriptContent` MEDIUMTEXT,
    `renderParams` MEDIUMTEXT,
    `startTime` TIMESTAMP NOT NULL DEFAULT '1999-12-31 23:59:59',
    `endTime` TIMESTAMP NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`uuid`),
    INDEX `idxScriptUuid` (`scriptUuid`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`GuestVmScriptExecutedRecordDetailVO` (
    `recordUuid` VARCHAR(32) NOT NULL,
    `vmInstanceUuid` VARCHAR(32) NOT NULL,
    `vmName` VARCHAR(255) NOT NULL,
    `status` VARCHAR(128) NOT NULL,
    `exitCode` INT UNSIGNED,
    `stdout` MEDIUMTEXT,
    `errCause` MEDIUMTEXT,
    `stderr` MEDIUMTEXT,
    `startTime` TIMESTAMP NOT NULL DEFAULT '1999-12-31 23:59:59',
    `endTime` TIMESTAMP NOT NULL DEFAULT '1999-12-31 23:59:59',
    PRIMARY KEY (`recordUuid`, `vmInstanceUuid`),
    CONSTRAINT `fkGuestVmScriptExecutedRecordDetailVOScriptExecutedRecordVO` FOREIGN KEY (`recordUuid`) REFERENCES `GuestVmScriptExecutedRecordVO` (`uuid`) ON DELETE CASCADE
)ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE `zstack`.`AuditsVO` MODIFY COLUMN requestDump MEDIUMTEXT, MODIFY COLUMN responseDump MEDIUMTEXT;

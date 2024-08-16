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
    `startTime` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
    `endTime` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
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
    `startTime` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
    `endTime` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`recordUuid`, `vmInstanceUuid`),
    CONSTRAINT `fkGuestVmScriptExecutedRecordDetailVOScriptExecutedRecordVO` FOREIGN KEY (`recordUuid`) REFERENCES `GuestVmScriptExecutedRecordVO` (`uuid`) ON DELETE CASCADE
)ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE `zstack`.`AuditsVO` MODIFY COLUMN requestDump MEDIUMTEXT, MODIFY COLUMN responseDump MEDIUMTEXT;
CREATE TABLE IF NOT EXISTS `zstack`.`HbaDeviceVO` (
    `uuid` varchar(32) not null unique,
    `hostUuid` varchar(32) default null,
    `name` varchar(255) default null,
    `hbaType`  varchar(64) default null,
    `createDate` timestamp not null default '0000-00-00 00:00:00',
    `lastOpDate` timestamp not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fkHBADeviceVOHostVO FOREIGN KEY (hostUuid) REFERENCES HostEO (uuid) ON DELETE CASCADE,
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`FcHbaDeviceVO` (
    `uuid` varchar(32) not null unique,
    `portName` varchar(255) default null,
    `portState`  varchar(64) default null,
    `supportedSpeeds`  varchar(255) default null,
    `speed`  varchar(255) default null,
    `symbolicName`  varchar(255) default null,
    `supportedClasses`  varchar(255) default null,
    `nodeName` varchar(255) default null,
    CONSTRAINT fkFcHbaDeviceVO FOREIGN KEY (uuid) REFERENCES HbaDeviceVO (uuid) ON DELETE CASCADE,
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`VmHaVO` (
    `uuid` char(32) not null unique,
    `haLevel` varchar(64) not null default 'Undefined',
    `haLevelUpdateTime` timestamp not null default CURRENT_TIMESTAMP,
    `inhibitionReason` varchar(255) default null,
    `inhibitionTime` timestamp default '0000-00-00 00:00:00',
    CONSTRAINT fkVmHaVOVmInstanceVO FOREIGN KEY (uuid) REFERENCES VmInstanceEO (uuid) ON DELETE CASCADE,
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO `zstack`.`VmHaVO` (`uuid`, `haLevel`)
    SELECT `uuid`, 'None' FROM `zstack`.`VmInstanceEO`;

UPDATE `zstack`.`VmHaVO`
    SET `haLevel` = 'NeverStop'
    WHERE `uuid` IN (
        SELECT `resourceUuid` FROM `SystemTagVO` WHERE `tag` = 'ha::NeverStop'
    );

UPDATE `zstack`.`VmHaVO`
    SET `haLevel` = 'OnHostFailure'
    WHERE `uuid` IN (
        SELECT `resourceUuid` FROM `SystemTagVO` WHERE `tag` = 'ha::OnHostFailure'
    );

-- in version zsv_4.3.0
-- Feature: LDAP Enhance | ZSV-5531

CREATE TABLE `zstack`.`AccountImportSourceVO` (
    `uuid` char(32) not null unique,
    `description` varchar(2048) default null,
    `type` varchar(32) not null,
    `lastOpDate` timestamp on update current_timestamp,
    `createDate` timestamp,
    primary key (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `zstack`.`ImportAccountRefVO` (
    `uuid` char(32) not null unique,
    `keyFromImportSource` varchar(2048) not null,
    `importSourceUuid` char(32) not null,
    `accountUuid` char(32) not null,
    `lastOpDate` timestamp on update current_timestamp,
    `createDate` timestamp,
    primary key (`uuid`),
    CONSTRAINT `fkImportAccountRefVOAccountImportSourceVO` FOREIGN KEY (`importSourceUuid`) REFERENCES AccountImportSourceVO (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkImportAccountRefVOAccountVO` FOREIGN KEY (`accountUuid`) REFERENCES AccountVO (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
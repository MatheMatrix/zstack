-- in version zsv_4.3.0
-- Feature: LDAP Enhance | ZSV-5531

CREATE TABLE `zstack`.`ThirdPartyAccountSourceVO` (
    `uuid` char(32) not null unique,
    `description` varchar(2048) default null,
    `type` varchar(32) not null,
    `createAccountStrategy` varchar(32) not null,
    `deleteAccountStrategy` varchar(32) not null,
    `lastOpDate` timestamp on update current_timestamp,
    `createDate` timestamp,
    primary key (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `zstack`.`AccountSourceRefVO` (
    `uuid` char(32) not null unique,
    `credentials` varchar(255) not null,
    `accountSourceUuid` char(32) not null,
    `accountUuid` char(32) not null,
    `lastOpDate` timestamp on update current_timestamp,
    `createDate` timestamp,
    primary key (`uuid`),
    CONSTRAINT `fkAccountSourceRefVOThirdPartyAccountSourceVO` FOREIGN KEY (`accountSourceUuid`) REFERENCES ThirdPartyAccountSourceVO (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkAccountSourceRefVOAccountVO` FOREIGN KEY (`accountUuid`) REFERENCES AccountVO (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE `zstack`.`AccountSourceRefVO` ADD UNIQUE INDEX(credentials,accountSourceUuid);
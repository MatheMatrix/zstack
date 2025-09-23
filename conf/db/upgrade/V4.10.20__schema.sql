CREATE TABLE IF NOT EXISTS `zstack`.`StoragePackageVO` (
    `uuid` char(32) NOT NULL UNIQUE,
    `name` varchar(255) NOT NULL,
    `hostUuid` char(32) NOT NULL,
    `managementNodeUuid` char(32) NOT NULL,
    `installPath` varchar(1024),
    `unzipInstallPath` varchar(1024),
    `type` varchar(1024),
    `size` bigint unsigned,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp,
    PRIMARY KEY  (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
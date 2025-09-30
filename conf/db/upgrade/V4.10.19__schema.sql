CREATE TABLE IF NOT EXISTS `zstack`.`StoragePackageVO` (
    `uuid` char(32) NOT NULL UNIQUE,
    `name` varchar(1024),
    `hostUuid` varchar(1024),
    `mnUuid` varchar(1024),
    `installPath` varchar(1024),
    `unzipInstallPath` varchar(1024),
    `size` bigint unsigned,
    `type` varchar(1024),
    PRIMARY KEY  (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

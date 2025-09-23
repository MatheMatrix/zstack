CREATE TABLE IF NOT EXISTS `zstack`.`StoragePackageVO` (
    `uuid` char(32) NOT NULL UNIQUE,
    `name` varchar(1024),
    `hostUuid` char(32) NOT NULL,
    `managementNodeUuid` char(32) NOT NULL,
    `installPath` varchar(1024),
    `unzipInstallPath` varchar(1024),
    `size` bigint unsigned,
    `type` varchar(1024),
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp,
    PRIMARY KEY  (`uuid`),
    CONSTRAINT fk_storage_package_host FOREIGN KEY (`hostUuid`) REFERENCES `Host`(`uuid`),
    CONSTRAINT fk_storage_package_mn FOREIGN KEY (`mnUuid`) REFERENCES `ManagementNode`(`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
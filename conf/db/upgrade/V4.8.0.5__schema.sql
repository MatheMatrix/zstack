CREATE TABLE IF NOT EXISTS `zstack`.`HostPhysicalCpuVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `designation` varchar(255) DEFAULT NULL,
    `version` varchar(32) DEFAULT NULL,
    `currentSpeed` varchar(32) DEFAULT NULL,
    `coreCount` varchar(32) NOT NULL,
    `threadCount` varchar(32) DEFAULT NULL,
    `hostUuid` varchar(32) NOT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00' ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkHostPhysicalCpuVOHostVO` FOREIGN KEY (`hostUuid`) REFERENCES `zstack`.`HostEO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
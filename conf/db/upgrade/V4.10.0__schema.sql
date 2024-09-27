CREATE TABLE IF NOT EXISTS `zstack`.`HostPhysicalCpuVO` (
    `uuid` char(32) NOT NULL UNIQUE,
    `designation` varchar(255) DEFAULT NULL,
    `version` varchar(255) DEFAULT NULL,
    `serialNumber` varchar(255) NOT NULL,
    `currentSpeed` varchar(32) DEFAULT NULL,
    `coreCount` varchar(32) DEFAULT NULL,
    `threadCount` varchar(32) DEFAULT NULL,
    `hostUuid` char(32) NOT NULL,
    `lastOpDate` timestamp ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkHostPhysicalCpuVOHostVO` FOREIGN KEY (`hostUuid`) REFERENCES `zstack`.`HostEO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE `zstack`.`PciDeviceVO` ADD COLUMN `passThroughState` varchar(32) NOT NULL;
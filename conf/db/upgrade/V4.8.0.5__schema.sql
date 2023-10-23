CREATE TABLE IF NOT EXISTS `zstack`.`EthernetVfPciDeviceVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `hostDevUuid` varchar(32) DEFAULT NULL,
    `interfaceName` varchar(32) DEFAULT NULL,
    `vmUuid` varchar(32) DEFAULT NULL,
    `l3NetworkUuid` varchar(32) DEFAULT NULL,
    `vfStatus` varchar(32) NOT NULL,
    PRIMARY KEY  (`uuid`),
    CONSTRAINT `fkEthernetVfPciDeviceVOVmInstanceEO` FOREIGN KEY (`vmUuid`) REFERENCES `VmInstanceEO` (`uuid`) ON DELETE SET NULL,
    CONSTRAINT `fkEthernetVfPciDeviceVOHostEO` FOREIGN KEY (`hostDevUuid`) REFERENCES `HostEO` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkEthernetVfPciDeviceVO` FOREIGN KEY (`uuid`) REFERENCES `PciDeviceVO` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkEthernetVfPciDeviceVOL3NetworkEO` FOREIGN KEY (`l3NetworkUuid`) REFERENCES `L3NetworkEO` (`uuid`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
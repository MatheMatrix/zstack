CALL ADD_COLUMN('SdnControllerVO', 'status', 'VARCHAR(32)', 0, 'Connected');
CALL ADD_COLUMN('HostNetworkInterfaceVO', 'driverType', 'VARCHAR(32)', 1, NULL);

CREATE TABLE IF NOT EXISTS `zstack`.`SdnControllerHostRefVO` (
    `id` BIGINT UNSIGNED NOT NULL UNIQUE AUTO_INCREMENT,
    `sdnControllerUuid` varchar(32) NOT NULL,
    `hostUuid`  varchar(32) NOT NULL,
    `vSwitchType`  varchar(255) NOT NULL,
    `vtepIp`  varchar(128) DEFAULT NULL,
    `netmask`  varchar(128) DEFAULT NULL,
    `nicPciAddresses`  varchar(1024) DEFAULT NULL,
    `nicDrivers`  varchar(1024) DEFAULT NULL,
    `bondMode`  varchar(64) DEFAULT NULL,
    `lacpMode`  varchar(64) DEFAULT NULL,
    CONSTRAINT fkSdnControllerHostRefVOSdnControllerVO FOREIGN KEY (sdnControllerUuid) REFERENCES SdnControllerVO (uuid) ON DELETE CASCADE,
    CONSTRAINT fkSdnControllerHostRefVOHostEO FOREIGN KEY (hostUuid) REFERENCES HostEO (uuid) ON DELETE CASCADE,
    CONSTRAINT ukSdnControllerHostRefVO UNIQUE (`sdnControllerUuid`,`hostUuid`, `vSwitchType`),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS  `zstack`.`OvnControllerVmOfferingVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `managementNetworkUuid` varchar(32) NOT NULL,
    `imageUuid` varchar(32) NOT NULL,
    `zoneUuid` varchar(32) NOT NULL,
    PRIMARY KEY  (`uuid`),
    CONSTRAINT fkOvnControllerVmOfferingVOL3NetworkEO FOREIGN KEY (managementNetworkUuid) REFERENCES `zstack`.`L3NetworkEO` (uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS  `zstack`.`OvnControllerVmInstanceVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    PRIMARY KEY  (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

UPDATE `zstack`.`L2NetworkVO` set vSwitchType='TfL2Network' where type='TfL2Network';
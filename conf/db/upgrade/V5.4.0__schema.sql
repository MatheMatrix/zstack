CALL ADD_COLUMN('SdnControllerVO', 'status', 'VARCHAR(32)', 0, 'Connected');

CREATE TABLE IF NOT EXISTS `zstack`.`SdnControllerHostRefVO` (
    `id` BIGINT UNSIGNED NOT NULL UNIQUE AUTO_INCREMENT,
    `sdnControllerUuid` varchar(32) default null,
    `hostUuid`  varchar(32) default null,
    `vswitchType`  varchar(255) default null,
    `vtepIp`  varchar(255) default null,
    `physicalNics`  varchar(255) default null,
    CONSTRAINT fkSdnControllerHostRefVOSdnControllerVO FOREIGN KEY (sdnControllerUuid) REFERENCES SdnControllerVO (uuid) ON DELETE CASCADE,
    CONSTRAINT fkSdnControllerHostRefVOHostEO FOREIGN KEY (hostUuid) REFERENCES HostEO (uuid) ON DELETE CASCADE,
    CONSTRAINT ukSdnControllerHostRefVO UNIQUE (`sdnControllerUuid`,`hostUuid`, `vswitchType`),
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
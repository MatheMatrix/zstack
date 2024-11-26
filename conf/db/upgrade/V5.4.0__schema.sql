CREATE TABLE `zstack`.`LogServerOfferingVO`(
    `uuid`                  varchar(32) NOT NULL UNIQUE,
    `managementNetworkUuid` varchar(32) DEFAULT NULL,
    `publicNetworkUuid`     varchar(32) DEFAULT NULL,
    `imageUuid`             varchar(32) NOT NULL,
    `zoneUuid`              varchar(32) NOT NULL,
    `isDefault`             tinyint(1) unsigned DEFAULT 0,
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE LogServerOfferingVO ADD CONSTRAINT fkLogServerOfferingVOImageEO FOREIGN KEY (imageUuid) REFERENCES ImageEO (uuid) ON DELETE CASCADE;
ALTER TABLE LogServerOfferingVO ADD CONSTRAINT fkLogServerOfferingVOInstanceOfferingEO FOREIGN KEY (uuid) REFERENCES InstanceOfferingEO (uuid) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE LogServerOfferingVO ADD CONSTRAINT fkLogServerOfferingVOL3NetworkEO FOREIGN KEY (managementNetworkUuid) REFERENCES L3NetworkEO (uuid) ON DELETE CASCADE;
ALTER TABLE LogServerOfferingVO ADD CONSTRAINT fkLogServerOfferingVOL3NetworkEO1 FOREIGN KEY (publicNetworkUuid) REFERENCES L3NetworkEO (uuid) ON DELETE CASCADE;
ALTER TABLE LogServerOfferingVO ADD CONSTRAINT fkLogServerOfferingVOZoneEO FOREIGN KEY (zoneUuid) REFERENCES ZoneEO (uuid) ON DELETE CASCADE;

CREATE TABLE  `zstack`.`LogServerVmVO` (
   `uuid` varchar(32) NOT NULL UNIQUE,
   `publicNetworkUuid` varchar(32) DEFAULT NULL,
   PRIMARY KEY  (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE LogServerVmVO ADD CONSTRAINT fkLogServerVmVOVmInstanceEO FOREIGN KEY (uuid) REFERENCES VmInstanceEO (uuid) ON UPDATE RESTRICT ON DELETE CASCADE;


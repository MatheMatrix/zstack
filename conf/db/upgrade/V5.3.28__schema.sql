CREATE TABLE `zstack`.`ObservabilityServerOfferingVO`(
    `uuid`                  varchar(32) NOT NULL UNIQUE,
    `managementNetworkUuid` varchar(32) DEFAULT NULL,
    `publicNetworkUuid`     varchar(32) DEFAULT NULL,
    `imageUuid`             varchar(32) NOT NULL,
    `zoneUuid`              varchar(32) NOT NULL,
    `isDefault`             tinyint(1) unsigned DEFAULT 0,
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE ObservabilityServerOfferingVO ADD CONSTRAINT fkObservabilityServerOfferingVOImageEO FOREIGN KEY (imageUuid) REFERENCES ImageEO (uuid) ON DELETE CASCADE;
ALTER TABLE ObservabilityServerOfferingVO ADD CONSTRAINT fkObservabilityServerOfferingVOInstanceOfferingEO FOREIGN KEY (uuid) REFERENCES InstanceOfferingEO (uuid) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ObservabilityServerOfferingVO ADD CONSTRAINT fkObservabilityServerOfferingVOL3NetworkEO FOREIGN KEY (managementNetworkUuid) REFERENCES L3NetworkEO (uuid) ON DELETE CASCADE;
ALTER TABLE ObservabilityServerOfferingVO ADD CONSTRAINT fkObservabilityServerOfferingVOL3NetworkEO1 FOREIGN KEY (publicNetworkUuid) REFERENCES L3NetworkEO (uuid) ON DELETE CASCADE;
ALTER TABLE ObservabilityServerOfferingVO ADD CONSTRAINT fkObservabilityServerOfferingVOZoneEO FOREIGN KEY (zoneUuid) REFERENCES ZoneEO (uuid) ON DELETE CASCADE;

CREATE TABLE  `zstack`.`ObservabilityServerVmVO` (
   `uuid` varchar(32) NOT NULL UNIQUE,
   `publicNetworkUuid` varchar(32) DEFAULT NULL,
   PRIMARY KEY  (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE ObservabilityServerVmVO ADD CONSTRAINT fkObservabilityServerVmVOVmInstanceEO FOREIGN KEY (uuid) REFERENCES VmInstanceEO (uuid) ON UPDATE RESTRICT ON DELETE CASCADE;

CREATE TABLE `zstack`.`ObservabilityServerServiceRefVO`(
    `id`                              BIGINT UNSIGNED NOT NULL UNIQUE AUTO_INCREMENT,
    `observabilityServerOfferingUuid` varchar(32)          DEFAULT NULL,
    `observabilityServerUuid`         varchar(32) NOT NULL,
    `serviceUuid`                     varchar(32) NOT NULL,
    `serviceType`                     varchar(32) NOT NULL,
    `observabilityServerPublicIp`     varchar(32)          DEFAULT NULL,
    `servicePublicIp`                 varchar(32)          DEFAULT NULL,
    `lastOpDate`                      timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate`                      timestamp   NOT NULL DEFAULT '0000-00-00 00:00:00',
PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE ObservabilityServerServiceRefVO ADD CONSTRAINT fkObservabilityServerServiceRefVOResourceVO FOREIGN KEY (serviceUuid) REFERENCES ResourceVO (uuid) ON DELETE CASCADE;

CREATE TABLE IF NOT EXISTS `zstack`.`LogServerVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `name` varchar(255) NOT NULL,
    `description` varchar(2048) NULL,
    `category` varchar(255) NOT NULL,
    `type` varchar(255) NOT NULL,
    `level` varchar(255) NULL,
    `configuration` text NOT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY  (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CALL ADD_COLUMN('GuestVmScriptEO', 'encodingType', 'VARCHAR(32)', 1, 'PlainText');
CALL ADD_COLUMN('GuestVmScriptExecutedRecordVO', 'encodingType', 'VARCHAR(32)', 1, 'PlainText');
DROP VIEW IF EXISTS `zstack`.`GuestVmScriptVO`;
CREATE VIEW `zstack`.`GuestVmScriptVO` AS SELECT uuid, name, description, platform, encodingType, scriptContent, renderParams, scriptType, scriptTimeout, version, createDate, lastOpDate FROM `zstack`.`GuestVmScriptEO` WHERE deleted IS NULL;


CREATE TABLE `zstack`.`ImageGroupVO` (
     `uuid` VARCHAR(32) NOT NULL UNIQUE,
     `name` VARCHAR(255) NOT NULL,
     `description` VARCHAR(2048) DEFAULT NULL,
     `imageCount` int unsigned NOT NULL,
     `lastOpDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00' ON UPDATE current_timestamp(),
     `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
     PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `zstack`.`ImageGroupRefVO` (
    `imageUuid` VARCHAR(32) NOT NULL UNIQUE,
    `imageGroupUuid` VARCHAR(32) NOT NULL,
    `size` bigint(20) unsigned DEFAULT NULL COMMENT 'image size',
    `md5sum` varchar(255) DEFAULT NULL COMMENT 'md5sum of image',
    `name` varchar(255) NOT NULL COMMENT 'image name',
    `description` varchar(2048) DEFAULT NULL COMMENT 'image description',
    `url` varchar(1024) NOT NULL COMMENT 'image url',
    `installUrl` varchar(1024) DEFAULT NULL COMMENT 'url where image installed on secondary storage',
    `mediaType` varchar(32) NOT NULL,
    `format` varchar(32) NOT NULL,
    `system` tinyint(3) unsigned DEFAULT 0,
    `platform` varchar(255) DEFAULT NULL,
    `type` varchar(255) NOT NULL COMMENT 'image type',
    `guestOsType` varchar(255) DEFAULT 'other' COMMENT 'guest os type string',
    `state` varchar(32) NOT NULL COMMENT 'image state',
    `status` varchar(32) NOT NULL COMMENT 'image status',
    `actualSize` bigint(20) unsigned DEFAULT NULL,
    `architecture` varchar(32) DEFAULT NULL,
    `virtio` tinyint(1) DEFAULT 1,
    `lastOpDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00' ON UPDATE current_timestamp(),
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`imageUuid`),
    KEY `idxImageGroupRefName` (`name`),
    CONSTRAINT `fkImageGroupRefVOImageGroupVO` FOREIGN KEY (`imageGroupUuid`) REFERENCES `ImageGroupVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
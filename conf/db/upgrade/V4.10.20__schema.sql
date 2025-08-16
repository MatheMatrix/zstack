CREATE TABLE IF NOT EXISTS `zstack`.`SoftwarePackageVO` (
    `uuid` char(32) NOT NULL UNIQUE,
    `name` varchar(255) NOT NULL,
    `hostUuid` char(32),
    `managementNodeUuid` char(32),
    `installPath` varchar(2048),
    `unzipInstallPath` varchar(2048),
    `type` varchar(1024),
    `md5sum` char(32),
    `status` char(32),
    `size` bigint unsigned,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp,
    PRIMARY KEY  (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CALL INSERT_COLUMN('HostEO', 'hostname', 'varchar(256)', 1, NULL, 'nqn');
DROP VIEW IF EXISTS `zstack`.`HostVO`;
CREATE VIEW `zstack`.`HostVO` AS SELECT uuid, zoneUuid, clusterUuid, name, description, managementIp, hypervisorType,
state, status, architecture, nqn, hostname, createDate, lastOpDate FROM `zstack`.`HostEO` WHERE deleted IS NULL;

CREATE TABLE `zstack`.`ActionProgressVO` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `apiId` char(32) NOT NULL,
    `content` varchar(255) DEFAULT NULL,
    `opaque` text,
    `createTime` bigint unsigned NOT NULL,
    `lastOpTime` bigint unsigned NOT NULL,
    `currentStep` bigint unsigned DEFAULT 0,
    `totalStep` bigint unsigned DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY `idxActionProgressVOApiId` (`apiId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


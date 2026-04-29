-- ZNS SDN Controller support

CREATE TABLE IF NOT EXISTS `ZnsControllerVO` (
    `uuid` varchar(32) NOT NULL,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkZnsControllerVOSdnControllerVO` FOREIGN KEY (`uuid`) REFERENCES `SdnControllerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `L2GeneveNetworkVO` (
    `uuid` varchar(32) NOT NULL,
    `geneveId` int(10) unsigned NOT NULL,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkL2GeneveNetworkVOL2NetworkEO` FOREIGN KEY (`uuid`) REFERENCES `L2NetworkEO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `ZnsTransportZoneVO` (
    `uuid` varchar(32) NOT NULL,
    `name` varchar(255) DEFAULT NULL,
    `description` varchar(2048) DEFAULT NULL,
    `type` varchar(64) DEFAULT NULL,
    `physicalNetwork` varchar(255) DEFAULT NULL,
    `status` varchar(64) DEFAULT NULL,
    `isDefault` tinyint(1) NOT NULL DEFAULT 0,
    `tags` text DEFAULT NULL,
    `znsSdnControllerUuid` varchar(32) NOT NULL,
    `createDate`         timestamp       NOT NULL DEFAULT '0000-00-00 00:00:00',
    `lastOpDate`         timestamp       NOT NULL DEFAULT '0000-00-00 00:00:00' ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkZnsTransportZoneVOSdnControllerVO` FOREIGN KEY (`znsSdnControllerUuid`) REFERENCES `SdnControllerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- ZNS Wave 2: Tenant / TenantRouter 资源建模 (ZCF-2133)

CREATE TABLE IF NOT EXISTS `ZnsTenantVO` (
  `uuid` VARCHAR(32) NOT NULL,
  `sdnControllerUuid` VARCHAR(32) NOT NULL,
  `znsResourceUuid` VARCHAR(64) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `description` VARCHAR(2048) DEFAULT NULL,
  `lastOpDate` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `createDate` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`uuid`),
  UNIQUE KEY `uk_zns_tenant_resource` (`sdnControllerUuid`, `znsResourceUuid`),
  CONSTRAINT `fk_zns_tenant_sdn` FOREIGN KEY (`sdnControllerUuid`) REFERENCES `SdnControllerVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `ZnsTenantRouterVO` (
  `uuid` VARCHAR(32) NOT NULL,
  `sdnControllerUuid` VARCHAR(32) NOT NULL,
  `tenantUuid` VARCHAR(32) NOT NULL,
  `znsResourceUuid` VARCHAR(64) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `description` VARCHAR(2048) DEFAULT NULL,
  `state` VARCHAR(32) NOT NULL COMMENT 'Active / Inactive',
  `lastOpDate` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `createDate` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`uuid`),
  UNIQUE KEY `uk_zns_tr_resource` (`sdnControllerUuid`, `znsResourceUuid`),
  KEY `idx_zns_tr_tenant` (`tenantUuid`),
  CONSTRAINT `fk_zns_tr_sdn` FOREIGN KEY (`sdnControllerUuid`) REFERENCES `SdnControllerVO` (`uuid`) ON DELETE CASCADE,
  CONSTRAINT `fk_zns_tr_tenant` FOREIGN KEY (`tenantUuid`) REFERENCES `ZnsTenantVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

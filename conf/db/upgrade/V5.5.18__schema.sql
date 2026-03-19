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


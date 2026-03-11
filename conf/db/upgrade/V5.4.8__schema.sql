-- Add ExternalServiceConfiguration table
CREATE TABLE IF NOT EXISTS `zstack`.`ExternalServiceConfigurationVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `serviceType` varchar(32) NOT NULL,
    `configuration` text DEFAULT NULL,
    `description` varchar(2048) DEFAULT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`ResNotifySubscriptionVO` (
    `uuid` VARCHAR(32) NOT NULL UNIQUE,
    `name` VARCHAR(255) DEFAULT NULL,
    `description` VARCHAR(2048) DEFAULT NULL,
    `resourceTypes` TEXT DEFAULT NULL,
    `eventTypes` VARCHAR(256) DEFAULT NULL,
    `type` VARCHAR(32) NOT NULL DEFAULT 'WEBHOOK',
    `state` VARCHAR(32) NOT NULL DEFAULT 'Enabled',
    `accountUuid` VARCHAR(32) DEFAULT NULL,
    `lastOpDate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`uuid`),
    INDEX `idx_ResNotifySubscriptionVO_accountUuid` (`accountUuid`),
    INDEX `idx_ResNotifySubscriptionVO_type_state` (`type`, `state`),
    CONSTRAINT `fkResNotifySubscriptionVOResourceVO` FOREIGN KEY (`uuid`) REFERENCES `ResourceVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`ResNotifyWebhookRefVO` (
    `uuid` VARCHAR(32) NOT NULL UNIQUE,
    `webhookUrl` TEXT NOT NULL,
    `secret` VARCHAR(256) DEFAULT NULL,
    `customHeaders` TEXT,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fk_ResNotifyWebhookRefVO_ResNotifySubscriptionVO`
        FOREIGN KEY (`uuid`) REFERENCES `ResNotifySubscriptionVO`(`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

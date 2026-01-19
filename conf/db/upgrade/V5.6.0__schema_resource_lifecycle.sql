CREATE TABLE IF NOT EXISTS `zstack`.`ResNotifySubscriptionVO` (
    `uuid` VARCHAR(32) NOT NULL UNIQUE,
    `name` VARCHAR(255) DEFAULT NULL,
    `description` VARCHAR(2048) DEFAULT NULL,
    `resourceTypes` VARCHAR(1024) DEFAULT NULL,
    `eventTypes` VARCHAR(256) DEFAULT NULL,
    `type` VARCHAR(32) NOT NULL DEFAULT 'WEBHOOK',
    `state` VARCHAR(32) NOT NULL DEFAULT 'Enabled',
    `lastOpDate` TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` TIMESTAMP,
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`ResNotifyWebhookRefVO` (
    `uuid` VARCHAR(32) NOT NULL UNIQUE,
    `webhookUrl` VARCHAR(2048) NOT NULL,
    `secret` VARCHAR(256) DEFAULT NULL,
    `customHeaders` VARCHAR(2048) DEFAULT NULL,
    `lastDeliveryTime` TIMESTAMP NULL DEFAULT NULL,
    `consecutiveFailures` INT DEFAULT 0,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fk_ResNotifyWebhookRefVO_ResNotifySubscriptionVO` 
        FOREIGN KEY (`uuid`) REFERENCES `ResNotifySubscriptionVO`(`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
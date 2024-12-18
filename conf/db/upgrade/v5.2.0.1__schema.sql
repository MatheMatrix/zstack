CREATE TABLE IF NOT EXISTS `zstack`.`ExternalPrimaryStorageHostRefVO` (
    `id`     BIGINT UNSIGNED UNIQUE,
    `hostId` INT DEFAULT NULL,
    `protocol` varchar(128) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;


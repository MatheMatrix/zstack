ALTER TABLE `zstack`.`IpRangeEO` ADD COLUMN `state` varchar(255) NOT NULL DEFAULT "Enabled";
DROP VIEW IF EXISTS `zstack`.`IpRangeVO`;
CREATE VIEW `zstack`.`IpRangeVO` AS SELECT uuid, l3NetworkUuid, name, description, startIp, endIp, netmask, gateway, networkCidr, createDate, lastOpDate, ipVersion, addressMode, prefixLen, state FROM `zstack`.`IpRangeEO` WHERE deleted IS NULL;
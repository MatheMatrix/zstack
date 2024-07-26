UPDATE ResourceConfigVO SET createDate = CURRENT_TIMESTAMP where name='iam2.force.enable.securityGroup' and createDate='0000-00-00 00:00:00';

CREATE TABLE IF NOT EXISTS `zstack`.`SanSecSecretResourcePoolVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `keyIndex` varchar(128) DEFAULT NULL,
    PRIMARY KEY  (`uuid`),
    CONSTRAINT fkSanSecSecretResourcePoolVOSecretResourcePoolVO FOREIGN KEY (uuid) REFERENCES SecretResourcePoolVO (uuid) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE `zstack`.`SanSecSecretResourcePoolVO` ADD COLUMN `managementIp` varchar(128) DEFAULT NULL;
ALTER TABLE `zstack`.`SanSecSecretResourcePoolVO` ADD COLUMN `port` int unsigned DEFAULT NULL;
ALTER TABLE `zstack`.`SanSecSecretResourcePoolVO` ADD COLUMN `username` varchar(128) DEFAULT NULL;
ALTER TABLE `zstack`.`SanSecSecretResourcePoolVO` ADD COLUMN `password` varchar(128) DEFAULT NULL;
ALTER TABLE `zstack`.`SanSecSecretResourcePoolVO` ADD COLUMN `sm3Key` varchar(128) DEFAULT NULL;
ALTER TABLE `zstack`.`SanSecSecretResourcePoolVO` ADD COLUMN `sm4Key` varchar(128) DEFAULT NULL;

DROP PROCEDURE IF EXISTS AddFkPciDeviceVOVmInstanceEO;
DELIMITER $$
CREATE PROCEDURE AddFkPciDeviceVOVmInstanceEO()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_TYPE = 'FOREIGN KEY' AND TABLE_NAME = 'PciDeviceVO' AND CONSTRAINT_NAME = 'fkPciDeviceVOVmInstanceEO') = 0 THEN
ALTER TABLE PciDeviceVO
    ADD CONSTRAINT fkPciDeviceVOVmInstanceEO FOREIGN KEY (vmInstanceUuid) REFERENCES VmInstanceEO(uuid) ON DELETE SET NULL;
END IF;
END $$
DELIMITER ;
CALL AddFkPciDeviceVOVmInstanceEO();

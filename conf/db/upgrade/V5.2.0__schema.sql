ALTER TABLE `zstack`.`HostNetworkInterfaceVO` MODIFY COLUMN `mac` varchar(128) DEFAULT NULL;

CREATE TABLE IF NOT EXISTS `zstack`.`XmlHookVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `name` varchar(255) UNIQUE NOT NULL,
    `description` varchar(2048) NULL,
    `type` varchar(32) NOT NULL,
    `hookScript` text NOT NULL,
    `libvirtVersion` varchar(32) DEFAULT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY  (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`XmlHookVmInstanceRefVO` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `xmlHookUuid` varchar(32) NOT NULL,
    `vmInstanceUuid` varchar(32) NOT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`id`),
    UNIQUE KEY `id` (`id`),
    KEY `fkXmlHookVmInstanceRefVOXmlHookVO` (`xmlHookUuid`),
    KEY `fkXmlHookVmInstanceRefVOVmInstanceVO` (`vmInstanceUuid`),
    CONSTRAINT `fkXmlHookVmInstanceRefVO` FOREIGN KEY (`xmlHookUuid`) REFERENCES `XmlHookVO` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkXmlHookVmInstanceRefVO1` FOREIGN KEY (`vmInstanceUuid`) REFERENCES `ResourceVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


DROP PROCEDURE IF EXISTS migrateJsonLabelToXmlHookVO;
DELIMITER $$
CREATE PROCEDURE migrateJsonLabelToXmlHookVO()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE hookUuid VARCHAR(32);
    DECLARE vmUuid VARCHAR(32);
    DECLARE hookValue TEXT;
    DECLARE cur CURSOR FOR SELECT DISTINCT REPLACE(labelKey,'user-defined-xml-hook-script-',''),labelValue FROM zstack.JsonLabelVO WHERE labelKey like 'user-defined-xml-hook-script-%%';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO vmUuid, hookValue;
        IF done THEN
            LEAVE read_loop;
        END IF;

        IF NOT EXISTS(SELECT * from XmlHookVO where hookScript = hookValue) THEN
            SET hookUuid = (REPLACE(UUID(), '-', ''));

            INSERT zstack.ResourceVO(uuid, resourceName, resourceType, concreteResourceType)
            VALUES (hookUuid, 'xml-hook', 'XmlHookVO', 'org.zstack.header.tag.XmlHookVO');

            INSERT zstack.XmlHookVO (uuid, name, description, type, hookScript, lastOpDate, createDate)
            VALUES(hookUuid, concat('xml-hook', hookUuid), 'xml-hook', 'Customization', hookValue, NOW(), NOW());

            INSERT zstack.XmlHookVmInstanceRefVO(xmlHookUuid, vmInstanceUuid, lastOpDate, createDate)
            VALUES (hookUuid, vmUuid, NOW(), NOW());

        ELSEIF NOT EXISTS(SELECT * from XmlHookVmInstanceRefVO where vmInstanceUuid = vmUuid) THEN
            SET hookUuid = (select uuid from XmlHookVO where hookScript = hookValue);
            INSERT zstack.XmlHookVmInstanceRefVO(xmlHookUuid, vmInstanceUuid, lastOpDate, createDate)
            VALUES (hookUuid, vmUuid, NOW(), NOW());
        END IF;

        DELETE FROM zstack.JsonLabelVO WHERE labelKey = CONCAT('user-defined-xml-hook-script-', vmUuid) AND labelValue = hookValue;
    END LOOP;
    CLOSE cur;

    SELECT CURTIME();
END $$
DELIMITER ;
call migrateJsonLabelToXmlHookVO();
DROP PROCEDURE IF EXISTS migrateJsonLabelToXmlHookVO;

DELETE b FROM HostNetworkInterfaceLldpVO b LEFT JOIN ResourceVO a ON b.uuid = a.uuid WHERE a.uuid IS NULL;

ALTER TABLE BareMetal2InstanceProvisionNicVO MODIFY mac varchar(17) NULL;

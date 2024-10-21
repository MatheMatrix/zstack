<<<<<<< HEAD
=======
CREATE TABLE IF NOT EXISTS `zstack`.`GpuDeviceVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `serialNumber` varchar(255),
    `memory` bigint unsigned NULL DEFAULT 0,
    `power` bigint unsigned NULL DEFAULT 0,
    `isDriverLoaded` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY  (`uuid`),
    CONSTRAINT `fkGpuDeviceInfoVOPciDeviceVO` FOREIGN KEY (`uuid`) REFERENCES `PciDeviceVO` (`uuid`) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CALL ADD_COLUMN('PciDeviceVO', 'vendor', 'VARCHAR(128)', 1, NULL);
CALL ADD_COLUMN('PciDeviceVO', 'device', 'VARCHAR(128)', 1, NULL);
CALL ADD_COLUMN('PciDeviceSpecVO', 'vendor', 'VARCHAR(128)', 1, NULL);
CALL ADD_COLUMN('PciDeviceSpecVO', 'device', 'VARCHAR(128)', 1, NULL);
CALL ADD_COLUMN('MdevDeviceVO', 'vendor', 'VARCHAR(128)', 1, NULL);

DROP PROCEDURE IF EXISTS `MdevDeviceAddVendor`;
DELIMITER $$
CREATE PROCEDURE MdevDeviceAddVendor()
    BEGIN
        DECLARE vendor VARCHAR(128);
        DECLARE pciDeviceUuid VARCHAR(32);
        DECLARE done INT DEFAULT FALSE;
        DECLARE cur CURSOR FOR SELECT pci.uuid, pci.vendor FROM PciDeviceVO pci;
        DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

        OPEN cur;
        read_loop: LOOP
            FETCH cur INTO vendor, pciDeviceUuid;
            IF done THEN
                LEAVE read_loop;
            END IF;
            UPDATE MdevDeviceVO SET vendor = vendor WHERE parentUuid = pciDeviceUuid;
        END LOOP;
        CLOSE cur;
        SELECT CURTIME();
    END $$
DELIMITER ;
call MdevDeviceAddVendor;
DROP PROCEDURE IF EXISTS `MdevDeviceAddVendor`;

CREATE TABLE IF NOT EXISTS `HostHwMonitorStatusVO`
(
    `uuid` varchar(32)  NOT NULL UNIQUE,
    `cpuStatus` varchar(32) NOT NULL,
    `memoryStatus` varchar(32) NOT NULL,
    `diskStatus` varchar(32) NOT NULL,
    `nicStatus` varchar(32) NOT NULL,
    `gpuStatus` varchar(32) NOT NULL,
    `powerSupplyStatus` varchar(32) NOT NULL,
    `fanStatus` varchar(32) NOT NULL,
    `raidStatus` varchar(32) NOT NULL,
    `temperatureStatus` varchar(32) NOT NULL,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkHostHwMonitorStatusVO` FOREIGN KEY (`uuid`) REFERENCES `HostEO` (`uuid`) ON DELETE CASCADE
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8;

DROP PROCEDURE IF EXISTS `CreateGpuDeviceVO`;
DELIMITER $$
CREATE PROCEDURE CreateGpuDeviceVO()
    BEGIN
        DECLARE uuid VARCHAR(32);
        DECLARE done INT DEFAULT FALSE;
        DECLARE cur CURSOR FOR SELECT pci.uuid FROM PciDeviceVO pci where pci.type in ('GPU_Video_Controller', 'GPU_3D_Controller');
        DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
        OPEN cur;
        read_loop: LOOP
            FETCH cur INTO uuid;
            IF done THEN
                LEAVE read_loop;
            END IF;
            insert into GpuDeviceVO (uuid) values (uuid);
        END LOOP;
        CLOSE cur;
        SELECT CURTIME();
    END $$
DELIMITER ;
call CreateGpuDeviceVO;
DROP PROCEDURE IF EXISTS `CreateGpuDeviceVO`;

DROP PROCEDURE IF EXISTS `addPciDeviceVendor`;
DELIMITER $$
CREATE PROCEDURE addPciDeviceVendor()
    BEGIN
        DECLARE pciUuid VARCHAR(32);
        DECLARE vendorId VARCHAR(32);
        DECLARE done INT DEFAULT FALSE;
        DECLARE cur CURSOR FOR SELECT pci.uuid, pci.vendorId FROM PciDeviceVO pci where pci.type in ('GPU_Video_Controller', 'GPU_3D_Controller');
        DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
        OPEN cur;
        read_loop: LOOP
            FETCH cur INTO pciUuid, vendorId;
            IF done THEN
                LEAVE read_loop;
            END IF;
            IF vendorId = '1d94' then
                update PciDeviceVO set vendor = 'Haiguang' where uuid = pciUuid;
            ELSEIF vendorId = '10de' then
                update PciDeviceVO set vendor = 'NVIDIA' where uuid = pciUuid;
            ELSEIF vendorId = '8086' then
                update PciDeviceVO set vendor = 'AMD' where uuid = pciUuid;
            END IF;
        END LOOP;
        CLOSE cur;
        SELECT CURTIME();
    END $$
DELIMITER ;
call addPciDeviceVendor;
DROP PROCEDURE IF EXISTS `addPciDeviceVendor`;
>>>>>>> aa4fb00b33 (<fix>[host]: fix host hw monitor status)

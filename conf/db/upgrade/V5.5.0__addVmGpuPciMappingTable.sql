-- ZStack Database Upgrade Script for VmGpuPciMappingVO
-- Version: 5.5.0
-- Description: Add VmGpuPciMappingVO table to maintain VM PCI address to Host PCI address mapping

DELIMITER $$

DROP PROCEDURE IF EXISTS addVmGpuPciMappingTable$$

CREATE PROCEDURE addVmGpuPciMappingTable()
BEGIN
    DECLARE table_exists INT DEFAULT 0;

    -- Check if table already exists
    SELECT COUNT(*) INTO table_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
    AND table_name = 'VmGpuPciMappingVO';

    IF table_exists = 0 THEN
        -- Create the VmGpuPciMappingVO table
        CREATE TABLE `zstack`.`VmGpuPciMappingVO` (
            `uuid` varchar(32) NOT NULL,
            `vmInstanceUuid` varchar(32) NOT NULL COMMENT 'VM实例UUID',
            `vmPciAddress` varchar(32) NOT NULL COMMENT 'VM内部看到的PCI地址',
            `hostPciAddress` varchar(32) NOT NULL COMMENT 'Host上真实的PCI地址',
            `gpuSerial` varchar(128) DEFAULT NULL COMMENT 'GPU序列号',
            `createDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
            `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (`uuid`),
            UNIQUE KEY `ukVmGpuPciMappingVO` (`vmInstanceUuid`, `vmPciAddress`),
            KEY `fkVmGpuPciMappingVOVmInstanceVO` (`vmInstanceUuid`),
            CONSTRAINT `fkVmGpuPciMappingVOVmInstanceVO` FOREIGN KEY (`vmInstanceUuid`) REFERENCES `zstack`.`VmInstanceVO` (`uuid`) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='VM GPU PCI地址映射表';

        -- Log the table creation
        SELECT 'VmGpuPciMappingVO table created successfully' AS message;
    ELSE
        SELECT 'VmGpuPciMappingVO table already exists' AS message;
    END IF;
END$$

DELIMITER ;

-- Execute the procedure
CALL addVmGpuPciMappingTable();

-- Clean up
DROP PROCEDURE addVmGpuPciMappingTable;
-- ZSTAC-73546: Migrate existing global GPU quota to per-vendor (NVIDIA) quota
-- For users who already set container.gpu.video.ram.size, copy the value as NVIDIA vendor quota.
-- Other vendor quotas will use the GlobalConfig default (32GB) via the quota framework.
DELIMITER $$

DROP PROCEDURE IF EXISTS MigrateGpuQuotaPerVendor$$

CREATE PROCEDURE MigrateGpuQuotaPerVendor()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_identity_uuid VARCHAR(32);
    DECLARE v_identity_type VARCHAR(255);
    DECLARE v_value BIGINT;
    DECLARE cur CURSOR FOR
        SELECT `identityUuid`, `identityType`, `value`
        FROM `QuotaVO`
        WHERE `name` = 'container.gpu.video.ram.size';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO v_identity_uuid, v_identity_type, v_value;
        IF done THEN
            LEAVE read_loop;
        END IF;

        INSERT IGNORE INTO `QuotaVO` (`uuid`, `name`, `identityUuid`, `identityType`, `value`, `lastOpDate`, `createDate`)
        VALUES (
            REPLACE(UUID(), '-', ''),
            'container.gpu.video.ram.size.nvidia',
            v_identity_uuid,
            v_identity_type,
            v_value,
            NOW(),
            NOW()
        );
    END LOOP;
    CLOSE cur;
END$$

DELIMITER ;

CALL MigrateGpuQuotaPerVendor();
DROP PROCEDURE IF EXISTS MigrateGpuQuotaPerVendor;

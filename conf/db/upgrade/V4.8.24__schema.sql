DROP PROCEDURE IF EXISTS CreateResourceConfigForBindingVms;
DELIMITER $$
CREATE PROCEDURE CreateResourceConfigForBindingVms()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE vmUuid VARCHAR(128);

    DECLARE vmCursor CURSOR FOR
        SELECT resourceUuid
        FROM SystemTagVO
        WHERE resourceType = 'VmInstanceVO'
          AND tag LIKE 'resourceBindings::Cluster:%';

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN vmCursor;

    read_loop: LOOP
        FETCH vmCursor INTO vmUuid;

        IF done THEN
            LEAVE read_loop;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM ResourceConfigVO
            WHERE resourceType = 'VmInstanceVO'
              AND resourceUuid = vmUuid
              AND category = 'vm'
              AND name = 'vm.ha.across.clusters'
        ) THEN
            INSERT INTO ResourceConfigVO (uuid, name, category, value, resourceUuid, resourceType, lastOpDate, createDate)
            VALUES (REPLACE(UUID(),'-',''), 'vm.ha.across.clusters', 'vm', 'false', vmUuid, 'VmInstanceVO', NOW(), NOW());
        END IF;
    END LOOP;

    CLOSE vmCursor;
END $$
DELIMITER ;
call CreateResourceConfigForBindingVms();
DROP PROCEDURE IF EXISTS CreateResourceConfigForBindingVms;

DELETE ref FROM `zstack`.`VolumeSnapshotReferenceVO` ref
         INNER JOIN `zstack`.`VolumeEO` vol ON vol.uuid = ref.referenceVolumeUuid
WHERE ref.referenceType = 'VolumeVO'
AND ref.referenceVolumeUuid = ref.referenceUuid
AND ref.referenceInstallUrl NOT LIKE CONCAT('%', SUBSTRING_INDEX(vol.installPath, '/', -1), '%');

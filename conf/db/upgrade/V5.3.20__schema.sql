ALTER TABLE AutoScalingRuleSchedulerJobTriggerVO DROP FOREIGN KEY fkAutoScalingRuleSchedulerJobTriggerVO;
CALL ADD_CONSTRAINT('AutoScalingRuleSchedulerJobTriggerVO', 'fkAutoScalingRuleSchedulerJobTriggerVO', 'schedulerJobUuid', 'SchedulerJobVO', 'uuid', 'CASCADE');

ALTER TABLE `zstack`.`ExternalPrimaryStorageVO` MODIFY COLUMN `config` TEXT DEFAULT NULL;
ALTER TABLE `zstack`.`HostNetworkInterfaceLldpRefVO` MODIFY COLUMN `systemName` VARCHAR(255) NOT NULL;

CREATE TABLE IF NOT EXISTS `zstack`.`ExternalPrimaryStorageHostRefVO` (
    `id`       BIGINT UNSIGNED UNIQUE,
    `hostId`   INT          DEFAULT NULL,
    `protocol` varchar(128) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

SET @row_number = 0;
INSERT INTO ExternalPrimaryStorageHostRefVO (id, hostId, protocol)
SELECT
    p.id,
    (@row_number := @row_number + 1) as hostId,
    e.defaultProtocol as protocol
FROM PrimaryStorageHostRefVO p LEFT JOIN ExternalPrimaryStorageVO e ON p.primaryStorageUuid = e.uuid
ORDER BY p.id;

-- Delete old UserTagVO of AI::Image-Generation
DELETE FROM UserTagVO WHERE uuid = 'a7ec68923efe447d9119ba7b6df2b54c';

DELETE ref FROM `zstack`.`VolumeSnapshotReferenceVO` ref
                    INNER JOIN `zstack`.`VolumeEO` vol ON vol.uuid = ref.referenceVolumeUuid
WHERE ref.referenceType = 'VolumeVO'
  AND ref.referenceVolumeUuid = ref.referenceUuid
  AND ref.referenceInstallUrl NOT LIKE CONCAT('%', SUBSTRING_INDEX(vol.installPath, '/', -1), '%');

DROP PROCEDURE IF EXISTS ModifyApplicationDevelopmentServiceVO;
DELIMITER $$

CREATE PROCEDURE ModifyApplicationDevelopmentServiceVO()
BEGIN
    START TRANSACTION;

    CREATE TABLE IF NOT EXISTS `zstack`.`ApplicationDevelopmentServiceVO_temp` (
        `uuid` varchar(32) NOT NULL UNIQUE,
        `deploymentStatus` varchar(255) NOT NULL,
        PRIMARY KEY (`uuid`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

    INSERT INTO `zstack`.`ApplicationDevelopmentServiceVO_temp` (uuid, deploymentStatus)
    SELECT modelServiceGroupUuid, deploymentStatus
    FROM `zstack`.`ApplicationDevelopmentServiceVO`
    WHERE modelServiceGroupUuid IS NOT NULL;

    DROP TABLE `zstack`.`ApplicationDevelopmentServiceVO`;

    RENAME TABLE `zstack`.`ApplicationDevelopmentServiceVO_temp` TO `zstack`.`ApplicationDevelopmentServiceVO`;

    COMMIT;
END $$

DELIMITER ;

CALL ModifyApplicationDevelopmentServiceVO();

CALL ADD_COLUMN('ModelVO', 'modelId', 'VARCHAR(255)', 1, NULL);
CALL ADD_COLUMN('ModelServiceInstanceGroupVO', 'description', 'VARCHAR(2048)', 1, NULL);

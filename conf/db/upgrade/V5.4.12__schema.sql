CREATE TABLE IF NOT EXISTS `zstack`.`CbtTaskTemporaryVolumeVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `taskUuid` varchar(32) NOT NULL,
    `sourceVolumeUuid` varchar(32) NOT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`uuid`),
    UNIQUE KEY `ukCbtTaskTemporaryVolumeVOTaskSourceVolume` (`taskUuid`, `sourceVolumeUuid`),
    INDEX `idxCbtTaskTemporaryVolumeVOtaskUuid` (`taskUuid`),
    INDEX `idxCbtTaskTemporaryVolumeVOsourceVolumeUuid` (`sourceVolumeUuid`),
    CONSTRAINT `fkCbtTaskTemporaryVolumeVOUuidVolumeEO` FOREIGN KEY (`uuid`) REFERENCES `VolumeEO` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkCbtTaskTemporaryVolumeVOSourceVolumeEO` FOREIGN KEY (`sourceVolumeUuid`) REFERENCES `VolumeEO` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkCbtTaskTemporaryVolumeVOCbtTaskVO` FOREIGN KEY (`taskUuid`) REFERENCES `CbtTaskVO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT IGNORE INTO `zstack`.`CbtTaskTemporaryVolumeVO` (`uuid`, `taskUuid`, `sourceVolumeUuid`, `createDate`, `lastOpDate`)
SELECT DISTINCT ref.`resourceUuid`,
       ref.`taskUuid`,
       SUBSTRING(vol.`description`, CHAR_LENGTH('cbt-volume-for-') + 1),
       ref.`createDate`,
       ref.`lastOpDate`
FROM `zstack`.`CbtTaskResourceRefVO` ref
INNER JOIN `zstack`.`CbtTaskVO` task ON task.`uuid` = ref.`taskUuid`
INNER JOIN `zstack`.`VolumeVO` vol ON vol.`uuid` = ref.`resourceUuid`
INNER JOIN `zstack`.`VolumeVO` sourceVol ON sourceVol.`uuid` = SUBSTRING(vol.`description`, CHAR_LENGTH('cbt-volume-for-') + 1)
WHERE ref.`resourceType` = 'VolumeVO'
  AND vol.`description` LIKE 'cbt-volume-for-%';

INSERT IGNORE INTO `zstack`.`CbtTaskTemporaryVolumeVO` (`uuid`, `taskUuid`, `sourceVolumeUuid`, `createDate`, `lastOpDate`)
SELECT DISTINCT ref.`resourceUuid`,
       ref.`taskUuid`,
       rec.`volumeUuid`,
       ref.`createDate`,
       ref.`lastOpDate`
FROM `zstack`.`CbtTaskResourceRefVO` ref
INNER JOIN `zstack`.`CbtTaskVO` task ON task.`uuid` = ref.`taskUuid`
INNER JOIN `zstack`.`VolumeVO` vol ON vol.`uuid` = ref.`resourceUuid`
INNER JOIN `zstack`.`VolumeCbtBackupRecordVO` rec ON rec.`taskUuid` = ref.`taskUuid` AND rec.`target` = vol.`installPath`
INNER JOIN `zstack`.`VolumeVO` sourceVol ON sourceVol.`uuid` = rec.`volumeUuid`
WHERE ref.`resourceType` = 'VolumeVO';

INSERT INTO `zstack`.`CbtTaskResourceRefVO` (`taskUuid`, `resourceUuid`, `resourceType`, `createDate`, `lastOpDate`)
SELECT DISTINCT tmp.`taskUuid`,
       tmp.`sourceVolumeUuid`,
       'VolumeVO',
       tmp.`createDate`,
       tmp.`lastOpDate`
FROM `zstack`.`CbtTaskTemporaryVolumeVO` tmp
LEFT JOIN `zstack`.`CbtTaskResourceRefVO` ref
       ON ref.`taskUuid` = tmp.`taskUuid`
      AND ref.`resourceUuid` = tmp.`sourceVolumeUuid`
WHERE ref.`id` IS NULL;

DELETE ref FROM `zstack`.`CbtTaskResourceRefVO` ref
INNER JOIN `zstack`.`CbtTaskTemporaryVolumeVO` tmp
        ON tmp.`taskUuid` = ref.`taskUuid`
       AND tmp.`uuid` = ref.`resourceUuid`
WHERE ref.`resourceType` = 'VolumeVO';

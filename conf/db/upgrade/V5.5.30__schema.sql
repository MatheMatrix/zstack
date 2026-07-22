CREATE TABLE IF NOT EXISTS `zstack`.`CbtTaskTemporaryVolumeVO` (
    `uuid` varchar(32) NOT NULL,
    `taskUuid` varchar(32) NOT NULL,
    `sourceVolumeUuid` varchar(32) NOT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY (`uuid`),
    INDEX `idxCbtTaskTemporaryVolumeVOtaskUuid` (`taskUuid`),
    INDEX `idxCbtTaskTemporaryVolumeVOsourceVolumeUuid` (`sourceVolumeUuid`),
    CONSTRAINT `fkCbtTaskTemporaryVolumeVOTempVolumeEO` FOREIGN KEY (`uuid`) REFERENCES `zstack`.`VolumeEO` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fkCbtTaskTemporaryVolumeVOCbtTaskVO` FOREIGN KEY (`taskUuid`) REFERENCES `zstack`.`CbtTaskVO` (`uuid`) ON DELETE RESTRICT,
    CONSTRAINT `fkCbtTaskTemporaryVolumeVOSourceVolumeEO` FOREIGN KEY (`sourceVolumeUuid`) REFERENCES `zstack`.`VolumeEO` (`uuid`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP TEMPORARY TABLE IF EXISTS `tmpCbtTaskTemporaryVolumeRef`;

CREATE TEMPORARY TABLE `tmpCbtTaskTemporaryVolumeRef` (
    `tempVolumeUuid` varchar(32) NOT NULL,
    `taskUuid` varchar(32) NOT NULL,
    `sourceVolumeUuid` varchar(32) NOT NULL,
    `createDate` timestamp NULL DEFAULT NULL,
    `lastOpDate` timestamp NULL DEFAULT NULL,
    PRIMARY KEY (`tempVolumeUuid`, `taskUuid`, `sourceVolumeUuid`)
) ENGINE=Memory DEFAULT CHARSET=utf8;

INSERT IGNORE INTO `tmpCbtTaskTemporaryVolumeRef` (`tempVolumeUuid`, `taskUuid`, `sourceVolumeUuid`, `createDate`, `lastOpDate`)
SELECT DISTINCT tempVol.uuid, ref.taskUuid, sourceVol.uuid, ref.createDate, ref.lastOpDate
FROM `zstack`.`CbtTaskResourceRefVO` ref
INNER JOIN `zstack`.`VolumeVO` tempVol ON tempVol.uuid = ref.resourceUuid
INNER JOIN `zstack`.`VolumeEO` sourceVol ON sourceVol.uuid = SUBSTRING(tempVol.description, CHAR_LENGTH('cbt-volume-for-') + 1)
WHERE ref.resourceType = 'VolumeVO'
  AND tempVol.description LIKE 'cbt-volume-for-%';

INSERT IGNORE INTO `tmpCbtTaskTemporaryVolumeRef` (`tempVolumeUuid`, `taskUuid`, `sourceVolumeUuid`, `createDate`, `lastOpDate`)
SELECT DISTINCT tempVol.uuid, ref.taskUuid, sourceVol.uuid, ref.createDate, ref.lastOpDate
FROM `zstack`.`CbtTaskResourceRefVO` ref
INNER JOIN `zstack`.`VolumeVO` tempVol ON tempVol.uuid = ref.resourceUuid
INNER JOIN `zstack`.`VolumeCbtBackupRecordVO` rec ON rec.taskUuid = ref.taskUuid AND rec.target = tempVol.installPath
INNER JOIN `zstack`.`VolumeEO` sourceVol ON sourceVol.uuid = rec.volumeUuid
WHERE ref.resourceType = 'VolumeVO';

INSERT IGNORE INTO `zstack`.`CbtTaskTemporaryVolumeVO` (`uuid`, `taskUuid`, `sourceVolumeUuid`, `createDate`, `lastOpDate`)
SELECT tmp.tempVolumeUuid,
       tmp.taskUuid,
       tmp.sourceVolumeUuid,
       IFNULL(MIN(tmp.createDate), '0000-00-00 00:00:00'),
       IFNULL(MAX(tmp.lastOpDate), CURRENT_TIMESTAMP)
FROM `tmpCbtTaskTemporaryVolumeRef` tmp
GROUP BY tmp.tempVolumeUuid, tmp.taskUuid, tmp.sourceVolumeUuid;

INSERT INTO `zstack`.`CbtTaskResourceRefVO` (`taskUuid`, `resourceUuid`, `resourceType`, `createDate`, `lastOpDate`)
SELECT tmp.taskUuid,
       tmp.sourceVolumeUuid,
       'VolumeVO',
       IFNULL(MIN(tmp.createDate), '0000-00-00 00:00:00'),
       IFNULL(MAX(tmp.lastOpDate), CURRENT_TIMESTAMP)
FROM `tmpCbtTaskTemporaryVolumeRef` tmp
LEFT JOIN `zstack`.`CbtTaskResourceRefVO` existingRef
       ON existingRef.taskUuid = tmp.taskUuid
      AND existingRef.resourceUuid = tmp.sourceVolumeUuid
      AND existingRef.resourceType = 'VolumeVO'
WHERE existingRef.id IS NULL
GROUP BY tmp.taskUuid, tmp.sourceVolumeUuid;

DELETE ref FROM `zstack`.`CbtTaskResourceRefVO` ref
INNER JOIN `tmpCbtTaskTemporaryVolumeRef` tmp
        ON tmp.taskUuid = ref.taskUuid
       AND tmp.tempVolumeUuid = ref.resourceUuid
WHERE ref.resourceType = 'VolumeVO';

DROP TEMPORARY TABLE IF EXISTS `tmpCbtTaskTemporaryVolumeRef`;

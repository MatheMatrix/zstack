-- Feature: VM HA enhance | ZSV-6282
CREATE TABLE IF NOT EXISTS `zstack`.`VmHaVO` (
    `vmUuid` char(32) not null,
    `haLevel` varchar(32) default 'None',
    `inhibitionReason` varchar(255) default null,
    `inhibitionTime` timestamp default '0000-00-00 00:00:00',
    `lastOpDate` timestamp not null default '0000-00-00 00:00:00' ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp not null default '0000-00-00 00:00:00',
    PRIMARY KEY (`vmUuid`),
    CONSTRAINT `fkVmHaVOVmInstanceVO` FOREIGN KEY (`vmUuid`) REFERENCES `VmInstanceEO` (`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- test
insert into VmHaVO (vmUuid) select uuid from VmInstanceVO;
update VmHaVO set lastOpDate=NOW(), createDate=NOW();

DELIMITER $$
CREATE PROCEDURE UpdateVmHaVO() BEGIN
    DECLARE vmUuid char(32);
    DECLARE haLevel varchar(32);
    DECLARE createDate timestamp;
    DECLARE done INT DEFAULT FALSE;
    DECLARE cur1 CURSOR FOR
        select `resourceUuid`, substring(`tag`, 5) from `SystemTagVO` where `tag` like 'ha::%';
    DECLARE cur2 CURSOR FOR
        select `resourceUuid`, `createDate` from `SystemTagVO` where `tag` = 'inhibitHA';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur1;
    read_loop: LOOP
        FETCH cur1 INTO vmUuid, haLevel;
        IF done THEN
            LEAVE read_loop;
        END IF;

        UPDATE `zstack`.`VmHaVO` SET `haLevel` = haLevel WHERE `vmUuid` = vmUuid;
    END LOOP;
    CLOSE cur1;

    SET done = FALSE;
    OPEN cur2;
    read_loop: LOOP
        FETCH cur2 INTO vmUuid, createDate;
        IF done THEN
            LEAVE read_loop;
        END IF;

        UPDATE `zstack`.`VmHaVO` SET `inhibitionReason` = 'Unknown', `inhibitionTime` = createDate WHERE `vmUuid` = vmUuid;
    END LOOP;
    CLOSE cur2;
END $$
DELIMITER ;

CALL UpdateVmHaVO();
DROP PROCEDURE IF EXISTS UpdateVmHaVO;

-- in version zsv_4.3.0
-- Improvement: HA | ZSV-6282

delete from `zstack`.`ResourceConfigVO` where `name`='vm.ha.level' and `category`='ha' and `resourceType`='VmInstanceVO';

insert into
    `zstack`.`ResourceConfigVO` (`uuid`, `name`, `category`, `value`, `resourceUuid`, `resourceType`, `lastOpDate`, `createDate`)
    select
        replace(uuid(),"-","") as `uuid`,
        'vm.ha.level' as `name`,
        'ha' as `category`,
        'None' as `value`,
        vm.uuid as `resourceUuid`,
        'VmInstanceVO' as `resourceType`,
        Now() as `lastOpDate`,
        Now() as `createDate`
    from VmInstanceVO vm;

DELIMITER $$
CREATE PROCEDURE transferHaTagToResourceConfig()
    BEGIN
        DECLARE vmUuid CHAR(32);
        DECLARE haLevel VARCHAR(32);
        DECLARE done INT DEFAULT FALSE;
        DECLARE tagCursor CURSOR FOR
            select `resourceUuid`, substring(`tag`, 5) from `SystemTagVO` where `tag` like 'ha::%';
        DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

        OPEN tagCursor;
        read_loop: LOOP
            FETCH tagCursor INTO vmUuid, haLevel;
            IF done THEN
                LEAVE read_loop;
            END IF;

            update `zstack`.`ResourceConfigVO` set `value` = haLevel
                where `resourceUuid` = vmUuid and `name` = 'vm.ha.level' and `category` = 'ha' and `resourceType` = 'VmInstanceVO';
        END LOOP;
        CLOSE tagCursor;
        SELECT CURTIME();
    END $$
DELIMITER ;

call transferHaTagToResourceConfig();
DROP PROCEDURE IF EXISTS transferHaTagToResourceConfig;

delete from `zstack`.`SystemTagVO` where `tag` like 'ha::%';
update `zstack`.`ResourceConfigVO` set `description` = 'High-Availability (HA) state of VM'
    where `name`='vm.ha.level' and `category`='ha';

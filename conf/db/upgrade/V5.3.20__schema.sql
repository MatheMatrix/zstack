CREATE TABLE IF NOT EXISTS `zstack`.`LogServerVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `name` varchar(255) NOT NULL,
    `description` varchar(2048) NULL,
    `category` varchar(255) NOT NULL,
    `type` varchar(255) NOT NULL,
    `level` varchar(255) NULL,
    `configuration` text NOT NULL,
    `lastOpDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
    PRIMARY KEY  (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP PROCEDURE IF EXISTS migrateJsonLabelToLogServerVO;
DELIMITER $$
CREATE PROCEDURE migrateJsonLabelToLogServerVO()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE logServerUuid VARCHAR(32);
    DECLARE logCreateDate timestamp;
    DECLARE logLastOpDate timestamp;
    DECLARE logLabelValue TEXT;
    DECLARE logLabelKey varchar(128);
    DECLARE name VARCHAR(32);
    DECLARE type VARCHAR(32);
    DECLARE level VARCHAR(32);
    DECLARE logConfiguration TEXT;
    DECLARE description VARCHAR(2048);


    DECLARE cur CURSOR FOR SELECT DISTINCT labelKey, labelValue, createDate, lastOpDate FROM zstack.JsonLabelVO WHERE labelKey like 'log4j2-%%';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO logLabelKey, logLabelValue, logCreateDate, logLastOpDate;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SET name = JSON_UNQUOTE(JSON_EXTRACT(logLabelValue, '$.name'));
        SET type = JSON_UNQUOTE(JSON_EXTRACT(logLabelValue, '$.type'));
        SET level = JSON_UNQUOTE(JSON_EXTRACT(logLabelValue, '$.level'));
        SET logConfiguration = JSON_UNQUOTE(JSON_EXTRACT(logLabelValue, '$.configuration'));
        SET description = JSON_UNQUOTE(JSON_EXTRACT(logLabelValue, '$.description'));

        IF NOT EXISTS(SELECT * from LogServerVO where configuration = logConfiguration) THEN
            SET logServerUuid = (REPLACE(UUID(), '-', ''));

            INSERT zstack.ResourceVO(uuid, resourceName, resourceType, concreteResourceType)
            VALUES (logServerUuid, name, 'LogServerVO', 'org.zstack.log.server.LogServerVO');

            INSERT zstack.LogServerVO(uuid, name, description, category, type, level, configuration, lastOpDate, createDate)
            VALUES(logServerUuid, name, description, 'ManagementNodeLog', 'Log4j2', level, logConfiguration, logLastOpDate, logCreateDate);

            INSERT zstack.AccountResourceRefVO(accountUuid, ownerAccountUuid, resourceUuid, resourceType, permission, isShared, lastOpDate, createDate, concreteResourceType)
            VALUES('36c27e8ff05c4780bf6d2fa65700f22e', '36c27e8ff05c4780bf6d2fa65700f22e', logServerUuid, 'LogServerVO', 2, 0, logLastOpDate, logCreateDate, 'org.zstack.log.server.LogServerVO');
        END IF;

    END LOOP;
    CLOSE cur;

    SELECT CURTIME();
END $$
DELIMITER ;
call migrateJsonLabelToLogServerVO();
DROP PROCEDURE IF EXISTS migrateJsonLabelToLogServerVO;
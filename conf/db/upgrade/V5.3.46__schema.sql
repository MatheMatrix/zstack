-- Migration script to update AuditVO table from AccountId to ProjectId.
-- This script efficiently migrates data using a single JOIN operation.

DELIMITER $$
DROP PROCEDURE IF EXISTS changeAccountIdToProjectIdForAuditVO$$
CREATE PROCEDURE changeAccountIdToProjectIdForAuditVO()
pro_label: BEGIN
    DECLARE v_total_updated INT DEFAULT 0;

    IF (SELECT COUNT(*) FROM IAM2ProjectAccountRefVO) = 0 THEN
        SELECT 'No IAM2ProjectAccountRefVO records found, skipping migration.' AS message;
        LEAVE pro_label;
    END IF;

    SELECT 'Starting migration of AuditsVO records from AccountUuid to ProjectUuid...' AS message;

    UPDATE AuditsVO a
    JOIN IAM2ProjectAccountRefVO i ON a.resourceUuid = i.accountUuid
    SET
        a.resourceUuid = i.projectUuid,
        a.resourceType = 'IAM2ProjectVO'
    WHERE
        a.apiName = 'org.zstack.header.identity.APIUpdateQuotaMsg'
        AND a.resourceType = 'AccountVO';
    SET v_total_updated = ROW_COUNT();
    SELECT CONCAT('Migration completed successfully. Total records updated: ', v_total_updated) AS message;
END pro_label$$

DELIMITER ;
CALL changeAccountIdToProjectIdForAuditVO();
DROP PROCEDURE IF EXISTS changeAccountIdToProjectIdForAuditVO;

CREATE TABLE IF NOT EXISTS `zstack`.`KoAlSecretResourcePoolVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `managementIp` varchar(32) NOT NULL,
    `port` int unsigned NOT NULL,
    `secretKey` varchar(255) NOT NULL,
    PRIMARY KEY  (`uuid`),
    CONSTRAINT fkKoAlSecretResourcePoolVOSecretResourcePoolVO FOREIGN KEY (uuid) REFERENCES SecretResourcePoolVO (uuid) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE `zstack`.`ModelEvaluationTaskVO` ADD taskRequestInJson VARCHAR(8192) DEFAULT NULL;
ALTER TABLE `zstack`.`ModelEvaluationTaskVO` ADD type VARCHAR(32) DEFAULT NULL;
ALTER TABLE `zstack`.`ModelEvaluationTaskVO` MODIFY `evaluatedServiceGroupUuid` varchar(32) DEFAULT NULL;
ALTER TABLE `zstack`.`ModelEvaluationTaskVO` MODIFY datasetUuid VARCHAR(32) DEFAULT NULL;

INSERT INTO SystemTagVO
(`uuid`, `resourceUuid`, `resourceType`, `inherent`, `type`, `tag`, `createDate`, `lastOpDate`)
SELECT
    REPLACE(UUID(), '-', ''),                  -- 生成不含连字符的uuid
    uuid,                                       -- 使用DatasetVO的uuid作为resourceUuid
    'DatasetVO',                               -- resourceType
    1,                                         -- inherent
    'System',                                  -- type
    'dataset::usage::scenarios::ModelEval',    -- tag
    CURRENT_TIMESTAMP(),                       -- createDate
    CURRENT_TIMESTAMP()                        -- lastOpDate
FROM DatasetVO
WHERE system = true;

INSERT INTO SystemTagVO
(`uuid`, `resourceUuid`, `resourceType`, `inherent`, `type`, `tag`, `createDate`, `lastOpDate`)
SELECT
    REPLACE(UUID(), '-', ''),                  -- 生成不含连字符的uuid
    uuid,                                       -- 使用DatasetVO的uuid作为resourceUuid
    'DatasetVO',                               -- resourceType
    1,                                         -- inherent
    'System',                                  -- type
    'dataset::datatype::Text',                 -- tag
    CURRENT_TIMESTAMP(),                       -- createDate
    CURRENT_TIMESTAMP()                        -- lastOpDate
FROM DatasetVO
WHERE system = true;

-- 重命名表
RENAME TABLE `zstack`.`ContainerManagementVmVO` TO `zstack`.`ContainerManagementEndpointVO`;

-- 删除列
ALTER TABLE `zstack`.`ContainerManagementEndpointVO`
DROP COLUMN `vmInstanceUuid`;


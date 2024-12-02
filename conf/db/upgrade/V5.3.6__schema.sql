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

CREATE TABLE `zstack`.`NativeClusterVO` (
    `uuid` varchar(32) NOT NULL UNIQUE COMMENT 'native cluster uuid',
    `endpointUuid` varchar(32) NOT NULL COMMENT 'container endpoint uuid',
    `bizUrl` varchar(255) DEFAULT NULL COMMENT 'business network url',
    `masterUrl` varchar(255) DEFAULT NULL COMMENT 'management network url',
    `kubeConfig` text COMMENT 'kubernetes configuration',
    `id` bigint(20) DEFAULT NULL COMMENT 'kubernetes cluster id',
    `prometheusURL` varchar(255) DEFAULT NULL COMMENT 'prometheus monitoring url',
    `version` varchar(64) DEFAULT NULL COMMENT 'kubernetes version',
    `nodeCount` int DEFAULT NULL COMMENT 'number of nodes',
    `createType` varchar(32) DEFAULT NULL COMMENT 'cluster creation type',
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE `zstack`.`NativeHostVO` (
    `uuid` varchar(32) NOT NULL UNIQUE COMMENT 'host uuid',
    `endpointUuid` varchar(32) NOT NULL COMMENT 'container endpoint uuid',
    PRIMARY KEY  (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

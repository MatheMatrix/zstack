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

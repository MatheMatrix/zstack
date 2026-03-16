CREATE TABLE IF NOT EXISTS `zstack`.`ExternalTenantResourceRefVO` (
    `id`            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `source`        VARCHAR(64)  NOT NULL COMMENT '来源服务标识 (zcf, svcX, ...)',
    `tenantId`      VARCHAR(128) NOT NULL COMMENT '外部租户标识',
    `userId`        VARCHAR(128) DEFAULT NULL COMMENT '外部用户标识（可选）',
    `resourceUuid`  VARCHAR(32)  NOT NULL COMMENT '资源 UUID',
    `resourceType`  VARCHAR(256) NOT NULL COMMENT '资源类型 (VO SimpleName)',
    `accountUuid`   VARCHAR(32)  NOT NULL COMMENT '关联的 ZStack Account',
    `createDate`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lastOpDate`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_source_tenant (`source`, `tenantId`),
    INDEX idx_source_tenant_user (`source`, `tenantId`, `userId`),
    INDEX idx_resource (`resourceUuid`),
    UNIQUE KEY uk_resource_source_tenant (`resourceUuid`, `source`, `tenantId`),
    CONSTRAINT fk_ext_tenant_resource FOREIGN KEY (`resourceUuid`)
        REFERENCES `ResourceVO`(`uuid`) ON DELETE CASCADE,
    CONSTRAINT fk_ext_tenant_account FOREIGN KEY (`accountUuid`)
        REFERENCES `AccountVO`(`uuid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
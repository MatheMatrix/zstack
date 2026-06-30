ALTER TABLE ThirdpartyPlatformVO ADD COLUMN receiveMode varchar(32) NOT NULL DEFAULT 'PULL';
ALTER TABLE ThirdpartyPlatformVO ADD COLUMN sourceUuid varchar(32) DEFAULT NULL;
ALTER TABLE ThirdpartyPlatformVO ADD COLUMN sourceSiteId varchar(32) DEFAULT NULL;
CREATE UNIQUE INDEX idxThirdpartyPlatformTypeSourceUuid ON ThirdpartyPlatformVO(type, sourceUuid);

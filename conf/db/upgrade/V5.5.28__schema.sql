CREATE TABLE IF NOT EXISTS `LicenseIntegrityStateVO` (
    `id` varchar(64) NOT NULL UNIQUE,
    `maxTimeSeenMillis` bigint NOT NULL DEFAULT 0,
    `maxLicenseVersion` bigint NOT NULL DEFAULT 0,
    `presetHardwareFingerprint` varchar(128) DEFAULT NULL,
    `createDate` timestamp NULL DEFAULT NULL,
    `lastOpDate` timestamp NULL DEFAULT NULL,
    PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

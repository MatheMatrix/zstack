CREATE TABLE IF NOT EXISTS `zstack`.`HbaDeviceVO` (
    `uuid` varchar(32) not null unique,
    `hostUuid` varchar(32) default null,
    `name` varchar(255) default null,
    `hbaType`  varchar(64) default null,
    `createDate` timestamp not null default '0000-00-00 00:00:00',
    `lastOpDate` timestamp not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fkHBADeviceVOHostVO FOREIGN KEY (hostUuid) REFERENCES HostEO (uuid) ON DELETE CASCADE,
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`FcHbaDeviceVO` (
    `uuid` varchar(32) not null unique,
    `portName` varchar(255) default null,
    `portState`  varchar(64) default null,
    `supportedSpeeds`  varchar(255) default null,
    `speed`  varchar(255) default null,
    `symbolicName`  varchar(255) default null,
    `supportedClasses`  varchar(255) default null,
    `nodeName` varchar(255) default null,
    CONSTRAINT fkFcHbaDeviceVO FOREIGN KEY (uuid) REFERENCES HbaDeviceVO (uuid) ON DELETE CASCADE,
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`VmHaVO` (
    `uuid` char(32) not null unique,
    `haLevel` varchar(64) not null default 'Undefined',
    `haLevelUpdateTime` timestamp not null default CURRENT_TIMESTAMP,
    `inhibitionReason` varchar(255) default null,
    `inhibitionTime` timestamp default '0000-00-00 00:00:00',
    CONSTRAINT fkVmHaVOVmInstanceVO FOREIGN KEY (uuid) REFERENCES VmInstanceEO (uuid) ON DELETE CASCADE,
    PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO `zstack`.`VmHaVO` (`uuid`, `haLevel`)
    SELECT `uuid`, 'None' FROM `zstack`.`VmInstanceEO`;

UPDATE `zstack`.`VmHaVO`
    SET `haLevel` = 'NeverStop'
    WHERE `uuid` IN (
        SELECT `resourceUuid` FROM `SystemTagVO` WHERE `tag` = 'ha::NeverStop'
    );

UPDATE `zstack`.`VmHaVO`
    SET `haLevel` = 'OnHostFailure'
    WHERE `uuid` IN (
        SELECT `resourceUuid` FROM `SystemTagVO` WHERE `tag` = 'ha::OnHostFailure'
    );

CALL INSERT_COLUMN('HostEO', 'nqn', 'varchar(256)', 1, NULL, 'managementIp');
DROP VIEW IF EXISTS `zstack`.`HostVO`;
CREATE VIEW `zstack`.`HostVO` AS SELECT uuid, zoneUuid, clusterUuid, name, description, managementIp, hypervisorType, state, status, architecture, nqn, createDate, lastOpDate FROM `zstack`.`HostEO` WHERE deleted IS NULL;

UPDATE GlobalConfigVO
    SET `description` = 'Allow the use of other hosts with the same storage but different clusters to check if the target host is still connected.'
    WHERE category = 'ha' and name = 'allow.slibing.cross.clusters';
UPDATE GlobalConfigVO
    SET `value` = 'true', `defaultValue` = 'true'
    WHERE `value` = 'false' and `defaultValue` = 'false' and category = 'ha' and name = 'allow.slibing.cross.clusters';

-- Feature: SSO Refactor | ZSV-5531

call RENAME_TABLE('OAuth2ClientVO', 'OAuth2ClientVODeprecated');
call RENAME_TABLE('CasClientVO', 'CasClientVODeprecated');

CREATE TABLE IF NOT EXISTS `zstack`.`OAuth2ClientVO` (
    `uuid` char(32) not null unique,
    `clientId` varchar(255) not null,
    `clientSecret` varchar(255),
    `grantType` varchar(64) not null,
    `loginMNUrl` varchar(255) default null,
    `redirectUrl` varchar(255) default null,
    `authorizationUrl` varchar(255) default null,
    `tokenUrl` varchar(255) not null,
    `userinfoUrl` varchar(255) default null,
    `logoutUrl` varchar(255) default null,
    `usernameProperty` varchar(255) not null,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkOAuth2ClientVOThirdPartyAccountSourceVO` FOREIGN KEY (`uuid`) REFERENCES `ThirdPartyAccountSourceVO` (`uuid`) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`CasClientVO` (
    `uuid` char(32) not null unique,
    `loginMNUrl` varchar(255) default null,
    `redirectUrl` varchar(255) default null,
    `casServerLoginUrl` varchar(255) not null,
    `casServerUrlPrefix` varchar(255) not null,
    `serverName` varchar(255) not null,
    `state` varchar(128) not null,
    `usernameProperty` varchar(255) not null,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkCasClientVOThirdPartyAccountSourceVO` FOREIGN KEY (`uuid`) REFERENCES `ThirdPartyAccountSourceVO` (`uuid`) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Transfer data       OAuth2ClientVODeprecated + SSOClientVO -> OAuth2ClientVO + ThirdPartyAccountSourceVO
-- Note: usernameProperty from SystemTagVO (tag like 'ssoUseAsLoginName::%')
INSERT INTO `zstack`.`ThirdPartyAccountSourceVO`
    (`uuid`, `description`, `type`, `createAccountStrategy`, `deleteAccountStrategy`, `createDate`)
SELECT
    sso.uuid, sso.description, 'OAuth2', 'CreateAccount', 'NoAction', sso.createDate
FROM
    OAuth2ClientVODeprecated oa
        LEFT JOIN SSOClientVO sso ON oa.uuid = sso.uuid;

INSERT INTO `zstack`.`OAuth2ClientVO`
    (`uuid`, `clientId`, `clientSecret`, `grantType`, `loginMNUrl`, `redirectUrl`, `authorizationUrl`, `tokenUrl`, `userinfoUrl`, `logoutUrl`, `usernameProperty`)
SELECT
    oa.uuid, oa.clientId, oa.clientSecret, oa.grantType, sso.loginMNUrl, sso.redirectUrl, oa.authorizationUrl, oa.tokenUrl, oa.userinfoUrl, oa.logoutUrl, substring(tag.tag, 20)
FROM
    OAuth2ClientVODeprecated oa
        LEFT JOIN SSOClientVO sso ON oa.uuid = sso.uuid
        LEFT JOIN SystemTagVO tag ON tag.resourceUuid = sso.uuid
WHERE
    tag.tag LIKE 'ssoUseAsLoginName::%';

-- Transfer data       CasClientVODeprecated + SSOClientVO -> CasClientVO + ThirdPartyAccountSourceVO
-- Note: usernameProperty from SystemTagVO (tag like 'ssoUseAsLoginName::%')
INSERT INTO `zstack`.`ThirdPartyAccountSourceVO`
    (`uuid`, `description`, `type`, `createAccountStrategy`, `deleteAccountStrategy`, `createDate`)
SELECT
    sso.uuid, sso.description, 'CAS', 'CreateAccount', 'NoAction', sso.createDate
FROM
    CasClientVODeprecated cas
        LEFT JOIN SSOClientVO sso ON cas.uuid = sso.uuid;

INSERT INTO `zstack`.`CasClientVO`
    (`uuid`, `loginMNUrl`, `redirectUrl`, `casServerLoginUrl`, `casServerUrlPrefix`, `serverName`, `state`, `usernameProperty`)
SELECT
    cas.uuid, sso.loginMNUrl, sso.redirectUrl, cas.casServerLoginUrl, cas.casServerUrlPrefix, cas.serverName, cas.state, substring(tag.tag, 20)
FROM
    CasClientVODeprecated cas
        LEFT JOIN SSOClientVO sso ON cas.uuid = sso.uuid
        LEFT JOIN SystemTagVO tag ON tag.resourceUuid = sso.uuid
WHERE
    tag.tag LIKE 'ssoUseAsLoginName::%';

-- Transfer data       ThirdClientAccountRefVO -> AccountThirdPartyAccountSourceRefVO
-- Note: credentials = '::{resourceUuid}'
INSERT INTO `zstack`.`AccountThirdPartyAccountSourceRefVO`
    (`credentials`, `accountSourceUuid`, `accountUuid`, `createDate`)
SELECT
    concat('::', t.resourceUuid), t.clientUuid, t.resourceUuid, t.createDate
FROM
    ThirdClientAccountRefVO t;

CALL DROP_FOREIGN_KEY('SSOTokenVO', 'fkSSOTokenVOClientVO');
CALL ADD_CONSTRAINT('SSOTokenVO', 'fkSSOTokenVOThirdPartyAccountSourceVO', 'clientUuid', 'ThirdPartyAccountSourceVO', 'uuid', 'CASCADE');

CALL DROP_FOREIGN_KEY('SSORedirectTemplateVO', 'fkSSORedirectTemplateClientVO');
CALL ADD_CONSTRAINT('SSORedirectTemplateVO', 'fkSSORedirectTemplateVOThirdPartyAccountSourceVO', 'clientUuid', 'ThirdPartyAccountSourceVO', 'uuid', 'CASCADE');

DROP TABLE IF EXISTS `OAuth2ClientVODeprecated`;
DROP TABLE IF EXISTS `CasClientVODeprecated`;
DROP TABLE IF EXISTS `ThirdClientAccountRefVO`;
DROP TABLE IF EXISTS `SSOClientVO`;

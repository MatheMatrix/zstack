CREATE TABLE IF NOT EXISTS `zstack`.`OAuth2AccountClientVO` (
    `uuid` char(32) not null unique,
    `clientType` varchar(255) not null,
    `loginMNUrl` varchar(255) not null,
    `redirectUrl` varchar(255) default null,
    `clientId` varchar(255) not null,
    `clientSecret` varchar(255) default null,
    `authorizationUrl` varchar(255) default null,
    `tokenUrl` varchar(255) not null,
    `grantType` varchar(64) not null,
    `userinfoUrl` varchar(255) default null,
    `logoutUrl` varchar(255) default null,
    `usernameProperty` varchar(255) not null,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkOAuth2AccountClientVOThirdPartyAccountSourceVO` FOREIGN KEY (`uuid`) REFERENCES `ThirdPartyAccountSourceVO` (`uuid`) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `zstack`.`SSOUrlTemplateVO` (
    `uuid` char(32) not null unique,
    `name` varchar(255) default null,
    `description` varchar(2048) default null,
    `clientUuid` char(32) not null,
    `redirectTemplate` varchar(2048) not null,
    `lastOpDate` timestamp on update current_timestamp,
    `createDate` timestamp,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkSSOUrlTemplateVOThirdPartyAccountSourceVO` FOREIGN KEY (`clientUuid`) REFERENCES `ThirdPartyAccountSourceVO` (`uuid`) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE `SSOTokenVO` DROP FOREIGN KEY fkSSOTokenVOClientVO;
ALTER TABLE `SSOTokenVO` ADD CONSTRAINT fkSSOTokenVOThirdPartyAccountSourceVO FOREIGN KEY (clientUuid) REFERENCES ThirdPartyAccountSourceVO (uuid) ON DELETE CASCADE;

CREATE TABLE IF NOT EXISTS `zstack`.`CasAccountClientVO` (
    `uuid` char(32) not null unique,
    `loginMNUrl` varchar(255) not null,
    `redirectUrl` varchar(255) default null,
    `casServerLoginUrl` varchar(255) not null,
    `casServerUrlPrefix` varchar(255) not null,
    `serverName` varchar(255) not null,
    `state` varchar(64) not null,
    `usernameProperty` varchar(255) not null,
    PRIMARY KEY (`uuid`),
    CONSTRAINT `fkCasAccountClientVOThirdPartyAccountSourceVO` FOREIGN KEY (`uuid`) REFERENCES `ThirdPartyAccountSourceVO` (`uuid`) ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


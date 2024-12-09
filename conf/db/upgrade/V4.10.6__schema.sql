-- Feature: SSO Refactor | ZSV-5531

call RENAME_TABLE('OAuth2ClientVO', 'OAuth2ClientVODeprecated');
call RENAME_TABLE('CasClientVO', 'CasClientVODeprecated');

create table if not exists `zstack`.`OAuth2ClientVO` (
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
    primary key (`uuid`),
    constraint `fkOAuth2ClientVOThirdPartyAccountSourceVO` foreign key (`uuid`) references `ThirdPartyAccountSourceVO` (`uuid`) on update restrict on delete cascade
) ENGINE=InnoDB default CHARSET=utf8;

create table if not exists `zstack`.`CasClientVO` (
    `uuid` char(32) not null unique,
    `loginMNUrl` varchar(255) default null,
    `redirectUrl` varchar(255) default null,
    `casServerLoginUrl` varchar(255) not null,
    `casServerUrlPrefix` varchar(255) not null,
    `serverName` varchar(255) not null,
    `state` varchar(128) not null,
    `usernameProperty` varchar(255) not null,
    primary key (`uuid`),
    constraint `fkCasClientVOThirdPartyAccountSourceVO` foreign key (`uuid`) references `ThirdPartyAccountSourceVO` (`uuid`) on update restrict on delete cascade
) ENGINE=InnoDB default CHARSET=utf8;

-- Transfer data       OAuth2ClientVODeprecated + SSOClientVO -> OAuth2ClientVO + ThirdPartyAccountSourceVO
-- Note: usernameProperty from SystemTagVO (tag like 'ssoUseAsLoginName::%')
insert into `zstack`.`ThirdPartyAccountSourceVO`
    (`uuid`, `description`, `type`, `createAccountStrategy`, `deleteAccountStrategy`, `createDate`)
select
    sso.uuid, sso.description, 'OAuth2', 'CreateAccount', 'NoAction', sso.createDate
from
    OAuth2ClientVODeprecated oa
        left join SSOClientVO sso on oa.uuid = sso.uuid;

insert into `zstack`.`OAuth2ClientVO`
    (`uuid`, `clientId`, `clientSecret`, `grantType`, `loginMNUrl`, `redirectUrl`, `authorizationUrl`, `tokenUrl`, `userinfoUrl`, `logoutUrl`, `usernameProperty`)
select
    oa.uuid, oa.clientId, oa.clientSecret, oa.grantType, sso.loginMNUrl, sso.redirectUrl, oa.authorizationUrl, oa.tokenUrl, oa.userinfoUrl, oa.logoutUrl, substring(tag.tag, 20)
from
    OAuth2ClientVODeprecated oa
        left join SSOClientVO sso on oa.uuid = sso.uuid
        left join SystemTagVO tag on tag.resourceUuid = sso.uuid
where
    tag.tag like 'ssoUseAsLoginName::%';

-- Transfer data       CasClientVODeprecated + SSOClientVO -> CasClientVO + ThirdPartyAccountSourceVO
-- Note: usernameProperty from SystemTagVO (tag like 'ssoUseAsLoginName::%')
insert into `zstack`.`ThirdPartyAccountSourceVO`
    (`uuid`, `description`, `type`, `createAccountStrategy`, `deleteAccountStrategy`, `createDate`)
select
    sso.uuid, sso.description, 'CAS', 'CreateAccount', 'NoAction', sso.createDate
from
    CasClientVODeprecated cas
        left join SSOClientVO sso on cas.uuid = sso.uuid;

insert into `zstack`.`CasClientVO`
    (`uuid`, `loginMNUrl`, `redirectUrl`, `casServerLoginUrl`, `casServerUrlPrefix`, `serverName`, `state`, `usernameProperty`)
select
    cas.uuid, sso.loginMNUrl, sso.redirectUrl, cas.casServerLoginUrl, cas.casServerUrlPrefix, cas.serverName, cas.state, substring(tag.tag, 20)
from
    CasClientVODeprecated cas
        left join SSOClientVO sso on cas.uuid = sso.uuid
        left join SystemTagVO tag on tag.resourceUuid = sso.uuid
where
    tag.tag like 'ssoUseAsLoginName::%';

-- Transfer data       ThirdClientAccountRefVO -> AccountThirdPartyAccountSourceRefVO
-- Note: credentials = '::{resourceUuid}'
insert into `zstack`.`AccountThirdPartyAccountSourceRefVO`
    (`credentials`, `accountSourceUuid`, `accountUuid`, `createDate`)
select
    concat('::', t.resourceUuid), t.clientUuid, t.resourceUuid, t.createDate
from
    ThirdClientAccountRefVO t;

call DROP_FOREIGN_KEY('SSOTokenVO', 'fkSSOTokenVOClientVO');
call ADD_CONSTRAINT('SSOTokenVO', 'fkSSOTokenVOThirdPartyAccountSourceVO', 'clientUuid', 'ThirdPartyAccountSourceVO', 'uuid', 'CASCADE');

call DROP_FOREIGN_KEY('SSORedirectTemplateVO', 'fkSSORedirectTemplateClientVO');
call ADD_CONSTRAINT('SSORedirectTemplateVO', 'fkSSORedirectTemplateVOThirdPartyAccountSourceVO', 'clientUuid', 'ThirdPartyAccountSourceVO', 'uuid', 'CASCADE');

drop table if exists `OAuth2ClientVODeprecated`;
drop table if exists `CasClientVODeprecated`;
drop table if exists `ThirdClientAccountRefVO`;
drop table if exists `SSOClientVO`;

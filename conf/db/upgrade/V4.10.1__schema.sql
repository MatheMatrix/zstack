create table if not exists `zstack`.`OAuth2AccountClientVO` (
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
    primary key (`uuid`),
    constraint `fkOAuth2AccountClientVOThirdPartyAccountSourceVO` foreign key (`uuid`) references `ThirdPartyAccountSourceVO` (`uuid`) on update restrict on delete cascade
) ENGINE=InnoDB default CHARSET=utf8;

create table if not exists `zstack`.`SSOUrlTemplateVO` (
    `uuid` char(32) not null unique,
    `name` varchar(255) default null,
    `description` varchar(2048) default null,
    `clientUuid` char(32) not null,
    `redirectTemplate` varchar(2048) not null,
    `lastOpDate` timestamp on update current_timestamp,
    `createDate` timestamp,
    primary key (`uuid`),
    constraint `fkSSOUrlTemplateVOThirdPartyAccountSourceVO` foreign key (`clientUuid`) references `ThirdPartyAccountSourceVO` (`uuid`) on update restrict on delete cascade
) ENGINE=InnoDB default CHARSET=utf8;

alter table `SSOTokenVO` drop foreign key fkSSOTokenVOClientVO;
alter table `SSOTokenVO` add constraint fkSSOTokenVOThirdPartyAccountSourceVO foreign key (clientUuid) references ThirdPartyAccountSourceVO (uuid) on delete cascade;

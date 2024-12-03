-- Feature: ZStone Support | ZSV-7443

create table if not exists `zstack`.`ZStoneVO` (
    `uuid` char(32) not null unique,
    `name` varchar(255) not null,
    `username` varchar(255) not null,
    `password` varchar(255) default null,
    `managementIp` varchar(255) not null,
    `authorizationServer` varchar(32) not null,
    `logInPort` int(11) not null,
    `apiPort` int(11) not null,
    `logInUrl` varchar(32) not null,
    `lastOpDate` timestamp on update CURRENT_TIMESTAMP,
    `createDate` timestamp,
    primary key (`uuid`)
) ENGINE=InnoDB default CHARSET=utf8;

-- Feature: ZCE-X Support | ZSV-7444

create table if not exists `zstack`.`ZceXVO` (
    `uuid` char(32) not null unique,
    `name` varchar(255) not null,
    `managementIp` varchar(255) not null,
    `apiPort` int(11) not null,
    `lastOpDate` timestamp on update CURRENT_TIMESTAMP,
    `createDate` timestamp,
    primary key (`uuid`)
) ENGINE=InnoDB default CHARSET=utf8;

create table if not exists `zstack`.`ZceXThirdPartyPlatformAlertRefVO` (
    `id` bigint unsigned not null unique AUTO_INCREMENT,
    `zceXUuid` char(32) not null,
    `thirdPartyPlatformUuid` char(32) not null,
    `createDate` timestamp,
    primary key (`id`),
    constraint `fkZceXThirdPartyPlatformAlertRefZceX` foreign key (`zceXUuid`) references `ZceXVO` (`uuid`) on delete cascade,
    constraint `fkZceXThirdPartyPlatformAlertRefThirdPartyPlatform` foreign key (`thirdPartyPlatformUuid`) references `ThirdpartyPlatformVO` (`uuid`) on delete cascade
) ENGINE=InnoDB default CHARSET=utf8;

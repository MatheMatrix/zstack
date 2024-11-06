-- Feature: ZStone Support | ZSV-7443

create table if not exists `zstack`.`ZStoneVO` (
    `uuid` char(32) not null unique,
    `name` varchar(255) not null,
    `username` varchar(255) not null,
    `managementIp` varchar(255) not null,
    `authorizationServer` varchar(32) not null,
    `logInPort` int(11) not null,
    `apiPort` int(11) not null,
    `logInUrl` varchar(32) not null,
    `lastOpDate` timestamp on update CURRENT_TIMESTAMP,
    `createDate` timestamp,
    primary key (`uuid`)
) ENGINE=InnoDB default CHARSET=utf8;

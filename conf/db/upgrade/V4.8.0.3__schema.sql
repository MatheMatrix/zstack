-- in version zsv_4.2.0
-- Feature: USB device support sharing | ZSV-4726
delete from ResourceVO where resourceType = 'UsbDeviceVO' and uuid not in (select uuid from UsbDeviceVO);

insert into AccountResourceRefVO (`accountUuid`,`ownerAccountUuid`,`resourceUuid`,`resourceType`,`permission`,`isShared`,`lastOpDate`,`createDate`,`concreteResourceType`)
select '36c27e8ff05c4780bf6d2fa65700f22e', '36c27e8ff05c4780bf6d2fa65700f22e', uuid, resourceType, 2, false, NOW(), NOW(), 'org.zstack.usbDevice.UsbDeviceVO'
    from ResourceVO where resourceType = 'UsbDeviceVO';

-- Feature: support OVF uploading breakpoint continuation | ZSV-4467
alter table `zstack`.`LongJobVO` modify `uuid` char(32) not null;
alter table `zstack`.`LongJobVO` add column `parentUuid` char(32) default null;

-- Feature: add VmInstanceTemplate
CREATE TABLE  `zstack`.`VmInstanceTemplateVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `name` varchar(255) NOT NULL,
    `vmInstanceUuid` varchar(32) DEFAULT NULL,
    `originalType` varchar(64) NOT NULL,
    `lastOpDate` timestamp ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp,
    `deleted` varchar(255) DEFAULT NULL,
    PRIMARY KEY  (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `zstack`.`VolumeTemplateVO` (
    `uuid` varchar(32) NOT NULL UNIQUE,
    `volumeUuid` varchar(32) DEFAULT NULL,
    `originalType` varchar(64) NOT NULL,
    `lastOpDate` timestamp ON UPDATE CURRENT_TIMESTAMP,
    `createDate` timestamp,
    `deleted` varchar(255) DEFAULT NULL,
    PRIMARY KEY  (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

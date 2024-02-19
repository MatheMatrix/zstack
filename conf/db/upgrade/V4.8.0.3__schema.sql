-- in version zsv_4.2.0
-- from issue: ZSV-4726 | feature: sharable USB device

ALTER TABLE `zstack`.`UsbDeviceVO` ADD COLUMN `accountUuid` char(32) DEFAULT NULL;
UPDATE `zstack`.`UsbDeviceVO` SET `accountUuid` = '36c27e8ff05c4780bf6d2fa65700f22e' WHERE `accountUuid` IS NULL;

-- from issue: PLEASE MODIFY HERE


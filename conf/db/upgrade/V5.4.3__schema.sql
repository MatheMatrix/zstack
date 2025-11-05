ALTER TABLE `zstack`.`VmVfNicVO`
    ADD COLUMN `secondaryPciDeviceUuid` varchar(32) DEFAULT NULL AFTER `pciDeviceUuid`;
ALTER TABLE `zstack`.`VmVfNicVO`
    ADD INDEX `idxVmVfNicVOSecondaryPciDeviceUuid` (`secondaryPciDeviceUuid`);
ALTER TABLE `zstack`.`VmVfNicVO`
    ADD CONSTRAINT `fkVmVfNicVOSecondaryPciDeviceVO`
        FOREIGN KEY (`secondaryPciDeviceUuid`) REFERENCES `zstack`.`PciDeviceVO` (`uuid`) ON DELETE SET NULL;

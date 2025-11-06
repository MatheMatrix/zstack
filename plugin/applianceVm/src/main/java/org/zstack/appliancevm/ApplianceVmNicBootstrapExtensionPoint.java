package org.zstack.appliancevm;

import org.zstack.header.vm.VmNicInventory;

/**
 * 扩展点用于在生成 appliance VM 启动信息时补充特定网卡的数据。
 */
public interface ApplianceVmNicBootstrapExtensionPoint {
    /**
     * 当需要填充网卡启动信息时调用，扩展可以根据需要修改 {@code nicTo}。
     *
     * @param nic   即将下发的虚拟机网卡信息
     * @param nicTo 发送到 appliance VM 的传输对象，可由扩展修改
     */
    void fillNicBootstrapInfo(VmNicInventory nic, ApplianceVmNicTO nicTo);
}

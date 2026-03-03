package org.zstack.compute.vm.metadata;

import org.zstack.header.message.APIMessage;
import org.zstack.header.vm.VmInstanceMessage;
import org.zstack.header.vm.VmUuidFromApiResolver;

import java.util.Collections;
import java.util.List;

/**
 * 默认 VM UUID 解析器：从实现 {@link VmInstanceMessage} 接口的 API 消息中直接获取 vmInstanceUuid。
 *
 * <p>覆盖绝大多数 VM 直接 API（如 APIUpdateVmInstanceMsg、APIStartVmInstanceMsg 等）。</p>
 */
public class DefaultVmUuidFromApiResolver implements VmUuidFromApiResolver {

    @Override
    public boolean supports(APIMessage msg) {
        return msg instanceof VmInstanceMessage;
    }

    @Override
    public List<String> resolveVmUuids(APIMessage msg) {
        String vmUuid = ((VmInstanceMessage) msg).getVmInstanceUuid();
        return vmUuid != null ? Collections.singletonList(vmUuid) : Collections.emptyList();
    }
}

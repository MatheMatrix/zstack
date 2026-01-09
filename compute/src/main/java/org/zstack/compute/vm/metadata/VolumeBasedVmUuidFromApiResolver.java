package org.zstack.compute.vm.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.SQL;
import org.zstack.header.message.APIMessage;
import org.zstack.header.vm.VmInstanceMessage;
import org.zstack.header.vm.VmUuidFromApiResolver;
import org.zstack.header.volume.VolumeMessage;

import java.util.Collections;
import java.util.List;

/**
 * Volume 关联 VM UUID 解析器：从实现 {@link VolumeMessage} 接口的 API 消息中获取 volumeUuid，
 * 查询 VolumeVO 得到关联的 vmInstanceUuid。
 *
 * <p>覆盖快照、云盘挂载/卸载等涉及 Volume 但不直接携带 vmInstanceUuid 的 API。</p>
 *
 * <h3>排除条件</h3>
 * <p>如果消息同时实现了 {@link VmInstanceMessage}，则由 {@link DefaultVmUuidFromApiResolver} 处理，
 * 本解析器不参与。</p>
 *
 * <h3>解析时机</h3>
 * <p>在 API 执行前调用。对于 attach 场景，VolumeVO.vmInstanceUuid 可能尚未设置，
 * 此时通过反射 fallback 到 msg.getVmInstanceUuid()（如果存在）。</p>
 */
public class VolumeBasedVmUuidFromApiResolver implements VmUuidFromApiResolver {

    @Override
    public boolean supports(APIMessage msg) {
        // 同时实现 VmInstanceMessage 的由 DefaultResolver 处理
        return msg instanceof VolumeMessage && !(msg instanceof VmInstanceMessage);
    }

    @Override
    public List<String> resolveVmUuids(APIMessage msg) {
        String volumeUuid = ((VolumeMessage) msg).getVolumeUuid();
        if (volumeUuid == null) {
            return Collections.emptyList();
        }

        // 查询 Volume → vmInstanceUuid
        List<String> vmUuids = SQL.New(
                "SELECT v.vmInstanceUuid FROM VolumeVO v " +
                        "WHERE v.uuid = :uuid AND v.vmInstanceUuid IS NOT NULL",
                String.class
        ).param("uuid", volumeUuid).list();

        if (!vmUuids.isEmpty()) {
            return vmUuids;
        }

        // Fallback：尝试通过反射获取 msg 上的 getVmInstanceUuid()
        // 适用于 APIAttachDataVolumeToVmMsg 等同时携带 vmInstanceUuid 的消息
        try {
            String vmUuid = (String) msg.getClass().getMethod("getVmInstanceUuid").invoke(msg);
            if (vmUuid != null) {
                return Collections.singletonList(vmUuid);
            }
        } catch (Exception ignored) {
            // 无此方法，忽略
        }

        return Collections.emptyList();
    }
}

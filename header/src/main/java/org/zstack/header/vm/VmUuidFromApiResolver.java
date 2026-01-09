package org.zstack.header.vm;

import org.zstack.header.message.APIMessage;

import java.util.List;

/**
 * 从 API 消息中解析关联的 vmInstanceUuid。
 *
 * <p>用于非 VM 直接 API（如 Volume/Nic/快照 API）中提取关联的 VM UUID，
 * 以便在 API 成功后触发对应 VM 的元数据更新。</p>
 *
 * <h3>实现类示例</h3>
 * <ul>
 *   <li>VolumeToVmResolver：volumeUuid → vmInstanceUuid</li>
 *   <li>NicToVmResolver：vmNicUuid → vmInstanceUuid</li>
 *   <li>SnapshotToVmResolver：snapshotUuid → volumeUuid → vmInstanceUuid</li>
 * </ul>
 *
 * <h3>解析时机</h3>
 * <p>Resolver 应在 <strong>API 执行前</strong> 预解析 vmUuid 并缓存在上下文中，
 * 因为 API 执行后相关资源可能已被删除（如 APIDeleteVolumeMsg 执行后 VolumeVO 不存在）。</p>
 *
 * @see MetadataImpact
 * @see UpdateVmInstanceMetadataMsg
 */
public interface VmUuidFromApiResolver {

    /**
     * 判断此 Resolver 是否能处理指定的 API 消息类型。
     *
     * @param msg API 消息
     * @return true 表示此 Resolver 可以从该消息中解析 vmUuid
     */
    boolean supports(APIMessage msg);

    /**
     * 从 API 消息中解析出关联的 vmInstanceUuid 列表。
     *
     * <p>可能返回空列表（如 volume 未挂载到任何 VM）。
     * 可能返回多个 UUID（如批量操作涉及多台 VM）。</p>
     *
     * <p>此方法应在 API 执行前调用。</p>
     *
     * @param msg API 消息
     * @return 关联的 vmInstanceUuid 列表，不为 null
     */
    List<String> resolveVmUuids(APIMessage msg);
}
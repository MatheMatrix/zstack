package org.zstack.compute.vm.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO_;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupRefVO;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupRefVO_;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO_;
import org.zstack.header.tag.SystemTagVO;
import org.zstack.header.tag.SystemTagVO_;
import org.zstack.header.vm.VmInstanceMetadataDTO;
import org.zstack.header.vm.VmInstanceVO;
import org.zstack.header.vm.VmInstanceVO_;
import org.zstack.header.vm.VmNicVO;
import org.zstack.header.vm.VmNicVO_;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;
import org.zstack.resourceconfig.ResourceConfigVO;
import org.zstack.resourceconfig.ResourceConfigVO_;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.Utils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 构建虚拟机元数据 payload 的 Spring Component。
 *
 * <p>从 VmInstanceBase 中提取出来，以获得 Spring AOP 代理的 {@code @Transactional} 支持。
 * VmInstanceBase 实例不是 Spring 单例 Bean，其内部方法调用不经过 AOP 代理，
 * 因此 {@code @Transactional} 注解在 VmInstanceBase 自身方法上不生效。</p>
 *
 * <p>{@link #buildVmInstanceMetadata(String)} 执行 6+ 条 SELECT 查询，
 * 必须在同一个 REPEATABLE READ 事务快照内完成，以保证读一致性。</p>
 *
 * @see VmInstanceMetadataDTO
 */
public class VmMetadataBuilder {
    private static final CLogger logger = Utils.getLogger(VmMetadataBuilder.class);

    /** Payload 大小预警阈值（8 MB） */
    public static final int WARN_THRESHOLD = 8 * 1024 * 1024;

    /** Payload 大小拒绝阈值（30 MB） */
    public static final int REJECT_THRESHOLD = 30 * 1024 * 1024;

    @Autowired
    private DatabaseFacade dbf;

    /**
     * 从 DB 全量构建指定 VM 的元数据 JSON 字符串。
     *
     * <p>使用 {@code @Transactional(readOnly = true)} 确保所有 SELECT 查询
     * 在同一个 InnoDB REPEATABLE READ 事务快照内执行，保证读一致性。</p>
     *
     * @param vmInstanceUuid 目标虚拟机 UUID
     * @return 元数据 JSON 字符串
     */
    @Transactional(readOnly = true)
    public String buildVmInstanceMetadata(String vmInstanceUuid) {
        VmInstanceMetadataDTO dto = new VmInstanceMetadataDTO();

        // ── VM 本体 ──
        VmInstanceVO vm = Q.New(VmInstanceVO.class).eq(VmInstanceVO_.uuid, vmInstanceUuid).find();
        dto.vm = buildResourceMetadata(vm.getUuid(), vm);

        // ── 云盘（挂载的 + 已卸载但 lastVmInstanceUuid 指向本 VM 的） ──
        List<VolumeVO> volumes = new ArrayList<>();
        volumes.addAll(Q.New(VolumeVO.class).eq(VolumeVO_.vmInstanceUuid, vmInstanceUuid).list());
        volumes.addAll(Q.New(VolumeVO.class).isNull(VolumeVO_.vmInstanceUuid)
                .eq(VolumeVO_.lastVmInstanceUuid, vmInstanceUuid).list());
        volumes.forEach(v -> dto.volumes.add(buildResourceMetadata(v.getUuid(), v)));

        // ── 网卡 ──
        List<VmNicVO> nics = Q.New(VmNicVO.class).eq(VmNicVO_.vmInstanceUuid, vmInstanceUuid).list();
        nics.forEach(n -> dto.nics.add(buildResourceMetadata(n.getUuid(), n)));

        // ── 快照 ──
        List<String> volumeUuids = volumes.stream().map(VolumeVO::getUuid).collect(Collectors.toList());
        if (!volumeUuids.isEmpty()) {
            Q.New(VolumeSnapshotVO.class).in(VolumeSnapshotVO_.volumeUuid, volumeUuids).list()
                    .forEach(s -> dto.snapshots
                            .computeIfAbsent(s.getVolumeUuid(), k -> new ArrayList<>())
                            .add(JSONObjectUtil.toJsonString(s)));
        }

        // ── 快照组 ──
        List<VolumeSnapshotGroupVO> groups = Q.New(VolumeSnapshotGroupVO.class)
                .eq(VolumeSnapshotGroupVO_.vmInstanceUuid, vmInstanceUuid).list();
        dto.snapshotGroups = groups.stream()
                .map(JSONObjectUtil::toJsonString).collect(Collectors.toList());

        List<String> groupUuids = groups.stream()
                .map(VolumeSnapshotGroupVO::getUuid).collect(Collectors.toList());
        if (!groupUuids.isEmpty()) {
            dto.snapshotGroupRefs = Q.New(VolumeSnapshotGroupRefVO.class)
                    .in(VolumeSnapshotGroupRefVO_.volumeSnapshotGroupUuid, groupUuids).list()
                    .stream().map(JSONObjectUtil::toJsonString).collect(Collectors.toList());
        }

        return JSONObjectUtil.toJsonString(dto);
    }

    /**
     * 构建单个资源的 {@link VmInstanceMetadataDTO.ResourceMetadata}。
     *
     * <p>VO 全量 JSON 明文存储；SystemTagVO 和 ResourceConfigVO 整体列表序列化为 JSON 数组后
     * 一次性 Base64 编码，以保护可能包含的密码、密钥等敏感信息。</p>
     *
     * @param resourceUuid 资源 UUID
     * @param vo           资源 VO 对象（VmInstanceVO / VolumeVO / VmNicVO）
     * @return 填充完毕的 ResourceMetadata
     */
    private VmInstanceMetadataDTO.ResourceMetadata buildResourceMetadata(String resourceUuid, Object vo) {
        VmInstanceMetadataDTO.ResourceMetadata meta = new VmInstanceMetadataDTO.ResourceMetadata();
        meta.resourceUuid = resourceUuid;
        meta.vo = JSONObjectUtil.toJsonString(vo);

        // SystemTagVO: 全部 → JSON 数组 → Base64
        List<SystemTagVO> tagVOs = Q.New(SystemTagVO.class)
                .eq(SystemTagVO_.resourceUuid, resourceUuid).list();
        List<String> tagJsons = tagVOs.stream()
                .map(JSONObjectUtil::toJsonString).collect(Collectors.toList());
        meta.systemTags = Base64.getEncoder().encodeToString(
                JSONObjectUtil.toJsonString(tagJsons).getBytes(StandardCharsets.UTF_8));

        // ResourceConfigVO: 全部 → JSON 数组 → Base64
        List<ResourceConfigVO> cfgVOs = Q.New(ResourceConfigVO.class)
                .eq(ResourceConfigVO_.resourceUuid, resourceUuid).list();
        List<String> cfgJsons = cfgVOs.stream()
                .map(JSONObjectUtil::toJsonString).collect(Collectors.toList());
        meta.resourceConfigs = Base64.getEncoder().encodeToString(
                JSONObjectUtil.toJsonString(cfgJsons).getBytes(StandardCharsets.UTF_8));

        return meta;
    }
}

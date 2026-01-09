package org.zstack.compute.vm.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.header.storage.snapshot.VolumeSnapshotInventory;
import org.zstack.header.storage.snapshot.VolumeSnapshotTree;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO;
import org.zstack.header.storage.snapshot.VolumeSnapshotVO_;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupRefVO;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupRefVO_;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO;
import org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO_;
import org.zstack.header.storage.snapshot.reference.VolumeSnapshotReferenceTreeVO;
import org.zstack.header.storage.snapshot.reference.VolumeSnapshotReferenceTreeVO_;
import org.zstack.header.storage.snapshot.reference.VolumeSnapshotReferenceVO;
import org.zstack.header.storage.snapshot.reference.VolumeSnapshotReferenceVO_;
import org.zstack.header.tag.SystemTagVO;
import org.zstack.header.tag.SystemTagVO_;
import org.zstack.header.vm.*;
import org.zstack.header.volume.VolumeType;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;
import org.zstack.resourceconfig.ResourceConfigVO;
import org.zstack.resourceconfig.ResourceConfigVO_;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.nio.charset.StandardCharsets;
import java.util.*;
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
     * @return 元数据 JSON 字符串；若 VM 不符合构建条件则返回 null
     */
    @Transactional(readOnly = true)
    public String buildVmInstanceMetadata(String vmInstanceUuid) {
        // ── 查询 VM 本体 ──
        VmInstanceVO vm = Q.New(VmInstanceVO.class).eq(VmInstanceVO_.uuid, vmInstanceUuid).find();
        if (vm == null) {
            logger.warn(String.format("VM[uuid:%s] not found, skip metadata build", vmInstanceUuid));
            return null;
        }

        // ── UserVm 类型检查 ──
        if (!VmInstanceConstant.USER_VM_TYPE.equals(vm.getType())) {
            logger.debug(String.format("VM[uuid:%s] type is [%s], not UserVm, skip metadata build",
                    vmInstanceUuid, vm.getType()));
            return null;
        }

        // ── 云盘（挂载的 + 已卸载但 lastVmInstanceUuid 指向本 VM 的） ──
        List<VolumeVO> allVolumes = new ArrayList<>();
        allVolumes.addAll(Q.New(VolumeVO.class).eq(VolumeVO_.vmInstanceUuid, vmInstanceUuid).list());
        allVolumes.addAll(Q.New(VolumeVO.class).isNull(VolumeVO_.vmInstanceUuid)
                .eq(VolumeVO_.lastVmInstanceUuid, vmInstanceUuid).list());

        // ── 共享盘排除 ──
        List<VolumeVO> volumes = allVolumes.stream()
                .filter(v -> !v.isShareable())
                .collect(Collectors.toList());

        // ── Root Volume 检查 ──
        boolean hasRootVolume = volumes.stream()
                .anyMatch(v -> VolumeType.Root.toString().equals(v.getType()));
        if (!hasRootVolume) {
            logger.warn(String.format("VM[uuid:%s] has no root volume, skip metadata build", vmInstanceUuid));
            return null;
        }

        // ── 确定性排序：volumes by uuid ──
        volumes.sort(Comparator.comparing(VolumeVO::getUuid));

        VmInstanceMetadataDTO dto = new VmInstanceMetadataDTO();

        // ── schemaVersion ──
        dto.schemaVersion = dbf.getDbVersion();

        // ── vmCategory（先判缓存再判模板） ──
        if (Q.New(TemplatedVmInstanceCacheVO.class)
                .eq(TemplatedVmInstanceCacheVO_.cacheVmInstanceUuid, vmInstanceUuid)
                .isExists()) {
            dto.vmCategory = VmMetadataCategory.TEMPLATE_CACHE;
        } else if (Q.New(TemplatedVmInstanceVO.class)
                .eq(TemplatedVmInstanceVO_.uuid, vmInstanceUuid)
                .isExists()) {
            dto.vmCategory = VmMetadataCategory.TEMPLATE;
        } else {
            dto.vmCategory = VmMetadataCategory.REGULAR;
        }

        // ── VM 本体 ──
        dto.vm = buildResourceMetadata(vm.getUuid(), vm);

        // ── 云盘（VolumeResourceMetadata，含引用数据） ──
        List<String> volumeUuids = volumes.stream().map(VolumeVO::getUuid).collect(Collectors.toList());
        dto.volumes = new ArrayList<>();
        for (VolumeVO vol : volumes) {
            dto.volumes.add(buildVolumeResourceMetadata(vol));
        }

        // ── 网卡（排序 by uuid） ──
        List<VmNicVO> nics = Q.New(VmNicVO.class).eq(VmNicVO_.vmInstanceUuid, vmInstanceUuid).list();
        nics.sort(Comparator.comparing(VmNicVO::getUuid));
        dto.nics = new ArrayList<>();
        nics.forEach(n -> dto.nics.add(buildResourceMetadata(n.getUuid(), n)));

        // ── 快照（BFS 拓扑排序，扁平列表） ──
        if (!volumeUuids.isEmpty()) {
            List<VolumeSnapshotVO> allSnapshots = Q.New(VolumeSnapshotVO.class)
                    .in(VolumeSnapshotVO_.volumeUuid, volumeUuids).list();

            if (allSnapshots.isEmpty()) {
                dto.snapshots = Collections.emptyList();
            } else {
                List<VolumeSnapshotVO> sorted = topoSortSnapshots(allSnapshots, vmInstanceUuid);
                dto.snapshots = sorted.stream()
                        .map(JSONObjectUtil::toJsonString)
                        .collect(Collectors.toList());
            }
        } else {
            dto.snapshots = Collections.emptyList();
        }

        // ── 快照组（排序 by uuid） ──
        List<VolumeSnapshotGroupVO> groups = Q.New(VolumeSnapshotGroupVO.class)
                .eq(VolumeSnapshotGroupVO_.vmInstanceUuid, vmInstanceUuid).list();
        groups.sort(Comparator.comparing(VolumeSnapshotGroupVO::getUuid));
        dto.snapshotGroups = groups.stream()
                .map(JSONObjectUtil::toJsonString).collect(Collectors.toList());

        // ── 快照组关联引用（复合键排序：volumeSnapshotGroupUuid + volumeUuid） ──
        List<String> groupUuids = groups.stream()
                .map(VolumeSnapshotGroupVO::getUuid).collect(Collectors.toList());
        if (!groupUuids.isEmpty()) {
            List<VolumeSnapshotGroupRefVO> refs = Q.New(VolumeSnapshotGroupRefVO.class)
                    .in(VolumeSnapshotGroupRefVO_.volumeSnapshotGroupUuid, groupUuids).list();
            refs.sort(Comparator.comparing(VolumeSnapshotGroupRefVO::getVolumeSnapshotGroupUuid)
                    .thenComparing(VolumeSnapshotGroupRefVO::getVolumeUuid));
            dto.snapshotGroupRefs = refs.stream()
                    .map(JSONObjectUtil::toJsonString).collect(Collectors.toList());
        } else {
            dto.snapshotGroupRefs = Collections.emptyList();
        }

        // ── 序列化 & payload 大小检查 ──
        String json = JSONObjectUtil.toJsonString(dto);
        int payloadSize = json.getBytes(StandardCharsets.UTF_8).length;
        if (payloadSize > REJECT_THRESHOLD) {
            logger.error(String.format("VM[uuid:%s] metadata payload size %d bytes exceeds reject threshold %d bytes, " +
                    "skip metadata build", vmInstanceUuid, payloadSize, REJECT_THRESHOLD));
            return null;
        }
        if (payloadSize > WARN_THRESHOLD) {
            logger.warn(String.format("VM[uuid:%s] metadata payload size %d bytes exceeds warn threshold %d bytes",
                    vmInstanceUuid, payloadSize, WARN_THRESHOLD));
        }

        return json;
    }

    /**
     * 对所有快照进行 BFS 拓扑排序。
     *
     * <p>按 volumeUuid 分组，再按 treeUuid 分组（双层 TreeMap 保证 ASC 排序），
     * 同一 tree 内使用 {@link VolumeSnapshotTree#fromVOs(List)} +
     * {@link VolumeSnapshotTree#levelOrderTraversal()} 进行 BFS 层序遍历。</p>
     *
     * @param allSnapshots 待排序的全部快照 VO
     * @param vmUuid       VM UUID（仅用于日志）
     * @return 拓扑排序后的快照 VO 列表
     */
    private List<VolumeSnapshotVO> topoSortSnapshots(List<VolumeSnapshotVO> allSnapshots, String vmUuid) {
        // 双层 TreeMap 分组：volumeUuid → treeUuid → List<VolumeSnapshotVO>
        Map<String, Map<String, List<VolumeSnapshotVO>>> byVolumeThenTree =
                allSnapshots.stream().collect(Collectors.groupingBy(
                        VolumeSnapshotVO::getVolumeUuid, TreeMap::new,
                        Collectors.groupingBy(VolumeSnapshotVO::getTreeUuid,
                                TreeMap::new, Collectors.toList())));

        List<VolumeSnapshotVO> result = new ArrayList<>();
        // 按 volumeUuid ASC → treeUuid ASC 遍历
        for (Map<String, List<VolumeSnapshotVO>> treesInVolume : byVolumeThenTree.values()) {
            for (List<VolumeSnapshotVO> treeSnapshots : treesInVolume.values()) {
                VolumeSnapshotTree tree = VolumeSnapshotTree.fromVOs(treeSnapshots);
                List<VolumeSnapshotInventory> ordered = tree.levelOrderTraversal();
                for (VolumeSnapshotInventory inv : ordered) {
                    VolumeSnapshotVO found = findSnapshotByUuid(treeSnapshots, inv.getUuid());
                    if (found != null) {
                        result.add(found);
                    }
                }
            }
        }

        // 循环引用防护：若 BFS 遗漏了快照，追加到结尾
        if (result.size() < allSnapshots.size()) {
            Set<String> resultUuids = result.stream()
                    .map(VolumeSnapshotVO::getUuid).collect(Collectors.toSet());
            List<VolumeSnapshotVO> missing = allSnapshots.stream()
                    .filter(s -> !resultUuids.contains(s.getUuid()))
                    .sorted(Comparator.comparing(VolumeSnapshotVO::getUuid))
                    .collect(Collectors.toList());
            logger.warn(String.format("Unreachable snapshots detected for VM[uuid:%s]: %d out of %d, " +
                            "possible circular reference. Appending missing snapshots by uuid ASC.",
                    vmUuid, missing.size(), allSnapshots.size()));
            result.addAll(missing);
        }

        return result;
    }

    /**
     * 从快照列表中按 UUID 查找。
     */
    private VolumeSnapshotVO findSnapshotByUuid(List<VolumeSnapshotVO> snapshots, String uuid) {
        for (VolumeSnapshotVO s : snapshots) {
            if (s.getUuid().equals(uuid)) {
                return s;
            }
        }
        return null;
    }

    /**
     * 构建单个 Volume 的 {@link VolumeResourceMetadata}。
     *
     * <p>包含 VO JSON、SystemTag、ResourceConfig 以及
     * 该 Volume 关联的快照引用（VolumeSnapshotReferenceVO）和引用树（VolumeSnapshotReferenceTreeVO）。</p>
     *
     * @param vol VolumeVO 对象
     * @return 填充完毕的 VolumeResourceMetadata
     */
    private VolumeResourceMetadata buildVolumeResourceMetadata(VolumeVO vol) {
        VolumeResourceMetadata meta = new VolumeResourceMetadata();
        meta.resourceUuid = vol.getUuid();
        meta.vo = JSONObjectUtil.toJsonString(vol);

        // SystemTag: 排序 by uuid → JSON 数组 → Base64
        List<SystemTagVO> tagVOs = Q.New(SystemTagVO.class)
                .eq(SystemTagVO_.resourceUuid, vol.getUuid()).list();
        tagVOs.sort(Comparator.comparing(SystemTagVO::getUuid));
        // TODO: 白名单过滤（CoreMemorySnapshotConfigs 当前不存在，待后续实现）
        List<String> tagJsons = tagVOs.stream()
                .map(JSONObjectUtil::toJsonString).collect(Collectors.toList());
        meta.systemTags = Base64.getEncoder().encodeToString(
                JSONObjectUtil.toJsonString(tagJsons).getBytes(StandardCharsets.UTF_8));

        // ResourceConfig: 排序 by uuid → JSON 数组 → Base64
        List<ResourceConfigVO> cfgVOs = Q.New(ResourceConfigVO.class)
                .eq(ResourceConfigVO_.resourceUuid, vol.getUuid()).list();
        cfgVOs.sort(Comparator.comparing(ResourceConfigVO::getUuid));
        // TODO: 白名单过滤（CoreMemorySnapshotConfigs 当前不存在，待后续实现）
        List<String> cfgJsons = cfgVOs.stream()
                .map(JSONObjectUtil::toJsonString).collect(Collectors.toList());
        meta.resourceConfigs = Base64.getEncoder().encodeToString(
                JSONObjectUtil.toJsonString(cfgJsons).getBytes(StandardCharsets.UTF_8));

        // 快照引用：按 id 排序
        List<VolumeSnapshotReferenceVO> refs = Q.New(VolumeSnapshotReferenceVO.class)
                .eq(VolumeSnapshotReferenceVO_.referenceVolumeUuid, vol.getUuid()).list();
        refs.sort(Comparator.comparing(VolumeSnapshotReferenceVO::getId));
        meta.snapshotReferences = refs.stream()
                .map(JSONObjectUtil::toJsonString).collect(Collectors.toList());

        // 快照引用树：按 uuid 排序
        List<VolumeSnapshotReferenceTreeVO> trees = Q.New(VolumeSnapshotReferenceTreeVO.class)
                .eq(VolumeSnapshotReferenceTreeVO_.rootVolumeUuid, vol.getUuid()).list();
        trees.sort(Comparator.comparing(VolumeSnapshotReferenceTreeVO::getUuid));
        meta.snapshotReferenceTrees = trees.stream()
                .map(JSONObjectUtil::toJsonString).collect(Collectors.toList());

        return meta;
    }

    /**
     * 构建单个资源的 {@link VmInstanceMetadataDTO.ResourceMetadata}。
     *
     * <p>VO 全量 JSON 明文存储；SystemTagVO 和 ResourceConfigVO 整体列表序列化为 JSON 数组后
     * 一次性 Base64 编码，以保护可能包含的密码、密钥等敏感信息。</p>
     *
     * @param resourceUuid 资源 UUID
     * @param vo           资源 VO 对象（VmInstanceVO / VmNicVO）
     * @return 填充完毕的 ResourceMetadata
     */
    private VmInstanceMetadataDTO.ResourceMetadata buildResourceMetadata(String resourceUuid, Object vo) {
        VmInstanceMetadataDTO.ResourceMetadata meta = new VmInstanceMetadataDTO.ResourceMetadata();
        meta.resourceUuid = resourceUuid;
        meta.vo = JSONObjectUtil.toJsonString(vo);

        // SystemTagVO: 排序 by uuid → JSON 数组 → Base64
        List<SystemTagVO> tagVOs = Q.New(SystemTagVO.class)
                .eq(SystemTagVO_.resourceUuid, resourceUuid).list();
        tagVOs.sort(Comparator.comparing(SystemTagVO::getUuid));
        // TODO: 白名单过滤（CoreMemorySnapshotConfigs 当前不存在，待后续实现）
        List<String> tagJsons = tagVOs.stream()
                .map(JSONObjectUtil::toJsonString).collect(Collectors.toList());
        meta.systemTags = Base64.getEncoder().encodeToString(
                JSONObjectUtil.toJsonString(tagJsons).getBytes(StandardCharsets.UTF_8));

        // ResourceConfigVO: 排序 by uuid → JSON 数组 → Base64
        List<ResourceConfigVO> cfgVOs = Q.New(ResourceConfigVO.class)
                .eq(ResourceConfigVO_.resourceUuid, resourceUuid).list();
        cfgVOs.sort(Comparator.comparing(ResourceConfigVO::getUuid));
        // TODO: 白名单过滤（CoreMemorySnapshotConfigs 当前不存在，待后续实现）
        List<String> cfgJsons = cfgVOs.stream()
                .map(JSONObjectUtil::toJsonString).collect(Collectors.toList());
        meta.resourceConfigs = Base64.getEncoder().encodeToString(
                JSONObjectUtil.toJsonString(cfgJsons).getBytes(StandardCharsets.UTF_8));

        return meta;
    }
}

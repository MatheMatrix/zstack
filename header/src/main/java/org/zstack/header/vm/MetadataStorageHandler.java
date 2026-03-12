package org.zstack.header.vm;

import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;

import java.util.List;

/**
 * 元数据存储处理器接口 — 抽象不同存储类型的元数据读写操作。
 *
 * <h3>设计背景（Part 01c §1.3）</h3>
 * <p>VM 元数据需要持久化到 VM 根盘所在的 Primary Storage 上。不同存储类型
 * （SharedBlock、Local/NFS）使用不同的存储格式和协议，本接口统一抽象
 * 这些差异，使上层逻辑（Poller、迁移流程、注册 API）无需关心底层实现。</p>
 *
 * <h3>Handler 动态路由（SM-07）</h3>
 * <p>所有方法通过 {@code psUuid} 参数动态路由 — 每次调用时根据
 * {@code PrimaryStorageVO.type} 查找对应 Handler 实现。支持同一迁移流程中
 * 源/目标使用不同 Handler。例如 VM 从 SharedBlock 迁移到 NFS 时，
 * {@code initializeMetadata(targetPsUuid)} 路由到 {@code LocalNfsMetadataStorageHandler}，
 * {@code deleteMetadata(sourcePsUuid)} 路由到 {@code SblkMetadataStorageHandler}。</p>
 *
 * <h3>实现类</h3>
 * <table>
 *   <tr><th>实现类</th><th>存储类型</th></tr>
 *   <tr><td>{@code SblkMetadataStorageHandler}</td><td>SharedBlock</td></tr>
 *   <tr><td>{@code LocalNfsMetadataStorageHandler}</td><td>Local/NFS</td></tr>
 * </table>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>Agent 端不解析 DTO 内容；控制面负责 DTO 构建、序列化和反序列化</li>
 *   <li>C-01C-9: {@code deleteMetadata} 必须幂等 — 删除不存在的元数据必须返回成功</li>
 *   <li>C-01C-11: 必须包含 {@code scanMetadataVmUuids()} 方法</li>
 * </ul>
 *
 * @see VmMetadataDirtyVO
 * @see MetadataImpact
 */
public interface MetadataStorageHandler {

    /**
     * 初始化 VM 元数据容器并写入完整 payload。
     *
     * <p>VM 创建或存储迁移 Step 4 时调用。对于 sblk，创建 LV 并写入 Header + Slot A；
     * 对于 local/NFS，创建 {@code .zstack-vm-metadata/} 目录（若不存在）并
     * 通过 tmp+fsync+rename 原子写入 JSON 文件。</p>
     *
     * <p>若容器已存在则覆盖写入（幂等）。</p>
     *
     * @param psUuid      Primary Storage UUID — 用于路由到正确的存储后端
     * @param vmUuid      VM UUID — 确定元数据文件/LV 名称
     * @param payloadJson 完整的元数据 JSON payload（由 {@code VmMetadataBuilder} 构建）
     * @param completion  异步回调
     */
    void initializeMetadata(String psUuid, String vmUuid, String payloadJson, Completion completion);

    /**
     * 删除 VM 元数据。
     *
     * <p>ExpungeVm 或存储迁移 Step 7 源端清理时调用。</p>
     *
     * <p><b>C-01C-9 幂等约束</b>：删除不存在的元数据（LV 已删除或 JSON 文件不存在）
     * 必须返回成功（不抛异常）。同时清理同名的 {@code .tmp} 和 {@code .sc.tmp}
     * 残留文件（如存在），删除 tmp 失败不影响主操作成功。</p>
     *
     * @param psUuid     Primary Storage UUID
     * @param vmUuid     VM UUID
     * @param completion 异步回调
     */
    void deleteMetadata(String psUuid, String vmUuid, Completion completion);

    /**
     * 写入/更新 VM 元数据（原子操作）。
     *
     * <p>Poller flush 时调用。sblk 使用三阶段原子写入；local/NFS 使用
     * tmp+fsync+rename 原子写入。</p>
     *
     * <p>{@code storageStructureChange} 参数用于区分 tmp 文件后缀：
     * {@code true} 使用 {@code .sc.tmp}（存储迁移写入），
     * {@code false} 使用 {@code .tmp}（普通写入）。
     * 注册时若检测到 {@code .sc.tmp} 残留，说明存储迁移写入未完成，
     * 该元数据文件标记为不可靠。</p>
     *
     * @param psUuid                 Primary Storage UUID
     * @param vmUuid                 VM UUID
     * @param payloadJson            完整的元数据 JSON payload
     * @param storageStructureChange 是否为存储结构变更写入（影响 tmp 文件后缀和 sblk OP type）
     * @param completion             异步回调
     */
    void writeMetadata(String psUuid, String vmUuid, String payloadJson,
                       boolean storageStructureChange, Completion completion);

    /**
     * 读取 VM 元数据。
     *
     * <p>存储迁移 Step 5 read-back 校验、Scan/Read API 时调用。</p>
     *
     * <p>返回值说明：</p>
     * <ul>
     *   <li>成功读取 → {@code success(payloadJson)}</li>
     *   <li>文件不存在 → {@code success(null)}</li>
     *   <li>读取失败或内容损坏 → {@code fail(errorCode)}</li>
     * </ul>
     *
     * @param psUuid     Primary Storage UUID
     * @param vmUuid     VM UUID
     * @param completion 异步回调，返回 JSON payload 字符串（可为 null 表示不存在）
     */
    void readMetadata(String psUuid, String vmUuid, ReturnValueCompletion<String> completion);

    /**
     * 判断给定的 Primary Storage 类型是否支持元数据。
     *
     * <p>当前支持：SharedBlock、LocalStorage、NFS。
     * 不支持的类型（ceph、zbs、vhost 等）返回 false，上层静默跳过。</p>
     *
     * @param psType Primary Storage 类型字符串（如 "SharedBlock"、"LocalStorage"、"NFS"）
     * @return true 如果该存储类型支持元数据
     */
    boolean isMetadataSupported(String psType);

    /**
     * 扫描指定 PS 上所有元数据条目，返回 {@link VmMetadataEntry} 列表（轻量级，不读取 payload）。
     *
     * <p>扫描方式因存储类型而异：</p>
     * <ul>
     *   <li>sblk: 扫描 VG 中所有 {@code *_vmmeta} LV，提取 vmUuid 前缀</li>
     *   <li>local/NFS: 列举 {@code .zstack-vm-metadata/} 目录下 {@code *.json} 文件名</li>
     * </ul>
     *
     * <p>用途：{@code MetadataOrphanDetector}（Part 2b §8.4.2）、Scan API（Part 5 §2）</p>
     *
     * <p><b>返回类型说明（讨论 Δ-7）</b>：原方案返回 {@code List<String>}（纯 vmUuid），
     * 改为返回 {@code List<VmMetadataEntry>}。Local Storage 场景下扫描需要逐 Host 执行，
     * 调用方需要知道元数据位于哪台 Host 上以便后续操作（如孤儿清理、注册时路由）。</p>
     *
     * @param psUuid     Primary Storage UUID
     * @param completion 异步回调，返回元数据条目列表
     */
    void scanMetadataVmUuids(String psUuid, ReturnValueCompletion<List<VmMetadataEntry>> completion);

    /**
     * 元数据扫描结果条目。
     *
     * <p>包含 vmUuid 和可选的 hostUuid 信息。对于共享存储（SharedBlock/NFS），
     * hostUuid 为 null；对于 Local Storage，hostUuid 标识元数据文件所在 Host。</p>
     */
    class VmMetadataEntry {
        private String vmUuid;
        private String hostUuid;  // nullable: SharedBlock/NFS 场景为 null

        public VmMetadataEntry() {
        }

        public VmMetadataEntry(String vmUuid, String hostUuid) {
            this.vmUuid = vmUuid;
            this.hostUuid = hostUuid;
        }

        public String getVmUuid() {
            return vmUuid;
        }

        public void setVmUuid(String vmUuid) {
            this.vmUuid = vmUuid;
        }

        public String getHostUuid() {
            return hostUuid;
        }

        public void setHostUuid(String hostUuid) {
            this.hostUuid = hostUuid;
        }
    }
}

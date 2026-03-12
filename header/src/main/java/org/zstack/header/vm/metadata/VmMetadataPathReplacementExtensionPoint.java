package org.zstack.header.vm.metadata;

import java.util.List;
import java.util.Map;

/**
 * 元数据注册时路径替换扩展点。
 *
 * 各存储插件（NFS/LocalStorage/SharedBlock）实现此接口，
 * 收集 DTO 中全部路径并一次性返回 old→new 完整映射。
 */
public interface VmMetadataPathReplacementExtensionPoint {
    /**
     * 判断本扩展是否处理指定存储类型
     */
    String getPrimaryStorageType();

    /**
     * 收集 DTO 中所有 installPath，计算完整的 old→new 路径映射。
     *
     * @param targetPsUuid  目标主存储 UUID
     * @param allOldPaths   DTO 中提取的所有原始路径（volume installPath、snapshot installPath 等）
     * @return PathReplacementResult
     */
    PathReplacementResult calculatePathReplacements(String targetPsUuid, List<String> allOldPaths);

    class PathReplacementResult {
        /** old path → new path 完整映射 */
        private Map<String, String> pathMap;
        /** rebase 前缀替换用的旧路径前缀 */
        private String oldPrefix;
        /** rebase 前缀替换用的新路径前缀 */
        private String newPrefix;

        public PathReplacementResult() {
        }

        public Map<String, String> getPathMap() {
            return pathMap;
        }

        public void setPathMap(Map<String, String> pathMap) {
            this.pathMap = pathMap;
        }

        public String getOldPrefix() {
            return oldPrefix;
        }

        public void setOldPrefix(String oldPrefix) {
            this.oldPrefix = oldPrefix;
        }

        public String getNewPrefix() {
            return newPrefix;
        }

        public void setNewPrefix(String newPrefix) {
            this.newPrefix = newPrefix;
        }
    }
}

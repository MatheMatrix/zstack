package org.zstack.header.vm.metadata;

import java.util.List;
import java.util.Map;

public interface VmMetadataPathReplacementExtensionPoint {
    String getPrimaryStorageType();
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

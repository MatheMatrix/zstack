package org.zstack.vm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zstack.core.Platform;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.header.vm.VmGpuPciMappingVO;
import org.zstack.header.vm.VmGpuPciMappingVO_;
import org.zstack.utils.logging.CLogger;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class VmGpuPciMappingService {
    private static final CLogger logger = CLogger.getLogger(VmGpuPciMappingService.class);

    @Autowired
    private DatabaseFacade dbf;

    // 缓存配置
    private static final long CACHE_EXPIRE_MS = 5 * 60 * 1000; // 5分钟缓存过期时间

    // 缓存结构：key = vmUuid + ":" + vmPciAddress, value = hostPciAddress
    private final Map<String, String> mappingCache = new ConcurrentHashMap<>();

    // 缓存时间戳：key = cacheKey, value = timestamp
    private final Map<String, Long> cacheTimestamp = new ConcurrentHashMap<>();

    /**
     * 根据VM UUID和VM PCI地址获取Host PCI地址（带缓存）
     */
    public String getHostPciAddress(String vmUuid, String vmPciAddress) {
        String cacheKey = vmUuid + ":" + vmPciAddress;

        // 检查缓存是否过期
        Long timestamp = cacheTimestamp.get(cacheKey);
        if (timestamp != null &&
            System.currentTimeMillis() - timestamp > CACHE_EXPIRE_MS) {
            // 缓存过期，清理
            mappingCache.remove(cacheKey);
            cacheTimestamp.remove(cacheKey);
            timestamp = null;
        }

        // 从缓存获取或查询数据库
        return mappingCache.computeIfAbsent(cacheKey, key -> {
            String hostAddress = queryFromDatabase(vmUuid, vmPciAddress);
            if (hostAddress != null) {
                cacheTimestamp.put(cacheKey, System.currentTimeMillis());
            }
            return hostAddress;
        });
    }

    /**
     * 批量获取Host PCI地址（带缓存优化）
     */
    public Map<String, String> getHostPciAddressesBatch(List<String> cacheKeys) {
        Map<String, String> result = new HashMap<>();
        List<String> needQueryKeys = new ArrayList<>();

        // 先从缓存获取
        for (String cacheKey : cacheKeys) {
            // 检查缓存是否过期
            Long timestamp = cacheTimestamp.get(cacheKey);
            if (timestamp != null &&
                System.currentTimeMillis() - timestamp > CACHE_EXPIRE_MS) {
                // 缓存过期，清理
                mappingCache.remove(cacheKey);
                cacheTimestamp.remove(cacheKey);
            }

            String cachedValue = mappingCache.get(cacheKey);
            if (cachedValue != null) {
                result.put(cacheKey, cachedValue);
            } else {
                needQueryKeys.add(cacheKey);
            }
        }

        // 批量查询数据库中缺失的映射
        if (!needQueryKeys.isEmpty()) {
            Map<String, String> dbResults = batchQueryFromDatabase(needQueryKeys);

            // 更新缓存和结果
            long currentTime = System.currentTimeMillis();
            for (Map.Entry<String, String> entry : dbResults.entrySet()) {
                String cacheKey = entry.getKey();
                String hostAddress = entry.getValue();

                mappingCache.put(cacheKey, hostAddress);
                cacheTimestamp.put(cacheKey, currentTime);
                result.put(cacheKey, hostAddress);
            }
        }

        return result;
    }

    /**
     * 从数据库批量查询映射关系
     */
    private Map<String, String> batchQueryFromDatabase(List<String> cacheKeys) {
        // 解析cacheKeys为vmUuid和pciAddress
        Set<String> vmUuids = cacheKeys.stream()
            .map(key -> key.split(":")[0])
            .collect(Collectors.toSet());

        Set<String> pciAddresses = cacheKeys.stream()
            .map(key -> key.split(":")[1])
            .collect(Collectors.toSet());

        // 批量查询数据库
        List<VmGpuPciMappingVO> mappings = Q.New(VmGpuPciMappingVO.class)
            .in(VmGpuPciMappingVO_.vmInstanceUuid, vmUuids)
            .in(VmGpuPciMappingVO_.vmPciAddress, pciAddresses)
            .list();

        // 转换为Map
        return mappings.stream()
            .collect(Collectors.toMap(
                vo -> vo.getVmInstanceUuid() + ":" + vo.getVmPciAddress(),
                VmGpuPciMappingVO::getHostPciAddress
            ));
    }

    /**
     * 创建映射关系
     */
    public void createMapping(String vmUuid, String vmPciAddress, String hostPciAddress, String gpuSerial) {
        VmGpuPciMappingVO existing = Q.New(VmGpuPciMappingVO.class)
            .eq(VmGpuPciMappingVO_.vmInstanceUuid, vmUuid)
            .eq(VmGpuPciMappingVO_.vmPciAddress, vmPciAddress)
            .find();

        if (existing != null) {
            // 更新现有映射
            existing.setHostPciAddress(hostPciAddress);
            existing.setGpuSerial(gpuSerial);
            dbf.update(existing);
            logger.debug(String.format("Updated GPU PCI mapping for VM[%s]: VM PCI[%s] -> Host PCI[%s]",
                vmUuid, vmPciAddress, hostPciAddress));
        } else {
            // 创建新映射
            VmGpuPciMappingVO mapping = new VmGpuPciMappingVO();
            mapping.setUuid(Platform.getUuid());
            mapping.setVmInstanceUuid(vmUuid);
            mapping.setVmPciAddress(vmPciAddress);
            mapping.setHostPciAddress(hostPciAddress);
            mapping.setGpuSerial(gpuSerial);
            dbf.persist(mapping);
            logger.debug(String.format("Created GPU PCI mapping for VM[%s]: VM PCI[%s] -> Host PCI[%s]",
                vmUuid, vmPciAddress, hostPciAddress));
        }

        // 更新缓存
        String cacheKey = vmUuid + ":" + vmPciAddress;
        mappingCache.put(cacheKey, hostPciAddress);
        cacheTimestamp.put(cacheKey, System.currentTimeMillis());
    }

    /**
     * 删除VM的所有映射关系
     */
    public void removeMappingsByVmUuid(String vmUuid) {
        List<VmGpuPciMappingVO> mappings = Q.New(VmGpuPciMappingVO.class)
            .eq(VmGpuPciMappingVO_.vmInstanceUuid, vmUuid)
            .list();

        for (VmGpuPciMappingVO mapping : mappings) {
            dbf.remove(mapping);
        }

        if (!mappings.isEmpty()) {
            logger.debug(String.format("Removed %d GPU PCI mappings for VM[%s]", mappings.size(), vmUuid));
        }

        // 清理缓存
        String vmPrefix = vmUuid + ":";
        mappingCache.entrySet().removeIf(entry -> entry.getKey().startsWith(vmPrefix));
        cacheTimestamp.entrySet().removeIf(entry -> entry.getKey().startsWith(vmPrefix));
    }

    /**
     * 删除特定的映射关系
     */
    public void removeMapping(String vmUuid, String vmPciAddress) {
        VmGpuPciMappingVO mapping = Q.New(VmGpuPciMappingVO.class)
            .eq(VmGpuPciMappingVO_.vmInstanceUuid, vmUuid)
            .eq(VmGpuPciMappingVO_.vmPciAddress, vmPciAddress)
            .find();

        if (mapping != null) {
            dbf.remove(mapping);
            logger.debug(String.format("Removed GPU PCI mapping for VM[%s]: VM PCI[%s]",
                vmUuid, vmPciAddress));
        }

        // 清理缓存
        String cacheKey = vmUuid + ":" + vmPciAddress;
        mappingCache.remove(cacheKey);
        cacheTimestamp.remove(cacheKey);
    }

    /**
     * 预加载所有映射关系到缓存
     */
    @PostConstruct
    public void preloadCache() {
        try {
            List<VmGpuPciMappingVO> allMappings = Q.New(VmGpuPciMappingVO.class).list();

            long currentTime = System.currentTimeMillis();
            for (VmGpuPciMappingVO mapping : allMappings) {
                String cacheKey = mapping.getVmInstanceUuid() + ":" + mapping.getVmPciAddress();
                mappingCache.put(cacheKey, mapping.getHostPciAddress());
                cacheTimestamp.put(cacheKey, currentTime);
            }

            logger.info(String.format("Preloaded %d GPU PCI mappings into cache", allMappings.size()));
        } catch (Exception e) {
            logger.warn("Failed to preload GPU PCI mapping cache", e);
        }
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", mappingCache.size());
        stats.put("timestampSize", cacheTimestamp.size());
        stats.put("cacheExpireMs", CACHE_EXPIRE_MS);

        long expiredCount = cacheTimestamp.values().stream()
            .mapToLong(timestamp -> System.currentTimeMillis() - timestamp)
            .filter(age -> age > CACHE_EXPIRE_MS)
            .count();
        stats.put("expiredEntries", expiredCount);

        return stats;
    }

    /**
     * 清理过期缓存
     */
    public void cleanExpiredCache() {
        long currentTime = System.currentTimeMillis();
        List<String> expiredKeys = cacheTimestamp.entrySet().stream()
            .filter(entry -> currentTime - entry.getValue() > CACHE_EXPIRE_MS)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        for (String key : expiredKeys) {
            mappingCache.remove(key);
            cacheTimestamp.remove(key);
        }

        if (!expiredKeys.isEmpty()) {
            logger.debug(String.format("Cleaned %d expired cache entries", expiredKeys.size()));
        }
    }
}
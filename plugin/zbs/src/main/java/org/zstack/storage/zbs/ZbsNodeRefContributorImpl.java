package org.zstack.storage.zbs;

import org.zstack.compute.host.HostSystemTags;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.Platform;
import org.zstack.core.db.Q;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.host.HostVO_;
import org.zstack.header.physicalserver.PhysicalServerIdentitySpec;
import org.zstack.header.physicalserver.PhysicalServerManager;
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageVO;
import org.zstack.header.storage.addon.primary.ExternalPrimaryStorageVO_;
import org.zstack.header.tag.SystemTagVO;
import org.zstack.header.tag.SystemTagVO_;
import org.zstack.kvm.KVMHostVO;
import org.zstack.utils.TagUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.zstack.core.Platform.operr;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.ORG_ZSTACK_CORE_10000;

public class ZbsNodeRefContributorImpl implements ZbsNodeRefContributor,
        ZbsPhysicalServerIdentityResolver {
    private static final CLogger logger = Utils.getLogger(
            ZbsNodeRefContributorImpl.class);

    @Autowired(required = false)
    private PhysicalServerManager physicalServerManager;

    @Override
    public Map<String, ZbsNodeRef> bulkList(Collection<String> serverUuids) {
        Set<String> requestedServerUuids = serverUuids == null
                ? Collections.emptySet() : new HashSet<>(serverUuids);
        List<ExternalPrimaryStorageVO> primaryStorages = Q.New(ExternalPrimaryStorageVO.class)
                .eq(ExternalPrimaryStorageVO_.identity, ZbsConstants.IDENTITY)
                .list();
        Map<String, AddonInfo> addonInfos = new LinkedHashMap<>();
        for (ExternalPrimaryStorageVO primaryStorage : primaryStorages) {
            addonInfos.put(primaryStorage.getUuid(), parseAddonInfo(primaryStorage));
        }
        Map<String, String> serversBySerialNumber = serversBySerialNumber(
                addonInfos.values());
        Map<String, Set<String>> serversByIp = requiresIpFallback(
                addonInfos.values())
                ? mapServersByIp(listHosts(requestedServerUuids))
                : Collections.emptyMap();
        Map<String, ZbsNodeRef> result = new LinkedHashMap<>();
        for (Map.Entry<String, AddonInfo> source : addonInfos.entrySet()) {
            for (MdsInfo mds : source.getValue().getMdsInfos()) {
                if (mds == null) {
                    throw invalidAddonInfo(source.getKey(),
                            "contains an empty mdsInfo");
                }
                String serialNumber = serialNumber(mds);
                if (serialNumber != null) {
                    String serverUuid = serversBySerialNumber.get(serialNumber);
                    if (serverUuid == null) {
                        logger.warn(String.format(
                                "cannot resolve ZBS MDS physical server serialNumber[%s], " +
                                        "reasonCode[ZBS_NODE_SERIAL_UNRESOLVED]",
                                serialNumber));
                        continue;
                    }
                    if (requestedServerUuids.isEmpty()
                            || requestedServerUuids.contains(serverUuid)) {
                        addRef(result, serverUuid, source.getKey(),
                                nodeAddress(mds), null);
                    }
                    continue;
                }
                String address = resolveAddress(mds, serversByIp);
                if (address == null) {
                    continue;
                }
                Set<String> matchedServers = serversByIp.get(address);
                for (String serverUuid : matchedServers) {
                    addRef(result, serverUuid, source.getKey(), address,
                            matchedServers.size() > 1
                                    ? "ZBS_NODE_ADDRESS_AMBIGUOUS"
                                    : "ZBS_NODE_SERIAL_MISSING");
                }
            }
        }
        return result;
    }

    @Override
    public Set<String> resolveServerUuids(
            String primaryStorageUuid, AddonInfo addonInfo) {
        validateAddonInfo(primaryStorageUuid, addonInfo);
        Map<String, String> serversBySerialNumber = serversBySerialNumber(
                Collections.singleton(addonInfo));
        Map<String, Set<String>> serversByIp = requiresIpFallback(
                Collections.singleton(addonInfo))
                ? mapServersByIp(listHosts(Collections.emptySet()))
                : Collections.emptyMap();
        Set<String> result = new HashSet<>();
        for (MdsInfo mds : addonInfo.getMdsInfos()) {
            if (mds == null) {
                throw invalidAddonInfo(primaryStorageUuid, "contains an empty mdsInfo");
            }
            String serialNumber = serialNumber(mds);
            if (serialNumber != null) {
                String serverUuid = serversBySerialNumber.get(serialNumber);
                if (serverUuid != null) {
                    result.add(serverUuid);
                }
                continue;
            }
            String address = resolveAddress(mds, serversByIp);
            if (address != null) {
                result.addAll(serversByIp.get(address));
            }
        }
        return result;
    }

    @Override
    public void enrichPhysicalServerSerialNumbers(
            String primaryStorageUuid, AddonInfo addonInfo) {
        validateAddonInfo(primaryStorageUuid, addonInfo);
        if (!requiresIpFallback(Collections.singleton(addonInfo))) {
            normalizeSerialNumbers(addonInfo);
            return;
        }
        Map<String, Set<String>> serversByIp = mapServersByIp(
                listHosts(Collections.emptySet()));
        Map<MdsInfo, String> matchedServers = new LinkedHashMap<>();
        for (MdsInfo mds : addonInfo.getMdsInfos()) {
            if (mds == null) {
                throw invalidAddonInfo(primaryStorageUuid,
                        "contains an empty mdsInfo");
            }
            String serialNumber = serialNumber(mds);
            if (serialNumber != null) {
                mds.setPhysicalServerSerialNumber(serialNumber);
                continue;
            }
            String address = resolveAddress(mds, serversByIp);
            if (address == null || serversByIp.get(address).size() != 1) {
                continue;
            }
            matchedServers.put(mds, serversByIp.get(address).iterator().next());
        }
        Map<String, String> serialNumbersByServer = serialNumbersByServer(
                new HashSet<>(matchedServers.values()));
        for (Map.Entry<MdsInfo, String> match : matchedServers.entrySet()) {
            match.getKey().setPhysicalServerSerialNumber(
                    serialNumbersByServer.get(match.getValue()));
        }
    }

    private List<KVMHostVO> listHosts(Collection<String> serverUuids) {
        Q hostQuery = Q.New(KVMHostVO.class)
                .notNull(HostVO_.serverUuid);
        if (serverUuids != null && !serverUuids.isEmpty()) {
            hostQuery.in(HostVO_.serverUuid, serverUuids);
        }
        return hostQuery.list();
    }

    private AddonInfo parseAddonInfo(ExternalPrimaryStorageVO primaryStorage) {
        if (primaryStorage.getAddonInfo() == null || primaryStorage.getAddonInfo().isEmpty()) {
            throw invalidAddonInfo(primaryStorage.getUuid(), "is empty");
        }

        AddonInfo addonInfo = JSONObjectUtil.toObject(
                primaryStorage.getAddonInfo(), AddonInfo.class);
        validateAddonInfo(primaryStorage.getUuid(), addonInfo);
        return addonInfo;
    }

    private void validateAddonInfo(String primaryStorageUuid, AddonInfo addonInfo) {
        if (addonInfo == null || addonInfo.getMdsInfos() == null
                || addonInfo.getMdsInfos().isEmpty()) {
            throw invalidAddonInfo(primaryStorageUuid, "does not contain mdsInfos");
        }
    }

    private OperationFailureException invalidAddonInfo(String primaryStorageUuid, String detail) {
        return new OperationFailureException(operr(
                ORG_ZSTACK_CORE_10000,
                "ZBS_NODE_RELATION_SOURCE_INVALID: cannot derive ZBS node relations because " +
                        "primary storage[uuid:%s] addonInfo %s",
                primaryStorageUuid,
                detail));
    }

    private Map<String, String> serversBySerialNumber(
            Collection<AddonInfo> addonInfos) {
        Set<String> serialNumbers = new HashSet<>();
        for (AddonInfo addonInfo : addonInfos) {
            for (MdsInfo mds : addonInfo.getMdsInfos()) {
                if (mds == null) {
                    continue;
                }
                String serialNumber = serialNumber(mds);
                if (serialNumber != null) {
                    serialNumbers.add(serialNumber);
                }
            }
        }
        if (serialNumbers.isEmpty()) {
            return Collections.emptyMap();
        }
        if (physicalServerManager == null) {
            return Collections.emptyMap();
        }
        List<PhysicalServerIdentitySpec> identities = new ArrayList<>();
        for (String serialNumber : serialNumbers) {
            identities.add(new PhysicalServerIdentitySpec(serialNumber, null));
        }
        return physicalServerManager.resolveIdentities(identities);
    }

    private boolean requiresIpFallback(Collection<AddonInfo> addonInfos) {
        for (AddonInfo addonInfo : addonInfos) {
            for (MdsInfo mds : addonInfo.getMdsInfos()) {
                if (mds == null || serialNumber(mds) == null) {
                    return true;
                }
            }
        }
        return false;
    }

    private void normalizeSerialNumbers(AddonInfo addonInfo) {
        for (MdsInfo mds : addonInfo.getMdsInfos()) {
            if (mds != null) {
                mds.setPhysicalServerSerialNumber(serialNumber(mds));
            }
        }
    }

    private Map<String, String> serialNumbersByServer(
            Collection<String> serverUuids) {
        if (serverUuids.isEmpty()) {
            return Collections.emptyMap();
        }
        return physicalServerManager == null
                ? Collections.emptyMap()
                : physicalServerManager.findSerialNumbersByServerUuids(serverUuids);
    }

    private String serialNumber(MdsInfo mds) {
        return Platform.normalizeMachineSerialNumber(
                mds.getPhysicalServerSerialNumber());
    }

    private String nodeAddress(MdsInfo mds) {
        return mds.getExternalAddr() == null ? mds.getAddr() : mds.getExternalAddr();
    }

    private void addRef(
            Map<String, ZbsNodeRef> refs,
            String serverUuid,
            String primaryStorageUuid,
            String nodeAddress,
            String reasonCode) {
        ZbsNodeRef ref = refs.computeIfAbsent(serverUuid, ignored -> {
            ZbsNodeRef created = new ZbsNodeRef();
            created.setServerUuid(serverUuid);
            return created;
        });
        ref.addPrimaryStorageUuid(primaryStorageUuid);
        if (nodeAddress != null) {
            ref.addNodeAddress(nodeAddress);
        }
        ref.incrementSourceRefCount();
        if (reasonCode != null) {
            ref.setReasonCode(reasonCode);
        }
    }

    private Map<String, Set<String>> mapServersByIp(List<KVMHostVO> hosts) {
        Map<String, Set<String>> result = new HashMap<>();
        if (hosts.isEmpty()) {
            return result;
        }
        Map<String, String> serverByHost = new HashMap<>();
        for (KVMHostVO host : hosts) {
            serverByHost.put(host.getUuid(), host.getServerUuid());
            add(result, host.getManagementIp(), host.getServerUuid());
        }

        List<SystemTagVO> tags = Q.New(SystemTagVO.class)
                .in(SystemTagVO_.resourceUuid, serverByHost.keySet())
                .eq(SystemTagVO_.resourceType, org.zstack.header.host.HostVO.class.getSimpleName())
                .like(SystemTagVO_.tag,
                        TagUtils.tagPatternToSqlPattern(HostSystemTags.EXTRA_IPS.getTagFormat()))
                .list();
        for (SystemTagVO tag : tags) {
            String extraIps = HostSystemTags.EXTRA_IPS.getTokenByTag(
                    tag.getTag(), HostSystemTags.EXTRA_IPS_TOKEN);
            if (extraIps == null) {
                continue;
            }
            for (String ip : extraIps.split(",")) {
                add(result, ip.trim(), serverByHost.get(tag.getResourceUuid()));
            }
        }
        return result;
    }

    private String resolveAddress(MdsInfo mds, Map<String, Set<String>> serversByIp) {
        if (mds.getExternalAddr() != null && serversByIp.containsKey(mds.getExternalAddr())) {
            return mds.getExternalAddr();
        }
        if (mds.getAddr() != null && serversByIp.containsKey(mds.getAddr())) {
            return mds.getAddr();
        }
        return null;
    }

    private void add(Map<String, Set<String>> values, String key, String value) {
        if (key == null || key.isEmpty() || value == null) {
            return;
        }
        values.computeIfAbsent(key, ignored -> new HashSet<>()).add(value);
    }
}

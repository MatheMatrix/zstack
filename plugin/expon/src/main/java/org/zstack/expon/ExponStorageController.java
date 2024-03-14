package org.zstack.expon;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SQL;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.expon.sdk.ExponClient;
import org.zstack.expon.sdk.ExponConnectConfig;
import org.zstack.expon.sdk.cluster.TianshuClusterModule;
import org.zstack.expon.sdk.iscsi.IscsiClientGroupModule;
import org.zstack.expon.sdk.iscsi.IscsiModule;
import org.zstack.expon.sdk.iscsi.IscsiSeverNode;
import org.zstack.expon.sdk.iscsi.IscsiUssResource;
import org.zstack.expon.sdk.nvmf.NvmfBoundUssGatewayRefModule;
import org.zstack.expon.sdk.nvmf.NvmfClientGroupModule;
import org.zstack.expon.sdk.nvmf.NvmfModule;
import org.zstack.expon.sdk.pool.FailureDomainModule;
import org.zstack.expon.sdk.uss.UssGatewayModule;
import org.zstack.expon.sdk.vhost.VhostControllerModule;
import org.zstack.expon.sdk.volume.ExponVolumeQos;
import org.zstack.expon.sdk.volume.VolumeModule;
import org.zstack.expon.sdk.volume.VolumeSnapshotModule;
import org.zstack.header.core.Completion;
import org.zstack.header.core.NopeCompletion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.expon.HealthStatus;
import org.zstack.header.host.HostInventory;
import org.zstack.header.storage.addon.*;
import org.zstack.header.storage.addon.primary.*;
import org.zstack.header.storage.primary.ImageCacheInventory;
import org.zstack.header.storage.primary.VolumeSnapshotCapability;
import org.zstack.header.storage.snapshot.VolumeSnapshotStats;
import org.zstack.header.volume.VolumeConstant;
import org.zstack.header.volume.VolumeInventory;
import org.zstack.header.volume.VolumeProtocol;
import org.zstack.header.volume.VolumeStats;
import org.zstack.iscsi.IscsiUtils;
import org.zstack.iscsi.kvm.IscsiHeartbeatVolumeTO;
import org.zstack.iscsi.kvm.IscsiVolumeTO;
import org.zstack.storage.primary.PrimaryStorageGlobalConfig;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.data.SizeUnit;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.path.PathUtil;
import org.zstack.vhost.kvm.VhostVolumeTO;

import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.zstack.core.Platform.operr;
import static org.zstack.expon.ExponIscsiHelper.*;
import static org.zstack.expon.ExponNameHelper.*;
import static org.zstack.iscsi.IscsiUtils.getHostMnIpFromInitiatorName;
import static org.zstack.storage.addon.primary.ExternalPrimaryStorageNameHelper.*;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class ExponStorageController implements PrimaryStorageControllerSvc, PrimaryStorageNodeSvc {
    private static CLogger logger = Utils.getLogger(ExponStorageController.class);

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private ThreadFacade thdf;
    private ExternalPrimaryStorageVO self;
    private ExponAddonInfo addonInfo;
    private ExponConfig config;

    private final ExponApiHelper apiHelper;

    // TODO static nqn
    private final static String hostNqn = "nqn.2014-08.org.nvmexpress:uuid:zstack";
    private final static String vhostSocketDir = "/var/run/wds/";

    private static final StorageCapabilities capabilities = new StorageCapabilities();

    private final static long MIN_SIZE = 1024 * 1024 * 1024L;

    private final static long MAX_ISCSI_TARGET_LUN_COUNT = 64;

    static {
        VolumeSnapshotCapability scap = new VolumeSnapshotCapability();
        scap.setSupport(true);
        scap.setArrangementType(VolumeSnapshotCapability.VolumeSnapshotArrangementType.INDIVIDUAL);
        scap.setSupportCreateOnHypervisor(false);
        capabilities.setSnapshotCapability(scap);
        capabilities.setSupportCloneFromVolume(false);
        capabilities.setSupportStorageQos(true);
        capabilities.setSupportLiveExpandVolume(false);
        capabilities.setSupportedImageFormats(Collections.singletonList("raw"));
    }

    enum LunType {
        Volume,
        Snapshot
    }

    public ExponStorageController(ExternalPrimaryStorageVO self) {
        this(self.getUrl());
        this.self = self;
    }

    public ExponStorageController(String url) {
        URI uri = URI.create(url);

        ExponConnectConfig clientConfig = new ExponConnectConfig();
        clientConfig.hostname = uri.getHost();
        clientConfig.port = uri.getPort();
        clientConfig.readTimeout = TimeUnit.MINUTES.toMillis(10);
        clientConfig.writeTimeout = TimeUnit.MINUTES.toMillis(10);
        ExponClient client = new ExponClient();
        client.configure(clientConfig);

        AccountInfo accountInfo = new AccountInfo();
        accountInfo.username = uri.getUserInfo().split(":")[0];
        accountInfo.password = uri.getUserInfo().split(":")[1];

        apiHelper = new ExponApiHelper(accountInfo, client);
    }

    private String protocolToString(VolumeProtocol protocol) {
        if (protocol == VolumeProtocol.NVMEoF) {
            return "nvmf";
        } else if (protocol == VolumeProtocol.Vhost) {
            return "vhost";
        } else if (protocol == VolumeProtocol.iSCSI) {
            return "iscsi";
        } else {
            throw new RuntimeException("not supported protocol " + protocol);
        }
    }

    // TODO cache uss
    private UssGatewayModule getUssGateway(VolumeProtocol protocol, String managerIp) {
        UssGatewayModule uss = apiHelper.queryUssGateway(buildUssGwName(protocolToString(protocol), managerIp));
        if (uss == null) {
            throw new RuntimeException(String.format("cannot find uss gateway for manager ip[%s] and protocol[%s]", managerIp, protocol));
        }

        return uss;
    }

    private VhostControllerModule getOrCreateVhostController(String name) {
        VhostControllerModule vhost = apiHelper.queryVhostController(name);
        if (vhost == null) {
            vhost = apiHelper.createVhostController(name);
        }

        return vhost;
    }

    @Override
    public void activate(BaseVolumeInfo v, HostInventory h, boolean shareable, ReturnValueCompletion<ActiveVolumeTO> comp) {
        ActiveVolumeTO to;
        if (VolumeProtocol.Vhost.toString().equals(v.getProtocol())) {
            to = activeVhostVolume(h, v);
            comp.success(to);
            return;
        } else if (VolumeProtocol.iSCSI.toString().equals(v.getProtocol())) {
            to = activeIscsiVolume(h, v, shareable);
            comp.success(to);
            return;
        }

        comp.fail(operr("not supported protocol[%s]", v.getProtocol()));
    }


    private ActiveVolumeTO activeVhostVolume(HostInventory h, BaseVolumeInfo vol) {
        String vhostName = buildVhostControllerName(vol.getUuid());

        UssGatewayModule uss = getUssGateway(VolumeProtocol.Vhost, h.getManagementIp());
        VolumeModule exponVol = getVolumeModule(vol);
        if (exponVol == null) {
            throw new RuntimeException("volume not found");
        }

        VhostControllerModule vhost = getOrCreateVhostController(vhostName);

        List<UssGatewayModule> boundUss = apiHelper.getVhostControllerBoundUss(vhost.getId());
        if (boundUss.stream().noneMatch(it -> it.getId().equals(uss.getId()))) {
            apiHelper.addVhostVolumeToUss(exponVol.getId(), vhost.getId(), uss.getId());
        }

        // TODO not remove every time
        apiHelper.removeVolumePathFromBlacklist(buildExponVolumeBoundPath(uss, exponVol.getVolumeName()));

        VhostVolumeTO to = new VhostVolumeTO();
        to.setInstallPath(vhost.getPath());
        return to;
    }

    private List<IscsiSeverNode> getIscsiServers(String tianshuId) {
        List<IscsiSeverNode> nodes = apiHelper.getIscsiTargetServer(tianshuId);
        nodes.removeIf(it -> !it.getUssName().startsWith("iscsi_zstack"));
        if (nodes.isEmpty()) {
            throw new RuntimeException("no zstack iscsi uss server found, please create a iscsi uss with prefix iscsi_zstack");
        }

        List<UssGatewayModule> uss = apiHelper.listUssGateway();
        Map<String, String> ussIdAndNetwork = uss.stream()
                .filter(it -> !it.getStatus().equals(HealthStatus.error.name()))
                .collect(Collectors.toMap(UssGatewayModule::getId, UssGatewayModule::getBusinessNetwork));
        nodes.removeIf(it -> !ussIdAndNetwork.containsKey(it.getUssGwId()));

        if (nodes.isEmpty()) {
            throw new RuntimeException("no healthy uss server found");
        }

        // deduplicate same uss iscsi server.
        return nodes.stream().collect(Collectors.groupingBy(IscsiSeverNode::getUssGwId))
                .entrySet().stream().map(it -> {
                    String businessIp = ussIdAndNetwork.get(it.getKey()).split("/")[0];
                    List<IscsiSeverNode> ns = it.getValue();
                    // prefer business network
                    return ns.stream().filter(n -> n.getGatewayIp().equals(businessIp)).findFirst().orElse(ns.get(0));
                }).collect(Collectors.toList());
    }

    private synchronized ActiveVolumeTO activeIscsiVolume(HostInventory h, BaseVolumeInfo vol, boolean shareable) {
        String lunId;
        LunType lunType;
        String source = vol.getInstallPath();
        if (source.contains("@")) {
            lunId = getSnapIdFromPath(source);
            lunType = LunType.Snapshot;
        } else {
            lunId = getVolIdFromPath(source);
            lunType = LunType.Volume;
        }

        String clientIqn = IscsiUtils.getHostInitiatorName(h.getUuid());
        if (clientIqn == null) {
            throw new RuntimeException(String.format("cannot get host[uuid:%s] initiator name", h.getUuid()));
        }

        String tianshuId = addonInfo.getClusters().get(0).getId();
        List<IscsiSeverNode> nodes = getIscsiServers(tianshuId);

        IscsiModule iscsi = "image".equals(vol.getType()) ? allocateImageIscsiTarget(nodes) : allocateIscsiTarget(nodes);

        // for active image, we use one iscsi client group for one iscsi target to save client group count
        String iscsiClientName = "image".equals(vol.getType()) ? iscsi.getName() : buildIscsiVolumeClientName(vol.getUuid());
        IscsiClientGroupModule client = prepareOneToOneIscsiClientGroup(iscsiClientName, iscsi.getId(), tianshuId, clientIqn);

        if (lunType == LunType.Volume && !apiHelper.getVolumeAttachedIscsiClientGroups(lunId).contains(client.getId())) {
            apiHelper.addVolumeToIscsiClientGroup(lunId, client.getId(), iscsi.getId(), shareable);
        } else if (lunType == LunType.Snapshot && !apiHelper.getSnapshotAttachedIscsiClientGroups(lunId).contains(client.getId())) {
            apiHelper.addSnapshotToIscsiClientGroup(lunId, client.getId(), iscsi.getId());
        }

        IscsiVolumeTO to = new IscsiVolumeTO();
        IscsiRemoteTarget target = new IscsiRemoteTarget();
        target.setPort(3260);
        target.setTransport("tcp");
        target.setIqn(iscsi.getIqn());
        target.setIp(nodes.stream().map(IscsiSeverNode::getGatewayIp).collect(Collectors.joining(",")));
        target.setDiskId(getDiskId(lunId, lunType));
        to.setInstallPath(target.getResourceURI());
        return to;
    }

    private IscsiClientGroupModule prepareOneToOneIscsiClientGroup(String iscsiClientName, String iscsiTargetId, String tianshuId, String clientIqn) {
        IscsiClientGroupModule client = apiHelper.queryIscsiClient(iscsiClientName);
        if (client == null) {
            client = apiHelper.createIscsiClient(iscsiClientName, tianshuId, Collections.singletonList(clientIqn));
        } else if (!client.getHosts().contains(clientIqn)) {
            apiHelper.addHostToIscsiClient(clientIqn, client.getId());
        }

        // one iscsi client group can only attach one iscsi target
        if (client.getiscsiGwCount() == 0) {
            apiHelper.addIscsiClientToIscsiTarget(client.getId(), iscsiTargetId);
        }
        return client;
    }

    private String getDiskId(String lunId, LunType lunType) {
        if (lunType == LunType.Volume) {
            VolumeModule vol = apiHelper.getVolume(lunId);
            return vol.getWwn();
        } else {
            VolumeSnapshotModule snap = apiHelper.getVolumeSnapshot(lunId);
            return snap.getWwn();
        }
    }

    private synchronized IscsiModule allocateIscsiTarget(List<IscsiSeverNode> nodes) {
        IscsiModule iscsi;
        String tianshuId = addonInfo.getClusters().get(0).getId();
        if (addonInfo.getCurrentIscsiTargetId() == null) {
            return createIscsiTarget(1, tianshuId, nodes);
        }

        iscsi = apiHelper.getIscsiController(addonInfo.getCurrentIscsiTargetId());
        if (iscsi == null) {
            int currentIscsiTargetIndex = addonInfo.getCurrentIscsiTargetIndex();
            return createIscsiTarget(currentIscsiTargetIndex + 1, tianshuId, nodes);
        }

        if (iscsi.getLunCount() < MAX_ISCSI_TARGET_LUN_COUNT) {
            return iscsi;
        }

        int index = Integer.parseInt(iscsi.getName().substring(iscsi.getName().lastIndexOf("_") + 1));
        return createIscsiTarget(index, tianshuId, nodes);
    }

    private IscsiModule createIscsiTarget(int index, String tianshuId, List<IscsiSeverNode> nodes) {
        IscsiModule iscsi = apiHelper.createIscsiController(buildVolumeIscsiTargetName(index),
                tianshuId, 3260, IscsiUssResource.valueOf(nodes));
        addonInfo.setCurrentIscsiTargetId(iscsi.getId());
        addonInfo.setCurrentIscsiTargetIndex(index);
        SQL.New(ExternalPrimaryStorageVO.class).eq(ExternalPrimaryStorageVO_.uuid, self.getUuid())
                .set(ExternalPrimaryStorageVO_.addonInfo, JSONObjectUtil.toJsonString(addonInfo)).update();
        return iscsi;
    }

    private synchronized IscsiModule allocateImageIscsiTarget(List<IscsiSeverNode> nodes) {
        IscsiModule iscsi;
        String tianshuId = addonInfo.getClusters().get(0).getId();
        if (addonInfo.getCurrentImageIscsiTargetId() == null) {
            return createImageIscsiTarget(1, tianshuId, nodes);
        }

        iscsi = apiHelper.getIscsiController(addonInfo.getCurrentImageIscsiTargetId());
        if (iscsi == null) {
            int currentIscsiImageTargetIndex = addonInfo.getCurrentImageIscsiTargetIndex();
            return createImageIscsiTarget(currentIscsiImageTargetIndex + 1, tianshuId, nodes);
        }

        if (iscsi.getLunCount() < MAX_ISCSI_TARGET_LUN_COUNT) {
            return iscsi;
        }

        int index = Integer.parseInt(iscsi.getName().substring(iscsi.getName().lastIndexOf("_") + 1));
        return createImageIscsiTarget(index, tianshuId, nodes);
    }

    private IscsiModule createImageIscsiTarget(int index, String tianshuId, List<IscsiSeverNode> nodes) {
        IscsiModule iscsi = apiHelper.createIscsiController(buildImageIscsiTargetName(index),
                tianshuId, 3260, IscsiUssResource.valueOf(nodes));
        addonInfo.setCurrentImageIscsiTargetId(iscsi.getId());
        addonInfo.setCurrentImageIscsiTargetIndex(index);
        SQL.New(ExternalPrimaryStorageVO.class).eq(ExternalPrimaryStorageVO_.uuid, self.getUuid())
                .set(ExternalPrimaryStorageVO_.addonInfo, JSONObjectUtil.toJsonString(addonInfo)).update();
        return iscsi;
    }

    synchronized IscsiRemoteTarget exportIscsi(ExportSpec espec) {
        String lunId;
        LunType lunType;
        String source = espec.getInstallPath();
        if (source.contains("@")) {
            lunId = getSnapIdFromPath(source);
            lunType = LunType.Snapshot;
        } else {
            lunId = getVolIdFromPath(source);
            lunType = LunType.Volume;
        }

        String tianshuId = addonInfo.getClusters().get(0).getId();
        List<IscsiSeverNode> nodes = getIscsiServers(tianshuId);

        IscsiModule iscsi = apiHelper.queryIscsiController(iscsiExportTargetName);
        if (iscsi == null) {
            iscsi = apiHelper.createIscsiController(iscsiExportTargetName, tianshuId, 3260, IscsiUssResource.valueOf(nodes));
        }

        String iscsiClientName = buildIscsiExportClientName(espec.getClientMnIp());
        IscsiClientGroupModule client = prepareOneToOneIscsiClientGroup(iscsiClientName, iscsi.getId(), tianshuId, espec.getClientQualifiedName());

        if (lunType == LunType.Volume) {
            apiHelper.addVolumeToIscsiClientGroup(lunId, client.getId(), iscsi.getId(), false);
        } else {
            apiHelper.addSnapshotToIscsiClientGroup(lunId, client.getId(), iscsi.getId());
        }

        IscsiRemoteTarget target = new IscsiRemoteTarget();
        target.setPort(3260);
        target.setTransport("tcp");
        target.setIqn(iscsi.getIqn());
        target.setIp(nodes.stream().map(IscsiSeverNode::getGatewayIp).collect(Collectors.joining(",")));
        target.setDiskId(getDiskId(lunId, lunType));
        return target;
    }

    @Override
    public String getActivePath(BaseVolumeInfo v, HostInventory h, boolean shareable) {
        if (VolumeProtocol.Vhost.toString().equals(v.getProtocol())) {
            String vhostName = buildVhostControllerName(v.getUuid());
            VhostControllerModule vhost = getOrCreateVhostController(vhostName);
            return vhost.getPath();
        } else if (VolumeProtocol.iSCSI.toString().equals(v.getProtocol())) {
            String lunId;
            LunType lunType;
            List<String> clientIds;
            if (v.getInstallPath().contains("@")) {
                lunId = getSnapIdFromPath(v.getInstallPath());
                lunType = LunType.Snapshot;
                clientIds = apiHelper.getSnapshotAttachedIscsiClientGroups(lunId);
            } else {
                lunId = getVolIdFromPath(v.getInstallPath());
                lunType = LunType.Volume;
                clientIds = apiHelper.getVolumeAttachedIscsiClientGroups(lunId);
            }

            if (clientIds.isEmpty()) {
                return null;
            }

            IscsiModule iscsi = apiHelper.getIscsiClientAttachedTargets(clientIds.get(0)).get(0);
            List<IscsiSeverNode> nodes = getIscsiServers(addonInfo.getClusters().get(0).getId());

            IscsiRemoteTarget target = new IscsiRemoteTarget();
            target.setPort(3260);
            target.setTransport("tcp");
            target.setIqn(iscsi.getIqn());
            target.setIp(nodes.stream().map(IscsiSeverNode::getGatewayIp).collect(Collectors.joining(",")));
            target.setDiskId((getDiskId(lunId, lunType)));
            return target.getResourceURI();
        }

        throw new OperationFailureException(operr("not supported protocol[%s]", v.getProtocol()));
    }

    @Override
    public BaseVolumeInfo getActiveVolumeInfo(String activePath, HostInventory h, boolean shareable) {
        BaseVolumeInfo info = new BaseVolumeInfo();
        String volUuid = null;
        if (activePath.startsWith(vhostSocketDir)) {
            volUuid = getVolumeUuidFromVhostControllerPath(activePath);
            info.setUuid(volUuid);
            info.setProtocol(VolumeProtocol.Vhost.toString());
            info.setShareable(shareable);
        } else {
            // TODO support other protocols
        }


        VolumeModule vol = apiHelper.queryVolume(buildVolumeName(volUuid));
        if (vol == null) {
            return info;
        }

        info.setInstallPath(buildExponPath(vol.getPoolName(), vol.getId()));
        return info;
    }

    @Override
    public List<String> getActiveVolumesLocation(HostInventory h) {
        // TODO support other protocols
        return Collections.singletonList("file://" + PathUtil.join(vhostSocketDir, "volume-*"));
    }

    @Override
    public List<ActiveVolumeClient> getActiveClients(String installPath, String protocol) {
        if (VolumeProtocol.Vhost.toString().equals(protocol)) {
            VolumeModule vol = apiHelper.getVolume(getVolIdFromPath(installPath));
            if (vol == null) {
                return Collections.emptyList();
            }

            String volUuid = getVolumeInfo(vol.getName()).getUuid();
            String vhostName = buildVhostControllerName(volUuid);
            VhostControllerModule vhost = apiHelper.queryVhostController(vhostName);
            if (vhost == null) {
                return Collections.emptyList();
            }

            List<UssGatewayModule> uss = apiHelper.getVhostControllerBoundUss(vhost.getId());
            return uss.stream().map(it -> {
                ActiveVolumeClient client = new ActiveVolumeClient();
                client.setManagerIp(getUssManagerIp(it.getName()));
                return client;
            }).collect(Collectors.toList());
        } else if (VolumeProtocol.iSCSI.toString().equals(protocol)) {
            IscsiClientGroupModule client = getLunAttachedIscsiClient(installPath);
            if (client == null) {
                return Collections.emptyList();
            }

            return client.getHosts().stream().map(it -> {
                ActiveVolumeClient c = new ActiveVolumeClient();
                if (it.contains("iqn")) {
                    c.setQualifiedName(it);
                    c.setManagerIp(getHostMnIpFromInitiatorName(it));
                } else {
                    c.setManagerIp(it);
                }
                return c;
            }).collect(Collectors.toList());
        } else {
            throw new OperationFailureException(operr("not supported protocol[%s] for active", protocol));
        }
    }

    private IscsiClientGroupModule getLunAttachedIscsiClient(String installPath) {
        List<String> clientIds;
        if (installPath.contains("@")) {
            String lunId = getSnapIdFromPath(installPath);
            clientIds = apiHelper.getSnapshotAttachedIscsiClientGroups(lunId);
        } else {
            String lunId = getVolIdFromPath(installPath);
            clientIds = apiHelper.getVolumeAttachedIscsiClientGroups(lunId);
        }

        if (clientIds.isEmpty()) {
            return null;
        }

        // one lun can only attach to one client group
        return apiHelper.getIscsiClient(clientIds.get(0));
    }

    @Override
    public void deactivate(String installPath, String protocol, HostInventory h, Completion comp) {
        logger.debug(String.format("deactivating volume[path: %s, protocol:%s] on host[uuid:%s, ip:%s]",
                installPath, protocol, h.getUuid(), h.getManagementIp()));
        if (VolumeProtocol.Vhost.toString().equals(protocol)) {
            deactivateVhost(installPath, h);
            comp.success();
            return;
        } else if (VolumeProtocol.iSCSI.toString().equals(protocol)) {
            // iscsi target is shared by all hosts, we cannot control one volume on one host for now.
            deactivateIscsi(installPath, h);
            comp.success();
            return;
        }

        comp.fail(operr("not supported protocol[%s] for deactivate", protocol));
    }

    public void cleanActiveRecord(VolumeInventory vol) {
        if (vol.getProtocol().equals(VolumeProtocol.Vhost.toString())) {
            String vhostName = buildVhostControllerName(vol.getUuid());
            VhostControllerModule vhost = apiHelper.queryVhostController(vhostName);
            if (vhost != null) {
                apiHelper.deleteVhostController(vhost.getId());
            }
        } else if (vol.getProtocol().equals(VolumeProtocol.iSCSI.toString())) {
            String iscsiClientName = buildIscsiVolumeClientName(vol.getUuid());
            IscsiClientGroupModule client = apiHelper.queryIscsiClient(iscsiClientName);
            if (client != null) {
                apiHelper.deleteIscsiClient(client.getId());
            }
        }
    }

    public void cleanActiveRecord(ImageCacheInventory cache) {
        // TODO
    }

    @Override
    public void blacklist(String installPath, String protocol, HostInventory h, Completion comp) {
        logger.debug(String.format("blacklisting volume[path: %s, protocol:%s] on host[uuid:%s, ip:%s]",
                installPath, protocol, h.getUuid(), h.getManagementIp()));

        UssGatewayModule uss = getUssGateway(VolumeProtocol.valueOf(protocol), h.getManagementIp());
        VolumeModule exponVol = apiHelper.getVolume(getVolIdFromPath(installPath));
        apiHelper.addVolumePathToBlacklist(buildExponVolumeBoundPath(uss, exponVol.getVolumeName()));
        comp.success();
    }

    @Override
    public synchronized void activateHeartbeatVolume(HostInventory h, ReturnValueCompletion<HeartbeatVolumeTO> comp) {
        String clientIqn = IscsiUtils.getHostInitiatorName(h.getUuid());
        if (clientIqn == null) {
            throw new RuntimeException(String.format("cannot get host[uuid:%s] initiator name", h.getUuid()));
        }

        String tianshuId = addonInfo.getClusters().get(0).getId();
        List<IscsiSeverNode> nodes = getIscsiServers(tianshuId);

        IscsiModule iscsi = apiHelper.queryIscsiController(iscsiHeartbeatTargetName);
        if (iscsi == null) {
            iscsi = apiHelper.createIscsiController(iscsiHeartbeatTargetName, tianshuId, 3260, IscsiUssResource.valueOf(nodes));
        }

        VolumeModule heartbeatVol = apiHelper.queryVolume(iscsiHeartbeatVolumeName);
        if (heartbeatVol == null) {
            long size = SizeUnit.GIGABYTE.toByte(2);
            heartbeatVol = apiHelper.createVolume(iscsiHeartbeatVolumeName, allocateFreePool(size).getId(), size);
        }

        IscsiClientGroupModule client = prepareOneToOneIscsiClientGroup(iscsiHeartbeatClientName, iscsi.getId(), tianshuId, clientIqn);
        if (client.getVolNum() == 0) {
            apiHelper.addVolumeToIscsiClientGroup(heartbeatVol.getId(), client.getId(), iscsi.getId(), false);
        }

        IscsiHeartbeatVolumeTO to = new IscsiHeartbeatVolumeTO();
        IscsiRemoteTarget target = new IscsiRemoteTarget();
        target.setPort(3260);
        target.setTransport("tcp");
        target.setIqn(iscsi.getIqn());
        target.setIp(nodes.stream().map(IscsiSeverNode::getGatewayIp).collect(Collectors.joining(",")));
        target.setDiskId(heartbeatVol.getWwn());
        to.setInstallPath(target.getResourceURI());
        to.setHostId(getHostId(h));
        to.setHeartbeatRequiredSpace(SizeUnit.MEGABYTE.toByte(1));
        to.setCoveringPaths(Collections.singletonList(vhostSocketDir));
        comp.success(to);
    }

    // hardcode
    private int getHostId(HostInventory host) {
        UssGatewayModule uss = getUssGateway(VolumeProtocol.Vhost, host.getManagementIp());
        return uss.getServerNo();
    }

    @Override
    public void deactivateHeartbeatVolume(HostInventory h, Completion comp) {
        VolumeModule heartbeatVol = apiHelper.queryVolume(iscsiHeartbeatVolumeName);
        if (heartbeatVol == null) {
            comp.success();
            return;
        }

        IscsiModule iscsi = apiHelper.queryIscsiController(iscsiHeartbeatTargetName);
        if (iscsi == null) {
            comp.success();
            return;
        }

        IscsiClientGroupModule client = apiHelper.queryIscsiClient(iscsiHeartbeatClientName);
        if (client == null) {
            comp.success();
            return;
        }

        String clientIqn = IscsiUtils.getHostInitiatorName(h.getUuid());
        if (clientIqn == null) {
            throw new RuntimeException(String.format("cannot get host[uuid:%s] initiator name", h.getUuid()));
        }

        if (client.getHosts().contains(clientIqn)) {
            apiHelper.removeHostFromIscsiClient(clientIqn, client.getId());
        }

        comp.success();
    }

    @Override
    public HeartbeatVolumeTO getHeartbeatVolumeActiveInfo(HostInventory h) {
        String tianshuId = addonInfo.getClusters().get(0).getId();
        List<IscsiSeverNode> nodes = getIscsiServers(tianshuId);

        IscsiModule iscsi = apiHelper.queryIscsiController(iscsiHeartbeatTargetName);
        if (iscsi == null) {
            throw new RuntimeException("heartbeat iscsi target not found");
        }

        VolumeModule heartbeatVol = apiHelper.queryVolume(iscsiHeartbeatVolumeName);
        if (heartbeatVol == null) {
            throw new RuntimeException("heartbeat volume not found");
        }

        IscsiHeartbeatVolumeTO to = new IscsiHeartbeatVolumeTO();
        IscsiRemoteTarget target = new IscsiRemoteTarget();
        target.setPort(3260);
        target.setTransport("tcp");
        target.setIqn(iscsi.getIqn());
        target.setIp(nodes.stream().map(IscsiSeverNode::getGatewayIp).collect(Collectors.joining(",")));
        target.setDiskId(heartbeatVol.getWwn());
        to.setInstallPath(target.getResourceURI());
        to.setHostId(getHostId(h));
        to.setHeartbeatRequiredSpace(SizeUnit.MEGABYTE.toByte(1));
        to.setCoveringPaths(Collections.singletonList(vhostSocketDir));
        return to;
    }

    private void deactivateVhost(String installPath, HostInventory h) {
        String volId = getVolIdFromPath(installPath);
        VolumeModule vol = apiHelper.getVolume(volId);
        if (vol == null) {
            return;
        }

        String volUuid = getVolumeInfo(vol.getName()).getUuid();
        String vhostName = buildVhostControllerName(volUuid);

        UssGatewayModule uss = getUssGateway(VolumeProtocol.Vhost, h.getManagementIp());
        VhostControllerModule vhost = apiHelper.queryVhostController(vhostName);
        if (vhost == null) {
            return;
        }

        retry(() -> apiHelper.removeVhostVolumeFromUss(volId, vhost.getId(), uss.getId()));
    }

    private void deactivateIscsi(String installPath, HostInventory h) {
        IscsiClientGroupModule client = getLunAttachedIscsiClient(installPath);
        if (client == null) {
            return;
        }

        int lunCount = client.getSnapNum() + client.getVolNum();
        if (lunCount > 1) {
            logger.debug(String.format("%s attached sharable iscsi client group[lun count: %d], skip deactivate", installPath, lunCount));
            return;
        }

        String iqn = IscsiUtils.getHostInitiatorName(h.getUuid());
        if (iqn == null) {
            throw new RuntimeException(String.format("cannot get host[uuid:%s] initiator name", h.getUuid()));
        }

        if (client.getHosts().contains(iqn)) {
            apiHelper.removeHostFromIscsiClient(iqn, client.getId());
        }
    }

    private synchronized void unexportIscsi(String source, String clientIp) {
        String lunId;
        LunType lunType;
        if (source.contains("@")) {
            lunId = getSnapIdFromPath(source);
            lunType = LunType.Snapshot;
        } else {
            lunId = getVolIdFromPath(source);
            lunType = LunType.Volume;
        }

        String iscsiClientName = buildIscsiExportClientName(clientIp);
        IscsiClientGroupModule client = apiHelper.queryIscsiClient(iscsiClientName);
        if (client == null) {
            return;
        }

        if (lunType == LunType.Volume) {
            apiHelper.removeVolumeFromIscsiClientGroup(lunId, client.getId());
        } else {
            apiHelper.removeSnapshotFromIscsiClientGroup(lunId, client.getId());
        }

        /*
        apiHelper.removeIscsiClientFromIscsiTarget(client.getId(), iscsi.getId());
        apiHelper.unbindIscsiTargetToUss(iscsi.getId(), uss.getId());
        apiHelper.deleteIscsiController(iscsi.getId());
        apiHelper.deleteIscsiClient(client.getId());
         */
    }

    private VolumeModule getVolumeModule(BaseVolumeInfo vol) {
        if (vol.getInstallPath() != null) {
            String volId = getVolIdFromPath(vol.getInstallPath());
            return apiHelper.getVolume(volId);
        }

        if ("image".equals(vol.getType())) {
            return apiHelper.queryVolume(buildImageName(vol.getUuid()));
        } else {
            return apiHelper.queryVolume(buildVolumeName(vol.getUuid()));
        }
    }

    @Override
    public String getIdentity() {
        return ExponConstants.IDENTITY;
    }

    @Override
    public void connect(String config, String url, ReturnValueCompletion<LinkedHashMap> comp) {
        apiHelper.login();
        ExponAddonInfo info = new ExponAddonInfo();

        List<FailureDomainModule> pools = apiHelper.queryPools();
        if (CollectionUtils.isEmpty(pools)) {
            comp.fail(operr("no pool found"));
            return;
        }

        pools = pools.stream().map(it -> apiHelper.getPool(it.getId())).collect(Collectors.toList());
        info.setPools(pools.stream().map(ExponAddonInfo.Pool::valueOf).collect(Collectors.toList()));
        List<TianshuClusterModule> clusters = apiHelper.queryClusters();
        info.setClusters(clusters.stream().map(ExponAddonInfo.TianshuCluster::valueOf).collect(Collectors.toList()));

        List<IscsiModule> iscsiTargets = apiHelper.listIscsiController();
        iscsiTargets.removeIf(it -> !it.getName().startsWith(iscsiTargetPrefix) && !it.getName().startsWith(iscsiTargetImagePrefix));
        if (!iscsiTargets.isEmpty()) {
            iscsiTargets.stream().filter(it -> it.getName().startsWith(iscsiTargetPrefix))
                    .max(Comparator.comparingInt(o -> Integer.parseInt(o.getName().substring(o.getName().lastIndexOf("_") + 1)))).ifPresent(i -> {
                info.setCurrentIscsiTargetId(i.getId());
                info.setCurrentIscsiTargetIndex(Integer.parseInt(i.getName().substring(i.getName().lastIndexOf("_") + 1)));
            });

            iscsiTargets.stream().filter(it -> it.getName().startsWith(iscsiTargetImagePrefix))
                    .max(Comparator.comparingInt(o -> Integer.parseInt(o.getName().substring(o.getName().lastIndexOf("_") + 1)))).ifPresent(i -> {
                info.setCurrentImageIscsiTargetId(i.getId());
                info.setCurrentImageIscsiTargetIndex(Integer.parseInt(i.getName().substring(i.getName().lastIndexOf("_") + 1)));
            });
        }
        addonInfo = info;
        setTrashExpireTime(PrimaryStorageGlobalConfig.TRASH_EXPIRATION_TIME.value(Integer.class), new NopeCompletion());
        comp.success(JSONObjectUtil.rehashObject(addonInfo, LinkedHashMap.class));
    }

    private void reloadDbInfo() {
        self = dbf.reload(self);
        addonInfo = StringUtils.isEmpty(self.getAddonInfo()) ? new ExponAddonInfo() : JSONObjectUtil.toObject(self.getAddonInfo(), ExponAddonInfo.class);
        config = StringUtils.isEmpty(self.getConfig()) ? new ExponConfig() : JSONObjectUtil.toObject(self.getConfig(), ExponConfig.class);
    }

    @Override
    public void reportCapacity(ReturnValueCompletion<StorageCapacity> comp) {
        reloadDbInfo();

        List<FailureDomainModule> pools = getSelfPools();
        long total = pools.stream().mapToLong(FailureDomainModule::getValidSize).sum();
        long avail = total - pools.stream().mapToLong(FailureDomainModule::getRealDataSize).sum();
        StorageCapacity cap = new StorageCapacity();
        cap.setHealthy(getHealthy(pools));
        cap.setAvailableCapacity(avail);
        cap.setTotalCapacity(total);
        comp.success(cap);
    }

    private StorageHealthy getHealthy(List<FailureDomainModule> pools) {
        if (pools.stream().allMatch(it -> it.getHealthStatus().equals(HealthStatus.health.name()))) {
            return StorageHealthy.Ok;
        } else if (pools.stream().allMatch(it -> it.getHealthStatus().equals(HealthStatus.error.name()))) {
            return StorageHealthy.Failed;
        } else {
            return StorageHealthy.Warn;
        }
    }

    @Override
    public void reportHealthy(ReturnValueCompletion<StorageHealthy> comp) {
        self = dbf.reload(self);
        addonInfo = JSONObjectUtil.toObject(self.getAddonInfo(), ExponAddonInfo.class);
        config = JSONObjectUtil.toObject(self.getConfig(), ExponConfig.class);

        List<FailureDomainModule> pools = getSelfPools();
        comp.success(getHealthy(pools));
    }

    @Override
    public void reportNodeHealthy(HostInventory host, ReturnValueCompletion<NodeHealthy> comp) {
        NodeHealthy healthy = new NodeHealthy();
        Arrays.asList(VolumeProtocol.Vhost).forEach(it -> {
            String ussName = buildUssGwName(protocolToString(it), host.getManagementIp());
            UssGatewayModule uss = apiHelper.queryUssGateway(ussName);
            if (uss == null) {
                healthy.setHealthy(it, StorageHealthy.Failed);
            } else if (uss.getStatus().equals(HealthStatus.health.name())) {
                healthy.setHealthy(it, StorageHealthy.Ok);
            } else if (uss.getStatus().equals(HealthStatus.error.name())) {
                healthy.setHealthy(it, StorageHealthy.Failed);
            } else if (uss.getStatus().equals(HealthStatus.warning.name())){
                healthy.setHealthy(it, StorageHealthy.Warn);
            } else {
                healthy.setHealthy(it, StorageHealthy.Unknown);
            }
        });

        comp.success(healthy);
    }

    private List<FailureDomainModule> getSelfPools() {
        Set<String> configPoolNames = config.getPoolNames();
        Set<String> poolIds = addonInfo.getPools().stream().filter(it -> configPoolNames.contains(it.getName()))
                .map(ExponAddonInfo.Pool::getId).collect(Collectors.toSet());

        List<FailureDomainModule> pools = apiHelper.queryPools();
        pools.removeIf(it -> !poolIds.contains(it.getId()));
        pools = pools.stream().map(it -> apiHelper.getPool(it.getId())).collect(Collectors.toList());
        return pools;
    }

    private String getPoolId(String name) {
        return addonInfo.getPools().stream().filter(it -> it.getName().equals(name)).findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("cannot find pool[name:%s]", name))).getId();
    }

    @Override
    public StorageCapabilities reportCapabilities() {
        return capabilities;
    }

    @Override
    public String allocateSpace(AllocateSpaceSpec aspec) {
        // TODO allocate pool
        FailureDomainModule pool = allocateFreePool(aspec.getSize());
        if (pool == null) {
            throw new OperationFailureException(operr("no available pool with enough space[%d] and healthy status", aspec.getSize()));
        }

        return buildExponPath(pool.getFailureDomainName(), "");
    }

    private FailureDomainModule allocateFreePool(long size) {
        List<FailureDomainModule> pools = getSelfPools();
        return pools.stream().filter(it -> !it.getHealthStatus().equals(HealthStatus.error.name()) &&
                        it.getValidSize() - it.getRealDataSize() > size)
                .max(Comparator.comparingLong(FailureDomainModule::getAvailableCapacity))
                .orElse(null);
    }

    @Override
    public void createVolume(CreateVolumeSpec v, ReturnValueCompletion<VolumeStats> comp) {
        String poolName;
        v.setSize(Math.max(v.getSize(), MIN_SIZE));

        if (v.getAllocatedUrl() == null) {
            FailureDomainModule pool = allocateFreePool(v.getSize());
            if (pool == null) {
                comp.fail(operr("no available pool with enough space[%d] and healthy status", v.getSize()));
                return;
            }
            poolName = pool.getFailureDomainName();
        } else {
            poolName = getPoolNameFromPath(v.getAllocatedUrl());
        }

        String poolId = getPoolId(poolName);

        VolumeModule exponVol = apiHelper.createVolume(v.getName(), poolId, v.getSize());
        VolumeStats stats = new VolumeStats();
        stats.setInstallPath(buildExponPath(poolName, exponVol.getId()));
        stats.setSize(exponVol.getVolumeSize());
        stats.setActualSize(exponVol.getDataSize());
        stats.setFormat(VolumeConstant.VOLUME_FORMAT_RAW);
        comp.success(stats);
    }

    @Override
    public void deleteVolume(String installPath, Completion comp) {
        String volId = getVolIdFromPath(installPath);
        apiHelper.deleteVolume(volId, true);
        comp.success();
    }

    @Override
    public void deleteVolumeAndSnapshot(String installPath, Completion comp) {
        String volId = getVolIdFromPath(installPath);
        apiHelper.deleteVolume(volId, true);
        comp.success();
    }

    @Override
    public void trashVolume(String installPath, Completion comp) {
        String volId = getVolIdFromPath(installPath);
        apiHelper.deleteVolume(volId, false);
        comp.success();
    }

    @Override
    public void cloneVolume(String srcInstallPath, CreateVolumeSpec dst, ReturnValueCompletion<VolumeStats> comp) {
        String snapId = getSnapIdFromPath(srcInstallPath);
        VolumeModule vol = apiHelper.cloneVolume(snapId, dst.getName(), ExponVolumeQos.valueOf(dst.getQos()));
        if (vol.getVolumeSize() < dst.getSize()) {
            vol = apiHelper.expandVolume(vol.getId(), dst.getSize());
        }

        VolumeStats stats = new VolumeStats();
        stats.setInstallPath(buildExponPath(getPoolNameFromPath(srcInstallPath), vol.getId()));
        stats.setFormat(VolumeConstant.VOLUME_FORMAT_RAW);
        stats.setSize(vol.getVolumeSize());
        stats.setActualSize(vol.getDataSize());
        comp.success(stats);
    }

    @Override
    public void copyVolume(String srcInstallPath, CreateVolumeSpec dst, ReturnValueCompletion<VolumeStats> comp) {
        String snapId = getSnapIdFromPath(srcInstallPath);
        VolumeSnapshotModule snap = apiHelper.getVolumeSnapshot(snapId);

        apiHelper.copySnapshot(snapId, snap.getPoolId(), dst.getName(), ExponVolumeQos.valueOf(dst.getQos()), new ReturnValueCompletion<VolumeModule>(comp) {
            @Override
            public void success(VolumeModule vol) {
                VolumeStats stats = new VolumeStats();
                stats.setInstallPath(buildExponPath(getPoolNameFromPath(srcInstallPath), vol.getId()));
                stats.setFormat(VolumeConstant.VOLUME_FORMAT_RAW);
                stats.setSize(vol.getVolumeSize());
                stats.setActualSize(vol.getDataSize());
                comp.success(stats);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                comp.fail(errorCode);
            }
        });
    }

    @Override
    public void flattenVolume(String installPath, ReturnValueCompletion<VolumeStats> comp) {
        // TODO flatten snapshot
        stats(installPath, comp);
    }

    @Override
    public void stats(String installPath, ReturnValueCompletion<VolumeStats> comp) {
        VolumeModule vol = apiHelper.getVolume(getVolIdFromPath(installPath));
        VolumeStats stats = new VolumeStats();
        stats.setInstallPath(installPath);
        stats.setSize(vol.getVolumeSize());
        stats.setActualSize(vol.getDataSize());
        stats.setFormat(VolumeConstant.VOLUME_FORMAT_RAW);
        comp.success(stats);
    }

    @Override
    public void batchStats(Collection<String> installPath, ReturnValueCompletion<List<VolumeStats>> comp) {
        List<VolumeStats> stats = installPath.stream().map(it -> {
            VolumeModule vol = apiHelper.getVolume(getVolIdFromPath(it));
            VolumeStats s = new VolumeStats();
            s.setInstallPath(it);
            s.setSize(vol.getVolumeSize());
            s.setActualSize(vol.getDataSize());
            s.setFormat(VolumeConstant.VOLUME_FORMAT_RAW);
            return s;
        }).collect(Collectors.toList());
        comp.success(stats);
    }

    @Override
    public void expandVolume(String installPath, long size, ReturnValueCompletion<VolumeStats> comp) {
        VolumeModule vol = apiHelper.expandVolume(getVolIdFromPath(installPath), size);
        VolumeStats stats = new VolumeStats();
        stats.setInstallPath(installPath);
        stats.setSize(vol.getVolumeSize());
        comp.success(stats);
    }

    @Override
    public void setVolumeQos(BaseVolumeInfo v, Completion comp) {
        apiHelper.setVolumeQos(getVolIdFromPath(v.getInstallPath()), ExponVolumeQos.valueOf(v.getQos()));
        comp.success();
    }

    @Override
    public void export(ExportSpec espec, VolumeProtocol protocol, ReturnValueCompletion<RemoteTarget> comp) {
        if (protocol == VolumeProtocol.NVMEoF) {
            NvmeRemoteTarget target = exportNvmf(espec);
            comp.success(target);
        } else if (protocol == VolumeProtocol.iSCSI) {
            IscsiRemoteTarget target = exportIscsi(espec);
            comp.success(target);
        } else {
            throw new RuntimeException("unsupport target " + protocol.name());
        }
    }

    synchronized NvmeRemoteTarget exportNvmf(ExportSpec espec) {
        String lunId;
        LunType lunType;
        String source = espec.getInstallPath();
        if (source.contains("@")) {
            lunId = getSnapIdFromPath(source);
            lunType = LunType.Snapshot;
        } else {
            lunId = getVolIdFromPath(source);
            lunType = LunType.Volume;
        }

        String tianshuId = addonInfo.getClusters().get(0).getId();
        UssGatewayModule uss = getUssGateway(VolumeProtocol.NVMEoF, "zstack");

        String nvmfControllerName = "nvmf_zstack";
        NvmfModule nvmf = apiHelper.queryNvmfController(nvmfControllerName);
        if (nvmf == null) {
            nvmf = apiHelper.createNvmfController(nvmfControllerName, tianshuId, lunId);
        }

        NvmfBoundUssGatewayRefModule ref = apiHelper.getNvmfBoundUssGateway(nvmf.getId(), uss.getId());
        if (ref == null) {
            ref = apiHelper.bindNvmfTargetToUss(nvmf.getId(), uss.getId(), 4420);
        }

        String nvmfClientName = "nvmf_zstack";
        NvmfClientGroupModule client = apiHelper.queryNvmfClient(nvmfClientName);
        if (client == null) {
            client = apiHelper.createNvmfClient(nvmfClientName, tianshuId, Collections.singletonList(hostNqn));
            apiHelper.addNvmfClientToNvmfTarget(client.getId(), nvmf.getId());
        }

        if (lunType == LunType.Volume) {
            apiHelper.addVolumeToNvmfClientGroup(lunId, client.getId(), nvmf.getId());
        } else {
            apiHelper.addSnapshotToNvmfClientGroup(lunId, client.getId(), nvmf.getId());
        }

        NvmeRemoteTarget target = new NvmeRemoteTarget();
        target.setHostNqn(hostNqn);
        target.setPort(4420);
        target.setTransport("tcp");
        target.setNqn(nvmf.getNqn());
        target.setIp(ref.getBindIp());
        target.setDiskId(lunId);
        return target;
    }

    @Override
    public void unexport(ExportSpec espec, VolumeProtocol protocol, Completion comp) {
        if (protocol == VolumeProtocol.NVMEoF) {
            unexportNvmf(espec.getInstallPath());
        } else if (protocol == VolumeProtocol.iSCSI) {
            unexportIscsi(espec.getInstallPath(), espec.getClientMnIp());
        } else {
            comp.fail(operr("unsupported protocol %s", protocol.name()));
            return;
        }
        comp.success();
    }

    private synchronized void unexportNvmf(String source) {
        String lunId;
        LunType lunType;
        if (source.contains("@")) {
            lunId = getSnapIdFromPath(source);
            lunType = LunType.Snapshot;
        } else {
            lunId = getVolIdFromPath(source);
            lunType = LunType.Volume;
        }

        // UssGatewayModule uss = getUssGateway(VolumeProtocol.NVMEoF, "zstack");

        String nvmfControllerName = "nvmf_zstack";
        NvmfModule nvmf = apiHelper.queryNvmfController(nvmfControllerName);

        String nvmfClientName = "nvmf_zstack";
        NvmfClientGroupModule client = apiHelper.queryNvmfClient(nvmfClientName);
        if (nvmf == null || client == null) {
            return;
        }

        if (lunType == LunType.Volume) {
            apiHelper.removeVolumeFromNvmfClientGroup(lunId, client.getId());
        } else {
            apiHelper.removeSnapshotFromNvmfClientGroup(lunId, client.getId());
        }

        /*
        apiHelper.removeNvmfClientFromNvmfTarget(client.getId(), nvmf.getId());
        apiHelper.unbindNvmfTargetToUss(nvmf.getId(), uss.getId());
        apiHelper.deleteNvmfController(nvmf.getId());
        apiHelper.deleteNvmfClient(client.getId());
         */
    }

    @Override
    public void createSnapshot(CreateVolumeSnapshotSpec spec, ReturnValueCompletion<VolumeSnapshotStats> comp) {
        VolumeSnapshotModule snapshot = apiHelper.createVolumeSnapshot(
                getVolIdFromPath(spec.getVolumeInstallPath()), spec.getName(), "todo");
        VolumeSnapshotStats stats = new VolumeSnapshotStats();
        stats.setInstallPath(buildExponSnapshotPath(spec.getVolumeInstallPath(), snapshot.getId()));
        stats.setActualSize(snapshot.getDataSize());
        comp.success(stats);
    }

    @Override
    public void deleteSnapshot(String installPath, Completion comp) {
        apiHelper.deleteVolumeSnapshot(getSnapIdFromPath(installPath));
        comp.success();
    }

    @Override
    public void revertVolumeSnapshot(String snapshotInstallPath, ReturnValueCompletion<VolumeStats> comp) {
        String volId = getVolIdFromPath(snapshotInstallPath);
        String snapId = getSnapIdFromPath(snapshotInstallPath);
        String poolName = getPoolNameFromPath(snapshotInstallPath);
        VolumeModule vol = apiHelper.recoverySnapshot(volId, snapId);

        VolumeStats stats = new VolumeStats();
        stats.setInstallPath(buildExponPath(poolName, volId));
        stats.setSize(vol.getVolumeSize());
        stats.setActualSize(vol.getDataSize());
        stats.setFormat(VolumeConstant.VOLUME_FORMAT_RAW);
        comp.success(stats);
    }

    @Override
    public void validateConfig(String config) {

    }

    @Override
    public void setTrashExpireTime(int timeInSeconds, Completion completion) {
        // set trash expire time in days in advanced method
        int days = timeInSeconds / 3600 / 24;
        if (timeInSeconds % (3600 * 24) > 0) {
            days++;
        }

        apiHelper.setTrashExpireTime(days);
        completion.success();
    }

    private void retry(Runnable r) {
        retry(r, 3);
    }

    private void retry(Runnable r, int retry) {
        while (retry-- > 0) {
            try {
                r.run();
                return;
            } catch (Exception e) {
                logger.warn("runnable failed, try ", e);
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException ignore) {}
            }
        }
    }
}

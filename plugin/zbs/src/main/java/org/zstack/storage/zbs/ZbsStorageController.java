package org.zstack.storage.zbs;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.cbd.*;
import org.zstack.cbd.kvm.CbdHeartbeatVolumeTO;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.core.workflow.ShareFlow;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.WhileDoneCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.errorcode.SysErrors;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.host.HostInventory;
import org.zstack.header.rest.RESTFacade;
import org.zstack.header.storage.addon.*;
import org.zstack.header.storage.addon.primary.*;
import org.zstack.header.storage.primary.PrimaryStorageStatus;
import org.zstack.header.storage.primary.VolumeSnapshotCapability;
import org.zstack.header.storage.snapshot.VolumeSnapshotStats;
import org.zstack.header.volume.VolumeConstant;
import org.zstack.header.volume.VolumeProtocol;
import org.zstack.header.volume.VolumeStats;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.data.SizeUnit;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.zstack.core.Platform.operr;

/**
 * @author Xingwei Yu
 * @date 2024/3/21 13:11
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class ZbsStorageController implements PrimaryStorageControllerSvc, PrimaryStorageNodeSvc {
    private static final CLogger logger = Utils.getLogger(ZbsStorageController.class);

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    protected RESTFacade restf;

    private ExternalPrimaryStorageVO self;
    private AddonInfo addonInfo;
    private Config config;

    public static final String GET_FACTS_PATH = "/zbs/primarystorage/facts";
    public static final String GET_CAPACITY_PATH = "/zbs/primarystorage/capacity";
    public static final String CREATE_VOLUME_PATH = "/zbs/primarystorage/volume/create";
    public static final String DELETE_VOLUME_PATH = "/zbs/primarystorage/volume/delete";
    public static final String CBD_TO_NBD_PATH = "/zbs/primarystorage/volume/cbd2nbd";
    public static final String CREATE_SNAPSHOT_PATH = "/zbs/primarystorage/snapshot/create";
    public static final String CLONE_VOLUME_PATH = "/zbs/primarystorage/volume/clone";
    public static final String QUERY_VOLUME_PATH = "/zbs/primarystorage/volume/query";

    private static final String ZBS_CBD_LUN_PATH_FORMAT = "cbd:%s/%s/%s";
    private static final String ZBS_CBD_LUN_SNAPSHOT_NAME_FORMAT = "%s/%s@%s";
    private static final StorageCapabilities capabilities = new StorageCapabilities();

    static {
        VolumeSnapshotCapability scap = new VolumeSnapshotCapability();
        scap.setSupport(true);
        scap.setArrangementType(VolumeSnapshotCapability.VolumeSnapshotArrangementType.INDIVIDUAL);
        scap.setSupportCreateOnHypervisor(false);
        capabilities.setSnapshotCapability(scap);
        capabilities.setSupportCloneFromVolume(false);
        capabilities.setSupportStorageQos(false);
        capabilities.setSupportLiveExpandVolume(false);
        capabilities.setSupportedImageFormats(Collections.singletonList("raw"));
    }

    @Override
    public void activate(BaseVolumeInfo v, HostInventory h, boolean shareable, ReturnValueCompletion<ActiveVolumeTO> comp) {

    }

    @Override
    public void deactivate(String installPath, String protocol, HostInventory h, Completion comp) {

    }

    @Override
    public void blacklist(String installPath, String protocol, HostInventory h, Completion comp) {

    }

    @Override
    public String getActivePath(BaseVolumeInfo v, HostInventory h, boolean shareable) {
        return null;
    }

    @Override
    public BaseVolumeInfo getActiveVolumeInfo(String activePath, HostInventory h, boolean shareable) {
        return null;
    }

    @Override
    public List<ActiveVolumeClient> getActiveClients(String installPath, String protocol) {
        if (VolumeProtocol.CBD.toString().equals(protocol)) {
            return Collections.emptyList();
        } else {
            throw new OperationFailureException(operr("not supported protocol[%s] for active.", protocol));
        }
    }

    @Override
    public List<String> getActiveVolumesLocation(HostInventory h) {
        return null;
    }

    @Override
    public synchronized void activateHeartbeatVolume(HostInventory h, ReturnValueCompletion<HeartbeatVolumeTO> comp) {
        reloadDbInfo();

        CreateVolumeCmd cmd = new CreateVolumeCmd();
        cmd.logicalPoolName = config.getLogicalPoolName();
        cmd.volumeName = ZbsHelper.zbsHeartbeatVolumeName;
        cmd.skipIfExisting = true;

        httpCall(CREATE_VOLUME_PATH, cmd, CreateVolumeRsp.class, new ReturnValueCompletion<CreateVolumeRsp>(comp) {
            @Override
            public void success(CreateVolumeRsp returnValue) {
                CbdHeartbeatVolumeTO to = new CbdHeartbeatVolumeTO();
                to.setInstallPath(returnValue.installPath);
                to.setHeartbeatRequiredSpace(SizeUnit.GIGABYTE.toByte(1));
                comp.success(to);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                comp.fail(errorCode);
            }
        });
    }

    @Override
    public void deactivateHeartbeatVolume(HostInventory h, Completion comp) {

    }

    @Override
    public HeartbeatVolumeTO getHeartbeatVolumeActiveInfo(HostInventory h) {
        return null;
    }

    @Override
    public String getIdentity() {
        return ZbsConstants.IDENTITY;
    }

    @Override
    public void connect(String cfg, String url, ReturnValueCompletion<LinkedHashMap> completion) {
        changeStatus(PrimaryStorageStatus.Connecting);

        config = JSONObjectUtil.toObject(self.getConfig(), Config.class);

        if (config.getLogicalPoolName().contains("/")) {
            throw new CloudRuntimeException(String.format("invalid logical pool name[%s].", config.getLogicalPoolName()));
        }

        AddonInfo info = new AddonInfo();
        List<MdsInfo> mdsInfos = new ArrayList<>();
        for (String mdsUrl : config.getMdsUrls()) {
            MdsInfo mdsInfo = new MdsInfo();
            MdsUri uri = new MdsUri(mdsUrl);
            mdsInfo.setSshUsername(uri.getSshUsername());
            mdsInfo.setSshPassword(uri.getSshPassword());
            mdsInfo.setSshPort(uri.getSshPort());
            mdsInfo.setMdsAddr(uri.getHostname());
            mdsInfo.setMdsPort(uri.getMdsPort());
            mdsInfos.add(mdsInfo);
        }
        info.setMdsInfos(mdsInfos);

        final List<ZbsPrimaryStorageMdsBase> mds = CollectionUtils.transformToList(info.getMdsInfos(),
                ZbsPrimaryStorageMdsBase::new);

        class Connector {
            private final ErrorCodeList errorCodes = new ErrorCodeList();
            private final Iterator<ZbsPrimaryStorageMdsBase> it = mds.iterator();

            void connect(final FlowTrigger trigger) {
                if (!it.hasNext()) {
                    if (errorCodes.getCauses().size() == mds.size()) {
                        if (errorCodes.getCauses().isEmpty()) {
                            trigger.fail(operr("unable to connect to the zbs primary storage[uuid:%s]," +
                                    " failed to connect all zbs mds.", self.getUuid()));
                        } else {
                            trigger.fail(operr(errorCodes, "unable to connect to the zbs primary storage[uuid:%s]," +
                                            " failed to connect all zbs mds.",
                                    self.getUuid()));
                        }
                    } else {
                        ExternalPrimaryStorageVO vo = dbf.reload(self);
                        if (vo == null) {
                            trigger.fail(operr("zbs primary storage[uuid:%s] may have been deleted.", self.getUuid()));
                        } else {
                            self = vo;
                            trigger.next();
                        }
                    }
                    return;
                }

                final ZbsPrimaryStorageMdsBase base = it.next();
                base.connect(new Completion(trigger) {
                    @Override
                    public void success() {
                        connect(trigger);
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        errorCodes.getCauses().add(errorCode);
                        connect(trigger);
                    }
                });
            }
        }

        FlowChain chain = FlowChainBuilder.newShareFlowChain();
        chain.setName(String.format("connect-zbs-%s", self.getUuid()));
        chain.then(new ShareFlow() {
            @Override
            public void setup() {
                flow(new NoRollbackFlow() {
                    String __name__ = "connect-mds";

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        new Connector().connect(trigger);
                    }
                });

                flow(new NoRollbackFlow() {
                    String __name__ = "get-facts";

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        List<ErrorCode> errors = new ArrayList<>();
                        new While<>(mds).each((m, comp) -> {
                            GetFactsCmd cmd = new GetFactsCmd();
                            cmd.uuid = self.getUuid();
                            cmd.mdsAddr = m.getSelf().getMdsAddr();
                            m.httpCall(GET_FACTS_PATH, cmd, GetFactsRsp.class, new ReturnValueCompletion<GetFactsRsp>(comp) {
                                @Override
                                public void success(GetFactsRsp returnValue) {
                                    m.getSelf().setMdsVersion(returnValue.version);
                                    comp.done();
                                }

                                @Override
                                public void fail(ErrorCode errorCode) {
                                    errors.add(errorCode);
                                    comp.done();
                                }
                            });
                        }).run(new WhileDoneCompletion(trigger) {
                            @Override
                            public void done(ErrorCodeList errorCodeList) {
                                if (!errors.isEmpty()) {
                                    trigger.fail(errors.get(0));
                                    return;
                                }

                                trigger.next();
                            }
                        });
                    }
                });

                done(new FlowDoneHandler(completion) {
                    @Override
                    public void handle(Map data) {
                        changeStatus(PrimaryStorageStatus.Connected);
                        addonInfo = info;
                        completion.success(JSONObjectUtil.rehashObject(info, LinkedHashMap.class));
                    }
                });

                error(new FlowErrorHandler(completion) {
                    @Override
                    public void handle(ErrorCode errCode, Map data) {
                        changeStatus(PrimaryStorageStatus.Disconnected);
                        completion.fail(errCode);
                    }
                });
            }
        }).start();
    }

    @Override
    public void reportCapacity(ReturnValueCompletion<StorageCapacity> comp) {
        reloadDbInfo();

        GetCapacityCmd cmd = new GetCapacityCmd();
        cmd.setLogicalPoolName(config.getLogicalPoolName());

        httpCall(GET_CAPACITY_PATH, cmd, GetCapacityRsp.class, new ReturnValueCompletion<GetCapacityRsp>(comp) {
            @Override
            public void success(GetCapacityRsp returnValue) {
                StorageCapacity cap = new StorageCapacity();
                long total = returnValue.capacity;
                long avail = total != 0 ? total - returnValue.storedSize : 0;
                cap.setHealthy(StorageHealthy.Ok);
                cap.setTotalCapacity(total);
                cap.setAvailableCapacity(avail);
                comp.success(cap);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                comp.fail(errorCode);
            }
        });
    }

    @Override
    public void reportHealthy(ReturnValueCompletion<StorageHealthy> comp) {

    }

    @Override
    public void reportNodeHealthy(HostInventory host, ReturnValueCompletion<NodeHealthy> comp) {
        NodeHealthy healthy = new NodeHealthy();

        Arrays.asList(VolumeProtocol.CBD).forEach(it -> {
            //TODO: CHECK_HOST_STORAGE_CONNECTION_PATH call
            healthy.setHealthy(it, StorageHealthy.Ok);
        });

        comp.success(healthy);
    }

    @Override
    public StorageCapabilities reportCapabilities() {
        return capabilities;
    }

    @Override
    public String allocateSpace(AllocateSpaceSpec aspec) {
        reloadDbInfo();

        return buildVolumePath("", config.getLogicalPoolName(), "");
    }

    @Override
    public void createVolume(CreateVolumeSpec v, ReturnValueCompletion<VolumeStats> comp) {
        reloadDbInfo();

        CreateVolumeCmd cmd = new CreateVolumeCmd();
        cmd.logicalPoolName = config.getLogicalPoolName();
        cmd.volumeName = v.getName();
        cmd.skipIfExisting = true;

        httpCall(CREATE_VOLUME_PATH, cmd, CreateVolumeRsp.class, new ReturnValueCompletion<CreateVolumeRsp>(comp) {
            @Override
            public void success(CreateVolumeRsp returnValue) {
                VolumeStats stats = new VolumeStats();
                stats.setInstallPath(returnValue.installPath);
                stats.setFormat(VolumeConstant.VOLUME_FORMAT_RAW);
                stats.setSize(returnValue.getSize());
                stats.setActualSize(returnValue.getSize());
                comp.success(stats);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                comp.fail(errorCode);
            }
        });
    }

    @Override
    public void deleteVolume(String installPath, Completion comp) {
        doDeleteVolume(installPath, true, comp);
    }

    @Override
    public void deleteVolumeAndSnapshot(String installPath, Completion comp) {

    }

    @Override
    public void trashVolume(String installPath, Completion comp) {
        doDeleteVolume(installPath, false, comp);
    }

    @Override
    public void cloneVolume(String srcInstallPath, CreateVolumeSpec dst, ReturnValueCompletion<VolumeStats> comp) {
        doCloneVolume(srcInstallPath, dst, comp);
    }

    @Override
    public void copyVolume(String srcInstallPath, CreateVolumeSpec dst, ReturnValueCompletion<VolumeStats> comp) {

    }

    @Override
    public void flattenVolume(String installPath, ReturnValueCompletion<VolumeStats> comp) {

    }

    @Override
    public void stats(String installPath, ReturnValueCompletion<VolumeStats> comp) {
        QueryVolumeCmd cmd = new QueryVolumeCmd();
        cmd.installPath = installPath;

        httpCall(QUERY_VOLUME_PATH, cmd, QueryVolumeRsp.class, new ReturnValueCompletion<QueryVolumeRsp>(comp) {
            @Override
            public void success(QueryVolumeRsp returnValue) {
                VolumeStats stats = new VolumeStats();
                stats.setInstallPath(installPath);
                stats.setSize(returnValue.getSize());
                stats.setActualSize(returnValue.getSize());
                stats.setFormat(VolumeConstant.VOLUME_FORMAT_RAW);
                comp.success(stats);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                comp.fail(errorCode);
            }
        });
    }

    @Override
    public void batchStats(Collection<String> installPath, ReturnValueCompletion<List<VolumeStats>> comp) {

    }

    @Override
    public void expandVolume(String installPath, long size, ReturnValueCompletion<VolumeStats> comp) {

    }

    @Override
    public void setVolumeQos(BaseVolumeInfo v, Completion comp) {

    }

    @Override
    public void export(ExportSpec espec, VolumeProtocol protocol, ReturnValueCompletion<RemoteTarget> comp) {
        if (protocol != VolumeProtocol.CBD) {
            throw new RuntimeException("unsupported target " + protocol.name());
        }

        CbdToNbdCmd cmd = new CbdToNbdCmd();
        cmd.installPath = espec.getInstallPath();

        httpCall(CBD_TO_NBD_PATH, cmd, CbdToNbdRsp.class, new ReturnValueCompletion<CbdToNbdRsp>(comp) {
            @Override
            public void success(CbdToNbdRsp returnValue) {
                CbdRemoteTarget target = new CbdRemoteTarget();
                target.setResourceURI(returnValue.getCbdToNbdPath());
                comp.success(target);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                comp.fail(errorCode);
            }
        });
    }

    @Override
    public void unexport(ExportSpec espec, VolumeProtocol protocol, Completion comp) {
        if (protocol == VolumeProtocol.CBD) {
            unexportCbd(espec.getInstallPath());
        } else {
            comp.fail(operr("unsupported protocol %s", protocol.name()));
            return;
        }
        comp.success();
    }

    @Override
    public void createSnapshot(CreateVolumeSnapshotSpec spec, ReturnValueCompletion<VolumeSnapshotStats> comp) {
        doCreateSnapshot(spec, comp);
    }

    @Override
    public void deleteSnapshot(String installPath, Completion comp) {

    }

    @Override
    public void revertVolumeSnapshot(String snapshotInstallPath, ReturnValueCompletion<VolumeStats> comp) {

    }

    @Override
    public void validateConfig(String config) {

    }

    @Override
    public void setTrashExpireTime(int timeInSeconds, Completion completion) {

    }

    private void doCloneVolume(String srcInstallPath, CreateVolumeSpec dst, ReturnValueCompletion<VolumeStats> comp) {
        final String srcPath = String.format(
                ZBS_CBD_LUN_SNAPSHOT_NAME_FORMAT,
                getLogicalPoolNameFromPath(srcInstallPath),
                getLunIdFromPath(srcInstallPath),
                getSnapIdFromPath(srcInstallPath)
        );
        final String destPath = String.format(
                ZBS_CBD_LUN_PATH_FORMAT,
                getPhysicalPoolNameFromPath(srcInstallPath),
                getLogicalPoolNameFromPath(srcInstallPath),
                dst.getName()
        );

        CloneVolumeCmd cmd = new CloneVolumeCmd();
        cmd.srcPath = srcPath;
        cmd.destPath = destPath;

        httpCall(CLONE_VOLUME_PATH, cmd, CloneVolumeRsp.class, new ReturnValueCompletion<CloneVolumeRsp>(comp) {
            @Override
            public void success(CloneVolumeRsp returnValue) {
                VolumeStats stats = new VolumeStats();
                stats.setInstallPath(returnValue.installPath);
                stats.setFormat(VolumeConstant.VOLUME_FORMAT_RAW);
                stats.setSize(returnValue.getSize());
                stats.setActualSize(returnValue.getSize());
                comp.success(stats);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                comp.fail(errorCode);
            }
        });
    }

    public void doCreateSnapshot(CreateVolumeSnapshotSpec spec, ReturnValueCompletion<VolumeSnapshotStats> comp) {
        final String snapPath = String.format(
                ZBS_CBD_LUN_SNAPSHOT_NAME_FORMAT,
                getLogicalPoolNameFromPath(spec.getVolumeInstallPath()),
                getLunIdFromPath(spec.getVolumeInstallPath()),
                spec.getName()
        );

        CreateSnapshotCmd cmd = new CreateSnapshotCmd();
        cmd.skipOnExisting = true;
        cmd.snapPath = snapPath;

        httpCall(CREATE_SNAPSHOT_PATH, cmd, CreateSnapshotRsp.class, new ReturnValueCompletion<CreateSnapshotRsp>(comp) {
            @Override
            public void success(CreateSnapshotRsp returnValue) {
                VolumeSnapshotStats stats = new VolumeSnapshotStats();
                stats.setInstallPath(returnValue.getInstallPath());
                stats.setActualSize(returnValue.getSize());
                comp.success(stats);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                comp.fail(errorCode);
            }
        });
    }

    private synchronized void unexportCbd(String source) {

    }

    public String getLogicalPoolNameFromPath(String url) {
        return url.split("/")[1];
    }

    public String getPhysicalPoolNameFromPath(String url) {
        return url.split("/")[0].split(":")[1];
    }

    public String getLunIdFromPath(String url) {
        return url.split("/")[2].split("@")[0];
    }

    public String getSnapIdFromPath(String url) {
        return url.split("/")[2].split("@")[1];
    }

    public void doDeleteVolume(String installPath, Boolean force, Completion comp) {
        DeleteVolumeCmd cmd = new DeleteVolumeCmd();
        cmd.installPath = installPath;
        cmd.force = force;

        httpCall(DELETE_VOLUME_PATH, cmd, DeleteVolumeRsp.class, new ReturnValueCompletion<DeleteVolumeRsp>(comp) {
            @Override
            public void success(DeleteVolumeRsp returnValue) {
                comp.success();
            }

            @Override
            public void fail(ErrorCode errorCode) {
                comp.fail(errorCode);
            }
        });
    }

    public static String buildVolumePath(String physicalPoolName, String logicalPoolName, String volId) {
        String base = volId.replace("-", "");
        return String.format(ZBS_CBD_LUN_PATH_FORMAT, physicalPoolName, logicalPoolName, base);
    }

    private void reloadDbInfo() {
        self = dbf.reload(self);
        addonInfo = StringUtils.isEmpty(self.getAddonInfo()) ? new AddonInfo() : JSONObjectUtil.toObject(self.getAddonInfo(), AddonInfo.class);
        config = StringUtils.isEmpty(self.getConfig()) ? new Config() : JSONObjectUtil.toObject(self.getConfig(), Config.class);
    }

    public void changeStatus(PrimaryStorageStatus status) {
        String uuid = self.getUuid();
        self = dbf.reload(self);
        if (self == null) {
            throw new OperationFailureException(operr(
                    "cannot update status of the zbs primary storage[uuid:%s], it has been deleted." +
                            "This error can be ignored", uuid
            ));
        }

        if (self.getStatus() == status) {
            return;
        }

        PrimaryStorageStatus oldStatus = self.getStatus();
        self.setStatus(status);
        self = dbf.updateAndRefresh(self);
        logger.debug(String.format("zbs primary storage[uuid:%s] changed status from %s to %s", self.getUuid(), oldStatus, status));
    }

    protected <T extends AgentResponse> void httpCall(final String path, final AgentCommand cmd, final Class<T> retClass, final ReturnValueCompletion<T> callback) {
        httpCall(path, cmd, retClass, callback, null, 0);
    }

    protected <T extends AgentResponse> void httpCall(final String path, final AgentCommand cmd, final Class<T> retClass, final ReturnValueCompletion<T> callback, TimeUnit unit, long timeout) {
        new HttpCaller<>(path, cmd, retClass, callback, unit, timeout).call();
    }

    public class HttpCaller<T extends AgentResponse> {
        private Iterator<ZbsPrimaryStorageMdsBase> it;
        private final List<MdsInfo> mdsInfos;
        private final ErrorCodeList errorCodes = new ErrorCodeList();

        private final String path;
        private final AgentCommand cmd;
        private final Class<T> retClass;
        private final ReturnValueCompletion<T> callback;
        private final TimeUnit unit;
        private final long timeout;

        private boolean tryNext = false;

        public HttpCaller(String path, AgentCommand cmd, Class<T> retClass, ReturnValueCompletion<T> callback, TimeUnit unit, long timeout) {
            this.path = path;
            this.cmd = cmd;
            this.retClass = retClass;
            this.callback = callback;
            this.unit = unit;
            this.timeout = timeout;
            this.mdsInfos = prepareMds();
        }

        public void call() {
            prepareMdsIterator();
            prepareCmd();
            doCall();
        }

        private void prepareCmd() {
            cmd.setUuid(self.getUuid());
        }

        private List<MdsInfo> prepareMds() {
            final List<MdsInfo> mds = new ArrayList<>(addonInfo.getMdsInfos());

            Collections.shuffle(mds);

            mds.removeIf(it -> it.getMdsStatus() != MdsStatus.Connected);
            if (mds.isEmpty()) {
                throw new OperationFailureException(operr(
                        "all zbs mds of primary storage[uuid:%s] are not in Connected state", self.getUuid())
                );
            }

            return mds;
        }

        private void prepareMdsIterator() {
            it = mdsInfos.stream().map(ZbsPrimaryStorageMdsBase::new).collect(Collectors.toList()).iterator();
        }

        private void doCall() {
            if (!it.hasNext()) {
                callback.fail(operr(errorCodes, "all mds cannot execute http call[%s]", path));
                return;
            }

            ZbsPrimaryStorageMdsBase base = it.next();
            cmd.mdsAddr = base.getSelf().getMdsAddr();

            ReturnValueCompletion<T> completion = new ReturnValueCompletion<T>(callback) {
                @Override
                public void success(T ret) {
                    callback.success(ret);
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    if (!errorCode.isError(SysErrors.OPERATION_ERROR) && !errorCode.isError(SysErrors.TIMEOUT)) {
                        logger.warn(String.format("mds[addr:%s] failed to execute http call[%s], error is: %s",
                                base.getSelf().getMdsAddr(), path, JSONObjectUtil.toJsonString(errorCode)));
                        errorCodes.getCauses().add(errorCode);
                        doCall();
                        return;
                    }

                    if (tryNext) {
                        doCall();
                    } else {
                        callback.fail(errorCode);
                    }
                }
            };

            if (unit == null) {
                base.httpCall(path, cmd, retClass, completion);
            } else {
                base.httpCall(path, cmd, retClass, completion, unit, timeout);
            }
        }
    }

    public static class QueryVolumeRsp extends AgentResponse {
        public Long size;

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }
    }

    public static class CbdToNbdRsp extends AgentResponse {
        public String cbdToNbdPath;

        public String getCbdToNbdPath() {
            return cbdToNbdPath;
        }

        public void setCbdToNbdPath(String cbdToNbdPath) {
            this.cbdToNbdPath = cbdToNbdPath;
        }
    }

    public static class CloneVolumeRsp extends AgentResponse {
        public Long size;
        public String installPath;

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }

        public String getInstallPath() {
            return installPath;
        }

        public void setInstallPath(String installPath) {
            this.installPath = installPath;
        }
    }

    public static class CreateSnapshotRsp extends AgentResponse {
        String installPath;
        Long size;

        public String getInstallPath() {
            return installPath;
        }

        public void setInstallPath(String installPath) {
            this.installPath = installPath;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }
    }

    public static class DeleteVolumeRsp extends AgentResponse {
    }

    public static class CreateVolumeRsp extends AgentResponse {
        public String installPath;
        public Long size;
        public Long actualSize;

        public String getInstallPath() {
            return installPath;
        }

        public void setInstallPath(String installPath) {
            this.installPath = installPath;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }

        public Long getActualSize() {
            return actualSize;
        }

        public void setActualSize(Long actualSize) {
            this.actualSize = actualSize;
        }
    }

    public static class GetPoolInfoRsp extends AgentResponse {
        String physicalPoolName;
        String logicalPoolName;

        public String getPhysicalPoolName() {
            return physicalPoolName;
        }

        public void setPhysicalPoolName(String physicalPoolName) {
            this.physicalPoolName = physicalPoolName;
        }

        public String getLogicalPoolName() {
            return logicalPoolName;
        }

        public void setLogicalPoolName(String logicalPoolName) {
            this.logicalPoolName = logicalPoolName;
        }
    }

    public static class GetCapacityRsp extends AgentResponse {
        public long capacity;
        public long storedSize;
    }

    public static class GetFactsRsp extends AgentResponse {
        public String version;
    }

    public static class AgentResponse extends ZbsMdsBase.AgentResponse {
    }

    public static class QueryVolumeCmd extends AgentCommand {
        public String installPath;

        public String getInstallPath() {
            return installPath;
        }

        public void setInstallPath(String installPath) {
            this.installPath = installPath;
        }
    }

    public static class CbdToNbdCmd extends AgentCommand {
        public String installPath;

        public String getInstallPath() {
            return installPath;
        }

        public void setInstallPath(String installPath) {
            this.installPath = installPath;
        }
    }

    public static class CloneVolumeCmd extends AgentCommand {
        String srcPath;
        String destPath;

        public String getSrcPath() {
            return srcPath;
        }

        public void setSrcPath(String srcPath) {
            this.srcPath = srcPath;
        }

        public String getDestPath() {
            return destPath;
        }

        public void setDestPath(String destPath) {
            this.destPath = destPath;
        }
    }

    public static class CreateSnapshotCmd extends AgentCommand {
        boolean skipOnExisting;
        String snapPath;

        public boolean isSkipOnExisting() {
            return skipOnExisting;
        }

        public void setSkipOnExisting(boolean skipOnExisting) {
            this.skipOnExisting = skipOnExisting;
        }

        public String getSnapPath() {
            return snapPath;
        }

        public void setSnapPath(String snapPath) {
            this.snapPath = snapPath;
        }
    }

    public static class DeleteVolumeCmd extends AgentCommand {
        String installPath;
        Boolean force;
    }

    public static class CreateVolumeCmd extends AgentCommand {
        String logicalPoolName;
        String volumeName;
        long size = 1;
        boolean skipIfExisting;

        public String getLogicalPoolName() {
            return logicalPoolName;
        }

        public void setLogicalPoolName(String logicalPoolName) {
            this.logicalPoolName = logicalPoolName;
        }

        public String getVolumeName() {
            return volumeName;
        }

        public void setVolumeName(String volumeName) {
            this.volumeName = volumeName;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public boolean isSkipIfExisting() {
            return skipIfExisting;
        }

        public void setSkipIfExisting(boolean skipIfExisting) {
            this.skipIfExisting = skipIfExisting;
        }
    }

    public static class GetCapacityCmd extends AgentCommand {
        String logicalPoolName;

        public String getLogicalPoolName() {
            return logicalPoolName;
        }

        public void setLogicalPoolName(String logicalPoolName) {
            this.logicalPoolName = logicalPoolName;
        }
    }

    public static class GetFactsCmd extends AgentCommand {
    }

    public static class AgentCommand {
        String uuid;
        String mdsAddr;

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public String getMdsAddr() {
            return mdsAddr;
        }

        public void setMdsAddr(String mdsAddr) {
            this.mdsAddr = mdsAddr;
        }
    }

    public ZbsStorageController(ExternalPrimaryStorageVO self) {
        this.self = self;
    }
}

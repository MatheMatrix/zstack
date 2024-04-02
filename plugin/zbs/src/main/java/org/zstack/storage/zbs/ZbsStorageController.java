package org.zstack.storage.zbs;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.core.workflow.ShareFlow;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.host.HostInventory;
import org.zstack.header.rest.RESTFacade;
import org.zstack.header.storage.addon.NodeHealthy;
import org.zstack.header.storage.addon.RemoteTarget;
import org.zstack.header.storage.addon.StorageCapacity;
import org.zstack.header.storage.addon.StorageHealthy;
import org.zstack.header.storage.addon.primary.*;
import org.zstack.header.storage.primary.PrimaryStorageStatus;
import org.zstack.header.storage.snapshot.VolumeSnapshotStats;
import org.zstack.header.volume.VolumeProtocol;
import org.zstack.header.volume.VolumeStats;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.*;

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
    private ZbsAddonInfo addonInfo;
    private ZbsConfig zbsConfig;

    public ZbsStorageController(ExternalPrimaryStorageVO self) {
        this.self = self;
    }

    public ExternalPrimaryStorageVO getSelf() {
        return self;
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
        return null;
    }

    @Override
    public List<String> getActiveVolumesLocation(HostInventory h) {
        return null;
    }

    @Override
    public void activateHeartbeatVolume(HostInventory h, ReturnValueCompletion<HeartbeatVolumeTO> comp) {

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

    @Override
    public void connect(String config, String url, ReturnValueCompletion<LinkedHashMap> completion) {
        changeStatus(PrimaryStorageStatus.Connecting);

        ZbsAddonInfo info = new ZbsAddonInfo();
        zbsConfig = JSONObjectUtil.toObject(self.getConfig(), ZbsConfig.class);
        for (String mdsUrl : zbsConfig.getMdsUrls()) {
            ZbsAddonInfo.Mds mds = new ZbsAddonInfo.Mds();
            MdsUri uri = new MdsUri(mdsUrl);
            mds.setHostname(uri.getHostname());
            mds.setSshUsername(uri.getSshUsername());
            mds.setSshPassword(uri.getSshPassword());
            mds.setMdsAddr(uri.getHostname());
            mds.setMdsPort(uri.getMdsPort());
            info.getMds().add(mds);
        }

        final List<ZbsPrimaryStorageMdsBase> mds = CollectionUtils.transformToList(info.getMds(),
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
        chain.setName(String.format("connect-zbs-primary-storage-%s", self.getUuid()));
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

    }

    @Override
    public void reportHealthy(ReturnValueCompletion<StorageHealthy> comp) {

    }

    @Override
    public void reportNodeHealthy(HostInventory host, ReturnValueCompletion<NodeHealthy> comp) {

    }

    @Override
    public StorageCapabilities reportCapabilities() {
        return null;
    }

    @Override
    public String allocateSpace(AllocateSpaceSpec aspec) {
        return null;
    }

    @Override
    public void createVolume(CreateVolumeSpec v, ReturnValueCompletion<VolumeStats> comp) {

    }

    @Override
    public void deleteVolume(String installPath, Completion comp) {

    }

    @Override
    public void deleteVolumeAndSnapshot(String installPath, Completion comp) {

    }

    @Override
    public void trashVolume(String installPath, Completion comp) {

    }

    @Override
    public void cloneVolume(String srcInstallPath, CreateVolumeSpec dst, ReturnValueCompletion<VolumeStats> comp) {

    }

    @Override
    public void copyVolume(String srcInstallPath, CreateVolumeSpec dst, ReturnValueCompletion<VolumeStats> comp) {

    }

    @Override
    public void flattenVolume(String installPath, ReturnValueCompletion<VolumeStats> comp) {

    }

    @Override
    public void stats(String installPath, ReturnValueCompletion<VolumeStats> comp) {

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

    }

    @Override
    public void unexport(ExportSpec espec, VolumeProtocol protocol, Completion comp) {

    }

    @Override
    public void createSnapshot(CreateVolumeSnapshotSpec spec, ReturnValueCompletion<VolumeSnapshotStats> comp) {

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
}

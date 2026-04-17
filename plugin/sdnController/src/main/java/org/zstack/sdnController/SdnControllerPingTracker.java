package org.zstack.sdnController;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.ResourceDestinationMaker;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.config.GlobalConfig;
import org.zstack.core.config.GlobalConfigUpdateExtensionPoint;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.thread.AsyncThread;
import org.zstack.core.tracker.PingTracker;
import org.zstack.header.core.Completion;
import org.zstack.header.managementnode.ManagementNodeChangeListener;
import org.zstack.header.managementnode.ManagementNodeInventory;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.message.MessageReply;
import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.network.l2.SdnControllerDeleteExtensionPoint;
import org.zstack.header.network.sdncontroller.SdnControllerConstant;
import org.zstack.header.network.sdncontroller.SdnControllerStatus;
import org.zstack.header.network.sdncontroller.SdnControllerVO;
import org.zstack.header.network.sdncontroller.SdnControllerVO_;
import org.zstack.sdnController.header.*;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.function.Function;
import org.zstack.utils.logging.CLogger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by shixin on 06/26/2025.
 */
public class SdnControllerPingTracker extends PingTracker implements
        ManagementNodeChangeListener, ManagementNodeReadyExtensionPoint, SdnControllerDeleteExtensionPoint {
    private final static CLogger logger = Utils.getLogger(SdnControllerPingTracker.class);

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private ResourceDestinationMaker destinationMaker;
    @Autowired
    protected PluginRegistry pluginRgty;
    @Autowired
    protected SdnControllerManager sdnMgr;
    @Autowired
    private DirtySyncTracker dirtySyncTracker;

    private final Map<String, AtomicInteger> consecutivePingSuccessCount = new ConcurrentHashMap<>();

    public String getResourceName() {
        return "sdn controller";
    }

    // sdn controller will only ping when it's connected or disconnected,
    // if ping failed, it will send ReconnectSdnControllerMsg,
    @Override
    public NeedReplyMessage getPingMessage(String resUuid) {
        SdnControllerVO vo = dbf.findByUuid(resUuid, SdnControllerVO.class);
        if (vo.getStatus() == SdnControllerStatus.Connecting) {
            return null;
        }

        SdnControllerPingMsg msg = new SdnControllerPingMsg();
        msg.setSdnControllerUuid(resUuid);
        bus.makeTargetServiceIdByResourceUuid(msg, SdnControllerConstant.SERVICE_ID, resUuid);
        return msg;
    }

    @Override
    public int getPingInterval() {
        return SdnControllerGlobalConfig.PING_INTERVAL.value(Integer.class);
    }

    @Override
    public int getParallelismDegree() {
        return SdnControllerGlobalConfig.PING_PARALLELISM_DEGREE.value(Integer.class);
    }

    @Override
    public void handleReply(final String resourceUuid, MessageReply reply) {
        SdnControllerVO vo = dbf.findByUuid(resourceUuid, SdnControllerVO.class);
        if (vo == null) {
            logger.warn(String.format("SDN controller[uuid:%s] has been deleted, skip ping handling", resourceUuid));
            return;
        }

        if (!reply.isSuccess()) {
            logger.warn(String.format("[SDN Ping Tracker]: unable to ping the sdn controller[uuid: %s], %s", resourceUuid, reply.getError()));
            consecutivePingSuccessCount.remove(resourceUuid);
            new SdnControllerBase(vo).changeSdnControllerStatus(SdnControllerStatus.Disconnected);
            return;
        }

        SdnControllerStatus oldStatus = vo.getStatus();
        if (oldStatus == SdnControllerStatus.Disconnected) {
            // when reconnect successfully, it will fire event: SdnControllerStatus.Connected
            consecutivePingSuccessCount.remove(resourceUuid);
            ReconnectSdnControllerMsg msg = new ReconnectSdnControllerMsg();
            msg.setControllerUuid(resourceUuid);
            bus.makeTargetServiceIdByResourceUuid(msg, SdnControllerConstant.SERVICE_ID, resourceUuid);
            bus.send(msg);
            return;
        }

        // Ping success on Connected controller: count consecutive successes and trigger sync if needed
        if (vo.getStatus() == SdnControllerStatus.Connected) {
            int count = consecutivePingSuccessCount
                    .computeIfAbsent(resourceUuid, k -> new AtomicInteger(0))
                    .incrementAndGet();

            SdnControllerFactory factory = sdnMgr.getSdnControllerFactory(vo.getVendorType());
            if (factory.isSyncEnabled() && dirtySyncTracker.needsSync(resourceUuid)) {
                int threshold = factory.getSyncPingSuccessThreshold();
                if (count >= threshold) {
                    consecutivePingSuccessCount.remove(resourceUuid);
                    dirtySyncTracker.clearNeedSync(resourceUuid);
                    triggerSync(resourceUuid);
                }
            }
        }
    }

    private void triggerSync(String controllerUuid) {
        logger.info(String.format("[SDN Ping Tracker]: triggering sync for controller[uuid:%s] after consecutive ping successes", controllerUuid));
        SyncSdnControllerDataMsg msg = new SyncSdnControllerDataMsg();
        msg.setControllerUuid(controllerUuid);
        bus.makeTargetServiceIdByResourceUuid(msg, SdnControllerConstant.SERVICE_ID, controllerUuid);
        bus.send(msg, new CloudBusCallBack(null) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    logger.warn(String.format("[SDN Ping Tracker]: sync failed for controller[uuid:%s], re-marking needsSync: %s",
                            controllerUuid, reply.getError()));
                    dirtySyncTracker.markNeedSync(controllerUuid);
                }
            }
        });
    }

    private void trackOurs() {
        List<String> sdnControllerUuids = Q.New(SdnControllerVO.class)
                .select(SdnControllerVO_.uuid).listValues();
        List<String> toTrack = CollectionUtils.transformToList(sdnControllerUuids, new Function<String, String>() {
            @Override
            public String call(String arg) {
                return destinationMaker.isManagedByUs(arg) ? arg : null;
            }
        });

        untrackAll();
        track(toTrack);
    }

    @Override
    public void nodeJoin(ManagementNodeInventory inv) {
        trackOurs();
    }

    @Override
    public void nodeLeft(ManagementNodeInventory inv) {
        trackOurs();
    }

    @Override
    public void iAmDead(ManagementNodeInventory inv) {

    }

    @Override
    public void iJoin(ManagementNodeInventory inv) {
    }

    @Override
    protected void startHook() {
        SdnControllerGlobalConfig.PING_INTERVAL.installUpdateExtension(new GlobalConfigUpdateExtensionPoint() {
            @Override
            public void updateGlobalConfig(GlobalConfig oldConfig, GlobalConfig newConfig) {
                pingIntervalChanged();
            }
        });
    }

    @Override
    @AsyncThread
    public void managementNodeReady() {
        trackOurs();
    }

    @Override
    protected void trackHook(String resourceUuid) {
        super.trackHook(resourceUuid);
    }

    @Override
    public void deleteNetworkServiceOfSdnController(String sdnControllerUuid, Completion completion) {
        consecutivePingSuccessCount.remove(sdnControllerUuid);
        super.untrack(sdnControllerUuid);
        completion.success();
    }
}

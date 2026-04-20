package org.zstack.compute.host;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.CoreGlobalProperty;
import org.zstack.core.cloudbus.*;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQLBatch;
import org.zstack.core.thread.AsyncThread;
import org.zstack.core.thread.AsyncTimer;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.Component;
import org.zstack.header.core.Completion;
import org.zstack.header.core.NoErrorCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.host.*;
import org.zstack.header.managementnode.ManagementNodeChangeListener;
import org.zstack.header.managementnode.ManagementNodeInventory;
import org.zstack.header.managementnode.ManagementNodeReadyExtensionPoint;
import org.zstack.header.message.MessageReply;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.Tuple;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class HostTrackImpl implements HostTracker, ManagementNodeChangeListener, Component, ManagementNodeReadyExtensionPoint {
    private final static CLogger logger = Utils.getLogger(HostTrackImpl.class);

    private Map<String, Tracker> trackers = new ConcurrentHashMap<>();
    private static boolean alwaysStartRightNow = false;
    private static final Cache<String, Long> skippedPingHostDeadline = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();

    // EMA adaptive timeout: tracks per-host exponential moving average of ping response times (in seconds)
    private static final double EMA_ALPHA = 0.2;
    private static final double EMA_SAFETY_FACTOR = 3.0;
    private static final ConcurrentHashMap<String, Double> pingResponseEma = new ConcurrentHashMap<>();

    /**
     * Returns the ping timeout for the given host (in seconds).
     * When adaptive timeout is enabled (host.ping.adaptiveTimeout.enable=true),
     * uses EMA of observed response times * safety factor, floored by the configured value.
     * When disabled, returns the configured ping.timeout directly.
     */
    public static long getAdaptiveTimeout(String hostUuid) {
        long configured = HostGlobalConfig.PING_HOST_TIMEOUT.value(Long.class);
        if (!HostGlobalConfig.PING_ADAPTIVE_TIMEOUT_ENABLED.value(Boolean.class)) {
            return configured;
        }
        Double ema = pingResponseEma.get(hostUuid);
        if (ema == null) {
            return configured;
        }
        long adaptive = (long) Math.ceil(ema * EMA_SAFETY_FACTOR);
        return Math.max(configured, adaptive);
    }

    /**
     * Updates the per-host EMA of ping response times using exponential moving average.
     * New EMA = alpha * sample + (1 - alpha) * oldEMA.
     */
    private static void updatePingResponseEma(String hostUuid, double responseTimeSec) {
        pingResponseEma.merge(hostUuid, responseTimeSec,
                (oldEma, sample) -> EMA_ALPHA * sample + (1 - EMA_ALPHA) * oldEma);
    }

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private ResourceDestinationMaker destMaker;
    @Autowired
    private CloudBus bus;
    @Autowired
    private ThreadFacade thdf;
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    protected EventFacade evtf;

    private static Map<String, HostReconnectTaskFactory> hostReconnectTaskFactories = new HashMap<>();

    private Map<String, AtomicInteger> hostDisconnectCount = new ConcurrentHashMap<>();

    @Override
    public void managementNodeReady() {
        reScanHost();
    }

    enum ReconnectDecision {
        DoNothing,
        ReconnectNow,
        SubmitReconnectTask,
        StopPing
    }

    private void reconnectNow(String uuid, Completion completion) {
        ReconnectHostMsg msg = new ReconnectHostMsg();
        msg.setHostUuid(uuid);
        msg.setSkipIfHostConnected(true);
        bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, uuid);
        bus.send(msg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (reply.isSuccess()) {
                    completion.success();
                } else {
                    completion.fail(reply.getError());
                }
            }
        });
    }

    private class Tracker extends AsyncTimer {
        private final CLogger logger = Utils.getLogger(HostTrackImpl.class);

        private String uuid;
        private String hypervisorType;
        private HostReconnectTask reconnectTask;

        Tracker(String uuid) {
            super(TimeUnit.SECONDS, HostGlobalConfig.PING_HOST_INTERVAL.value(Long.class));
            this.uuid = uuid;
            hypervisorType = Q.New(HostVO.class).select(HostVO_.hypervisorType)
                    .eq(HostVO_.uuid, uuid).findValue();
            if (hypervisorType == null) {
                throw new CloudRuntimeException(String.format("host[uuid:%s] is deleted, why you submit a tracker for it???", uuid));
            }


            __name__ = String.format("host-tracker-%s-hypervisor-%s", uuid, hypervisorType);
        }

        @Override
        protected void execute() {
            track();
        }

        private void track()  {
            Tuple t = Q.New(HostVO.class).select(HostVO_.state, HostVO_.status)
                    .eq(HostVO_.uuid, uuid).findTuple();

            if (t == null) {
                logger.debug(String.format("host[uuid:%s] seems to be deleted, stop tracking it", uuid));
                return;
            }

            HostState state = t.get(0, HostState.class);

            if (state == HostState.PreMaintenance || state == HostState.Maintenance) {
                logger.debug(String.format("host[uuid:%s] is in state of %s, not tracking it this time", uuid, state));
                continueToRunThisTimer();
                return;
            }

            if (skippedPingHostDeadline.getIfPresent(uuid) != null && System.currentTimeMillis() / 1000 <= skippedPingHostDeadline.getIfPresent(uuid)) {
                logger.debug(String.format("skip tracking host[uuid:%s] this time, deadline %s", uuid, skippedPingHostDeadline.getIfPresent(uuid)));
                continueToRunThisTimer();
                return;
            }

            PingHostMsg msg = new PingHostMsg();
            msg.setHostUuid(uuid);
            bus.makeLocalServiceId(msg, HostConstant.SERVICE_ID);
            final long pingStartMs = System.currentTimeMillis();
            bus.send(msg, new CloudBusCallBack(null) {
                @Override
                public void run(MessageReply reply) {
                    decideWhatToDoNext(makeReconnectDecision(reply));
                }

                private ReconnectDecision makeReconnectDecision(MessageReply reply) {
                    if (!reply.isSuccess()) {
                        logger.warn(String.format("[Host Tracker]: unable track host[uuid:%s], %s", uuid, reply.getError()));
                        return ReconnectDecision.DoNothing;
                    }

                    // update EMA with observed response time on successful ping
                    double responseTimeSec = (System.currentTimeMillis() - pingStartMs) / 1000.0;
                    updatePingResponseEma(uuid, responseTimeSec);
                    logger.debug(String.format("[Host Tracker]: host[uuid:%s] ping response time %.2fs, adaptive timeout %ds",
                            uuid, responseTimeSec, getAdaptiveTimeout(uuid)));

                    PingHostReply r = reply.castReply();
                    if (r.isNoReconnect()) {
                        return ReconnectDecision.DoNothing;
                    }

                    AtomicInteger disconnectCount = hostDisconnectCount.get(uuid);
                    int threshold = HostGlobalConfig.AUTO_RECONNECT_ON_ERROR_MAX_ATTEMPT_NUM.value(Integer.class);
                    if (threshold > 0 && disconnectCount != null && disconnectCount.get() >= threshold) {
                        logger.warn(String.format("stop pinging host[uuid:%s, hypervisorType:%s] because it fail to reconnect too many times", uuid, hypervisorType));
                        return ReconnectDecision.StopPing;
                    }

                    boolean autoReconnect = HostGlobalConfig.AUTO_RECONNECT_ON_ERROR.value(Boolean.class);
                    if (!r.isConnected() && autoReconnect) {
                        return ReconnectDecision.SubmitReconnectTask;
                    }

                    // host can be successfully pinged
                    if (r.getCurrentHostStatus().equals(HostStatus.Disconnected.toString())) {
                        if (autoReconnect) {
                            return ReconnectDecision.ReconnectNow;
                        } else {
                            logger.warn(String.format("stop pinging host[uuid:%s, hypervisorType:%s] because it's disconnected and connection.autoReconnectOnError is false", uuid, hypervisorType));
                            return ReconnectDecision.StopPing;
                        }
                    }

                    // host can be pinged and the current status is Connected
                    return ReconnectDecision.DoNothing;
                }
            });
        }

        private void decideWhatToDoNext(ReconnectDecision decision) {
            if (decision == ReconnectDecision.DoNothing) {
                continueToRunThisTimer();
            } else if (decision == ReconnectDecision.ReconnectNow) {
                reconnectNow(uuid, new Completion(new NoErrorCompletion() {
                    @Override
                    public void done() {
                        continueToRunThisTimer();
                    }
                }) {
                    @Override
                    public void success() {
                        continueToRunThisTimer();
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        submitReconnectTask(errorCode);
                    }
                });
            } else if (decision == ReconnectDecision.StopPing) {
                cancel();
            } else if (decision == ReconnectDecision.SubmitReconnectTask) {
                submitReconnectTask(null);
            } else {
                throw new CloudRuntimeException("should not be here");
            }
        }

        private void submitReconnectTask(ErrorCode lastConnectError) {
            if (isCanceled()) {
                return;
            }

            if (reconnectTask != null) {
                reconnectTask.cancel();
            }

            reconnectTask = createTask(lastConnectError);
            reconnectTask.start();
        }

        HostReconnectTask createTask(ErrorCode lastConnectError) {
            final HostReconnectTaskFactory factory = getHostReconnectTaskFactory(hypervisorType);
            NoErrorCompletion completion = new NoErrorCompletion() {
                @Override
                public void done() {
                    continueToRunThisTimer();
                }
            };

            return lastConnectError == null ?
                    factory.createTask(uuid, completion) :
                    factory.createTaskWithLastConnectError(uuid, lastConnectError, completion);
        }

        @Override
        public void cancel() {
            if (reconnectTask != null) {
                reconnectTask.cancel();
            }

            super.cancel();

            trackers.remove(uuid);
        }
    }


    public void trackHost(String hostUuid) {
        if (!destMaker.isManagedByUs(hostUuid)) {
            logger.debug(String.format("skip tracking host[uuid:%s], not managed by us", hostUuid));
            return;
        }

        Tracker t = trackers.get(hostUuid);
        if (t != null) {
            t.cancel();
        }

        t = new Tracker(hostUuid);
        trackers.put(hostUuid, t);

        if (CoreGlobalProperty.UNIT_TEST_ON && !alwaysStartRightNow) {
            t.start();
        } else {
            t.startRightNow();
        }

        logger.debug(String.format("starting tracking hosts[uuid:%s]", hostUuid));
    }

    @Override
    public void untrackHost(String huuid) {
        Tracker t = trackers.get(huuid);
        if (t != null) {
            t.cancel();
        }
        trackers.remove(huuid);
        pingResponseEma.remove(huuid);
        logger.debug(String.format("stop tracking host[uuid:%s]", huuid));
    }

    @Override
    public void trackHost(Collection<String> huuids) {
        huuids.forEach(this::trackHost);
    }

    @Override
    public void untrackHost(Collection<String> huuids) {
        huuids.forEach(this::untrackHost);
    }

    private void reScanHost() {
        reScanHost(false);
    }

    private void reScanHost(boolean skipExisting) {
        if (!skipExisting) {
            new HashSet<>(trackers.values()).forEach(Tracker::cancel);
        }

        new SQLBatch() {
            @Override
            protected void scripts() {
                long count = sql("select count(h) from HostVO h", Long.class).find();
                sql("select h.uuid from HostVO h", String.class).limit(1000).paginate(count, (List<String> hostUuids) -> {
                    List<String> byUs = hostUuids.stream().filter(huuid -> {
                        if (skipExisting) {
                            return destMaker.isManagedByUs(huuid) && !trackers.containsKey(huuid);
                        } else {
                            return destMaker.isManagedByUs(huuid);
                        }
                    }).collect(Collectors.toList());

                    trackHost(byUs);
                });
            }
        }.execute();
    }

    @Override
    @AsyncThread
    public void nodeJoin(ManagementNodeInventory inv) {
        reScanHost();
    }

    @Override
    public void nodeLeft(ManagementNodeInventory inv) {
        reScanHost();
    }

    @Override
    public void iAmDead(ManagementNodeInventory inv) {

    }

    @Override
    public void iJoin(ManagementNodeInventory inv) {

    }

    private HostReconnectTaskFactory getHostReconnectTaskFactory(String hvType) {
        HostReconnectTaskFactory f = hostReconnectTaskFactories.get(hvType);
        if (f == null) {
            throw new CloudRuntimeException(String.format("cannot find HostReconnectTaskFactory with hypervisorType[%s]", hvType));
        }

        return f;
    }

    @Override
    public boolean start() {
        populateExtensions();
        onHostStatusChange();
        onHostPingSkip();

        HostGlobalConfig.PING_HOST_INTERVAL.installUpdateExtension((oldConfig, newConfig) -> {
            logger.debug(String.format("%s change from %s to %s, restart host trackers",
                    oldConfig.getCanonicalName(), oldConfig.value(), newConfig.value()));
            reScanHost();
        });

        HostGlobalConfig.AUTO_RECONNECT_ON_ERROR.installUpdateExtension((oc, nc)-> {
            if (nc.value(Boolean.class)) {
                logger.debug(String.format("%s change from %s to %s, restart host trackers",
                        oc.getCanonicalName(), oc.value(), nc.value()));
                reScanHost(true);
            }
        });

        return true;
    }

    private void populateExtensions() {
        pluginRgty.getExtensionList(HostReconnectTaskFactory.class).forEach(f -> {
            HostReconnectTaskFactory old = hostReconnectTaskFactories.get(f.getHypervisorType());
            if (old != null) {
                throw new CloudRuntimeException(String.format("duplicate HostReconnectTaskFactory[%s, %s] with the same type[%s]", f, old, f.getHypervisorType()));
            }

            hostReconnectTaskFactories.put(f.getHypervisorType(), f);
        });
    }

    private void onHostStatusChange() {
        evtf.onLocal(HostCanonicalEvents.HOST_STATUS_CHANGED_PATH, new EventCallback() {

            @Override
            protected void run(Map tokens, Object data) {
                HostCanonicalEvents.HostStatusChangedData d = (HostCanonicalEvents.HostStatusChangedData) data;
                if (HostStatus.Connected.toString().equals(d.getNewStatus())) {
                    hostDisconnectCount.remove(d.getHostUuid());
                } else if (HostStatus.Disconnected.toString().equals(d.getNewStatus()) &&
                        HostStatus.Connecting.toString().equals(d.getOldStatus())) {
                    hostDisconnectCount.computeIfAbsent(d.getHostUuid(), key -> new AtomicInteger(0)).addAndGet(1);
                }
            }
        });
    }

    private void onHostPingSkip() {
        evtf.on(HostCanonicalEvents.HOST_PING_SKIP, new EventCallback() {
            @Override
            protected void run(Map tokens, Object data) {
                HostCanonicalEvents.HostPingSkipData d = (HostCanonicalEvents.HostPingSkipData) data;
                Long deadline = System.currentTimeMillis() / 1000 + d.getSkipTimeInSec();
                skippedPingHostDeadline.put(d.getHostUuid(), deadline);
            }
        });
        evtf.on(HostCanonicalEvents.HOST_PING_CANCEL_SKIP, new EventCallback() {
            @Override
            protected void run(Map tokens, Object data) {
                HostCanonicalEvents.HostPingSkipData d = (HostCanonicalEvents.HostPingSkipData) data;
                skippedPingHostDeadline.invalidate(d.getHostUuid());
            }
        });
    }

    @Override
    public boolean stop() {
        return true;
    }
}

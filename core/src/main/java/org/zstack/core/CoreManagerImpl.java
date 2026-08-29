package org.zstack.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.ResourceDestinationMaker;
import org.zstack.core.singleflight.ExternalSingleFlightMsg;
import org.zstack.core.singleflight.ExternalSingleFlightReply;
import org.zstack.core.singleflight.MultiNodeSingleFlightImpl;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.AbstractService;
import org.zstack.header.core.*;
import org.zstack.header.core.execution.APIQueryExecutionMsg;
import org.zstack.header.core.execution.APIQueryExecutionReply;
import org.zstack.header.core.execution.ExecutionInventory;
import org.zstack.header.core.execution.ExecutionObservabilityFacade;
import org.zstack.header.core.execution.ExecutionStageInventory;
import org.zstack.header.core.execution.GetLocalExecutionMsg;
import org.zstack.header.core.execution.GetLocalExecutionReply;
import org.zstack.header.core.progress.ChainInfo;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.errorcode.SysErrors;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;
import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.addon.SingleFlightExecutor;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.sql.Timestamp;

import static org.zstack.core.Platform.operr;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.*;

public class CoreManagerImpl extends AbstractService implements CoreManager {
    private static final CLogger logger = Utils.getLogger(CoreManagerImpl.class);

    @Autowired
    private ResourceDestinationMaker destMaker;
    @Autowired
    private CloudBus bus;
    @Autowired
    private ThreadFacade thdf;
    @Autowired
    private MultiNodeSingleFlightImpl singleFlight;
    @Autowired
    private ExecutionObservabilityFacade executionObservability;

    @Override
    public void handleMessage(Message msg) {
        if (msg instanceof APIMessage) {
            handleApiMessage((APIMessage) msg);
        } else {
            handleLocalMessage(msg);
        }
    }

    private void handleApiMessage(APIMessage msg) {
        if (msg instanceof APIQueryExecutionMsg) {
            handleQueryExecution((APIQueryExecutionMsg) msg);
        } else if (msg instanceof APIGetChainTaskMsg) {
            handleMessage((APIGetChainTaskMsg) msg);
        }
    }

    private void handleLocalMessage(Message msg) {
        if (msg instanceof GetLocalTaskMsg) {
            handle((GetLocalTaskMsg) msg);
        } else if (msg instanceof GetLocalExecutionMsg) {
            handle((GetLocalExecutionMsg) msg);
        } else if (msg instanceof ExternalSingleFlightMsg) {
            handle((ExternalSingleFlightMsg) msg);
        }
    }

    private void handle(GetLocalExecutionMsg msg) {
        GetLocalExecutionReply reply = new GetLocalExecutionReply();
        if (msg.getQuery() != null) {
            reply.setInventories(executionObservability.queryLocal(msg.getQuery()));
        }
        bus.reply(msg, reply);
    }

    private void handleQueryExecution(APIQueryExecutionMsg msg) {
        if (msg.getLimit() != null && (msg.getLimit() < 1 || msg.getLimit() > 200)) {
            bus.replyErrorByMessageType(msg, Platform.argerr(ORG_ZSTACK_CORE_CLOUDBUS_10012,
                    "limit must be between 1 and 200"));
            return;
        }
        Timestamp startedAfter = parseQueryTimestamp(msg.getStartedAfter());
        Timestamp startedBefore = parseQueryTimestamp(msg.getStartedBefore());
        if ((msg.getStartedAfter() != null && startedAfter == null)
                || (msg.getStartedBefore() != null && startedBefore == null)) {
            bus.replyErrorByMessageType(msg, Platform.argerr(ORG_ZSTACK_CORE_CLOUDBUS_10012,
                    "startedAfter and startedBefore must be epoch milliseconds or ISO-8601 timestamps"));
            return;
        }
        if (startedAfter != null && startedBefore != null && startedAfter.after(startedBefore)) {
            bus.replyErrorByMessageType(msg, Platform.argerr(ORG_ZSTACK_CORE_CLOUDBUS_10012,
                    "startedAfter cannot be later than startedBefore"));
            return;
        }
        int selectors = 0;
        if (msg.getExecutionUuid() != null) selectors++;
        if (msg.getApiUuid() != null) selectors++;
        if (msg.getMessageUuid() != null) selectors++;
        if (msg.getTaskRunUuid() != null) selectors++;
        if (selectors > 1) {
            bus.replyErrorByMessageType(msg, Platform.argerr(ORG_ZSTACK_CORE_CLOUDBUS_10012,
                    "only one of executionUuid, apiUuid, messageUuid and taskRunUuid can be specified"));
            return;
        }
        if (selectors == 0 && msg.getTriggerType() == null && msg.getTriggerName() == null
                && msg.getState() == null && msg.getStartedAfter() == null && msg.getStartedBefore() == null
                && msg.getNodeUuid() == null) {
            bus.replyErrorByMessageType(msg, Platform.argerr(ORG_ZSTACK_CORE_CLOUDBUS_10012,
                    "an exact selector or a trigger, state, node or time bound is required"));
            return;
        }
        List<ExecutionInventory> result = Collections.synchronizedList(
                new ArrayList<>(executionObservability.queryLocal(queryAllFromNode(msg))));
        java.util.Collection<ResourceDestinationMaker.NodeInfo> nodes = destMaker.getAllNodeInfo();
        List<ResourceDestinationMaker.NodeInfo> remotes = nodes.stream()
                .filter(n -> !n.getNodeUuid().equals(Platform.getManagementServerId()))
                .collect(Collectors.toList());

        if (remotes.isEmpty()) {
            finishExecutionQuery(msg, result, false);
            return;
        }

        AtomicInteger pending = new AtomicInteger(remotes.size());
        AtomicBoolean partial = new AtomicBoolean(false);
        for (ResourceDestinationMaker.NodeInfo node : remotes) {
            GetLocalExecutionMsg local = new GetLocalExecutionMsg();
            local.setQuery(queryAllFromNode(msg));
            bus.makeServiceIdByManagementNodeId(local, CoreConstant.SERVICE_ID, node.getNodeUuid());
            try {
                bus.send(local, new CloudBusCallBack(msg) {
                    @Override
                    public void run(MessageReply r) {
                        if (r.isSuccess()) {
                            result.addAll(r.<GetLocalExecutionReply>castReply().getInventories());
                        } else {
                            partial.set(true);
                        }
                        if (pending.decrementAndGet() == 0) {
                            finishExecutionQuery(msg, result, partial.get());
                        }
                    }
                });
            } catch (Throwable t) {
                partial.set(true);
                if (pending.decrementAndGet() == 0) {
                    finishExecutionQuery(msg, result, true);
                }
            }
        }
    }

    private Timestamp parseQueryTimestamp(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new Timestamp(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            // Try ISO-8601 and JDBC timestamp forms below.
        }
        try {
            return Timestamp.from(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
            try {
                return Timestamp.valueOf(value);
            } catch (IllegalArgumentException ignoredAgain) {
                return null;
            }
        }
    }

    private APIQueryExecutionMsg queryAllFromNode(APIQueryExecutionMsg source) {
        APIQueryExecutionMsg query = new APIQueryExecutionMsg();
        query.setExecutionUuid(source.getExecutionUuid());
        query.setApiUuid(source.getApiUuid());
        query.setMessageUuid(source.getMessageUuid());
        query.setTaskRunUuid(source.getTaskRunUuid());
        query.setTriggerType(source.getTriggerType());
        query.setTriggerName(source.getTriggerName());
        query.setState(source.getState());
        query.setStartedAfter(source.getStartedAfter());
        query.setStartedBefore(source.getStartedBefore());
        query.setNodeUuid(source.getNodeUuid());
        query.setDetail(source.getDetail());
        query.setLimit(200);
        query.setCursor(null);
        return query;
    }

    private void finishExecutionQuery(APIQueryExecutionMsg msg, List<ExecutionInventory> inventories, boolean partial) {
        Map<String, ExecutionInventory> unique = new LinkedHashMap<>();
        for (ExecutionInventory inventory : inventories) {
            ExecutionInventory old = unique.get(inventory.getExecutionUuid());
            if (old == null) {
                unique.put(inventory.getExecutionUuid(), inventory);
                continue;
            }
            mergeExecutionInventory(old, inventory);
        }
        inventories = new ArrayList<>(unique.values());
        if (msg.getExecutionUuid() != null && inventories.isEmpty() && !partial) {
            bus.replyErrorByMessageType(msg, Platform.err(ORG_ZSTACK_CORE_CLOUDBUS_10012,
                    SysErrors.RESOURCE_NOT_FOUND, "execution[uuid:%s] not found", msg.getExecutionUuid()));
            return;
        }
        inventories.sort((a, b) -> {
            if (a.getAcceptedAt() == null || b.getAcceptedAt() == null) {
                return 0;
            }
            return b.getAcceptedAt().compareTo(a.getAcceptedAt());
        });

        int offset = 0;
        if (msg.getCursor() != null) {
            try {
                offset = Math.max(0, Integer.parseInt(msg.getCursor()));
            } catch (NumberFormatException ignored) {
                // Invalid cursors are treated as the first page for compatibility with
                // the node-local registry.
            }
        }
        int limit = msg.getLimit() == null ? 50 : Math.max(1, Math.min(200, msg.getLimit()));
        int total = inventories.size();
        int from = Math.min(offset, inventories.size());
        int to = Math.min(from + limit, inventories.size());
        List<ExecutionInventory> page = new ArrayList<>(inventories.subList(from, to));
        page.forEach(i -> {
            i.setVisibility("CLUSTER");
            i.setPartial(partial);
            i.setPartialReason(partial ? "one or more management nodes were unavailable" : null);
        });

        APIQueryExecutionReply reply = new APIQueryExecutionReply();
        reply.setInventories(page);
        reply.setTotal(total);
        reply.setNextCursor(to < inventories.size() ? String.valueOf(to) : null);
        bus.reply(msg, reply);
    }

    private void mergeExecutionInventory(ExecutionInventory target, ExecutionInventory source) {
        Set<String> nodes = new LinkedHashSet<>();
        if (target.getSourceNodes() != null) nodes.addAll(target.getSourceNodes());
        if (source.getSourceNodes() != null) nodes.addAll(source.getSourceNodes());
        target.setSourceNodes(new ArrayList<>(nodes));

        if (target.getFinishedAt() == null && source.getFinishedAt() != null) {
            target.setState(source.getState());
            target.setFinishedAt(source.getFinishedAt());
            target.setLastHeartbeatAt(source.getLastHeartbeatAt());
            target.setElapsedMs(source.getElapsedMs());
            target.setExecutionMs(source.getExecutionMs());
            target.setError(source.getError());
        }
        if (target.getEvents() != null && source.getEvents() != null) {
            Set<String> sequences = target.getEvents().stream()
                    .map(e -> eventKey(e.getNodeUuid(), e.getSequence())).collect(Collectors.toSet());
            source.getEvents().forEach(event -> {
                if (event.getSequence() == null || sequences.add(eventKey(event.getNodeUuid(), event.getSequence()))) {
                    target.getEvents().add(event);
                }
            });
            target.getEvents().sort((a, b) -> {
                if (a.getTimestamp() == null || b.getTimestamp() == null) return 0;
                return a.getTimestamp().compareTo(b.getTimestamp());
            });
        }
        if (source.getActiveStages() != null && !source.getActiveStages().isEmpty()) {
            Map<String, ExecutionStageInventory> stages = new LinkedHashMap<>();
            if (target.getActiveStages() != null) {
                target.getActiveStages().forEach(stage -> stages.put(stage.getStageUuid(), stage));
            }
            source.getActiveStages().forEach(stage -> stages.putIfAbsent(stage.getStageUuid(), stage));
            target.setActiveStages(new ArrayList<>(stages.values()));
        }
    }

    private String eventKey(String nodeUuid, Long sequence) {
        return String.valueOf(nodeUuid) + ":" + String.valueOf(sequence);
    }

    private void handle(ExternalSingleFlightMsg msg) {
        SingleFlightExecutor executor = MultiNodeSingleFlightImpl.getExecutor(msg.getResourceUuid());
        if (executor == null) {
            bus.replyErrorByMessageType(msg, operr(ORG_ZSTACK_CORE_10000, "no executor found for resourceUuid[%s]", msg.getResourceUuid()));
            return;
        }

        Object[] args = Arrays.copyOf(msg.getArgs(), msg.getArgs().length + 1);
        args[args.length - 1] = new ReturnValueCompletion<Object>(msg) {
            @Override
            public void success(Object returnValue) {
                ExternalSingleFlightReply reply = new ExternalSingleFlightReply();
                reply.setResult(returnValue);
                bus.reply(msg, reply);
            }

            @Override
            public void fail(ErrorCode errorCode) {
                bus.replyErrorByMessageType(msg, errorCode);
            }
        };
        singleFlight.run(executor, msg.getMethod(), args);
    }

    private void handleMessage(APIGetChainTaskMsg msg) {
        APIGetChainTaskReply reply = new APIGetChainTaskReply();
        Function<String, String> resourceUuidMaker = msg.getResourceUuidMaker();
        Map<String, List<String>> mnIds;
        if (resourceUuidMaker == null) {
            mnIds = destMaker.getAllNodeInfo().stream().collect(Collectors.toMap(
                    ResourceDestinationMaker.NodeInfo::getNodeUuid, list -> msg.getSyncSignatures()));
        } else {
            mnIds = msg.getSyncSignatures().stream().collect(Collectors.groupingBy(
                    syncSignature -> destMaker.makeDestination(resourceUuidMaker.apply(syncSignature))));
        }

        new While<>(mnIds.entrySet()).all((e, compl) -> {
            GetLocalTaskMsg gmsg = new GetLocalTaskMsg();
            gmsg.setSyncSignatures(e.getValue());
            bus.makeServiceIdByManagementNodeId(gmsg, CoreConstant.SERVICE_ID, e.getKey());
            bus.send(gmsg, new CloudBusCallBack(compl) {
                @Override
                public void run(MessageReply r) {
                    if (r.isSuccess()) {
                        GetLocalTaskReply gr = r.castReply();
                        Map<String, ChainInfo> result = gr.getResults();
                        if (resourceUuidMaker == null) {
                            reply.putAllResults(result);
                        } else {
                            Map<String, ChainInfo> chainInfoMap = result.keySet().stream().collect(
                                    Collectors.toMap(resourceUuidMaker, result::get));
                            reply.putAllResults(chainInfoMap);
                        }
                    } else {
                        logger.error("get task fail, because " + r.getError().getDetails());
                    }

                    compl.done();
                }
            });
        }).run(new WhileDoneCompletion(msg) {
            @Override
            public void done(ErrorCodeList errorCodeList) {
                bus.reply(msg, reply);
            }
        });
    }

    private void handle(GetLocalTaskMsg msg) {
        GetLocalTaskReply reply = new GetLocalTaskReply();
        Map<String, ChainInfo> results = msg.getSyncSignatures().stream()
                .collect(Collectors.toMap(Function.identity(), thdf::getChainTaskInfo));
        if (msg.isOnlyRunningTask()) {
            results.values().forEach(c -> c.getPendingTask().clear());
        }
        reply.setResults(results);
        bus.reply(msg, reply);
    }

    @Override
    public String getId() {
        return bus.makeLocalServiceId(CoreConstant.SERVICE_ID);
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }
}

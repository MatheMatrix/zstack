package org.zstack.core.execution;

import io.opentelemetry.api.trace.Span;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.Platform;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.header.Constants;
import org.zstack.header.Component;
import org.zstack.header.core.execution.*;
import org.zstack.header.message.APIEvent;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.Message;
import org.zstack.header.message.MessageReply;
import org.zstack.header.message.BeforeDeliveryMessageInterceptor;
import org.zstack.header.rest.RestAPIExtensionPoint;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * A bounded, node-local execution registry. The API handler aggregates this
 * registry from every management node through CloudBus, so no sticky REST
 * session or business-code changes are required.
 */
public class ExecutionObservabilityFacadeImpl implements ExecutionObservabilityFacade, Component,
        RestAPIExtensionPoint, BeforeDeliveryMessageInterceptor {
    private static final int MAX_RECORDS = 10000;
    private static final String API_QUERY_CLASS = APIQueryExecutionMsg.class.getName();
    private static final String EXECUTION_UUID_HEADER = "__execution_uuid__";
    /*
     * CloudBus serializes the Log4j ThreadContext into the message task
     * context. Keeping the execution UUID in that context makes a child
     * message carry the same correlation when it crosses to another
     * management node, without requiring business code to copy a header.
     */
    private static final String EXECUTION_UUID_CONTEXT = EXECUTION_UUID_HEADER;

    private final Map<String, MutableExecution> executions = new ConcurrentHashMap<>();
    private final Map<String, String> messageToExecution = new ConcurrentHashMap<>();
    private final Map<String, String> messageToStage = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> scheduledThreadContexts = new ConcurrentHashMap<>();

    @Autowired
    private CloudBus bus;

    @Override
    public void afterAPIRequest(Message message) {
        if (message instanceof APIMessage) {
            recordApiRequest((APIMessage) message);
        }
    }

    @Override
    public void beforeAPIResponse(Message message) {
        recordApiResponse(message);
    }

    @Override
    public void beforeRestResponse(String method, int statusCode) {
        // The API response hook is the authoritative completion point. HTTP
        // response timing is intentionally kept out of the execution record.
    }

    @Override
    public void afterRestRequest(String method) {
        // no-op
    }

    @Override
    public int orderOfBeforeDeliveryMessageInterceptor() {
        return Integer.MIN_VALUE;
    }

    @Override
    public void beforeDeliveryMessage(Message message) {
        recordMessageDelivery(message);
    }

    @Override
    public void recordApiRequest(APIMessage message) {
        if (message == null || API_QUERY_CLASS.equals(message.getClass().getName())) {
            return;
        }

        String executionUuid = message.getHeaderEntry(EXECUTION_UUID_HEADER);
        if (executionUuid == null || executionUuid.isEmpty()) {
            executionUuid = uuid();
            message.putHeaderEntry(EXECUTION_UUID_HEADER, executionUuid);
        }
        final String finalExecutionUuid = executionUuid;
        MutableExecution execution = executions.computeIfAbsent(finalExecutionUuid, id ->
                MutableExecution.api(finalExecutionUuid, message));
        messageToExecution.put(message.getId(), finalExecutionUuid);
        ThreadContext.put(EXECUTION_UUID_CONTEXT, finalExecutionUuid);
        execution.accepted();
        trimIfNeeded();
    }

    @Override
    public void recordMessageDelivery(Message message) {
        if (message == null) {
            return;
        }

        if (message instanceof GetLocalExecutionMsg) {
            // This is the fan-out control message used by the query API itself;
            // exposing it would make every read create another observable record.
            return;
        }

        if (message instanceof MessageReply) {
            String correlationId = message.getHeaderEntry("correlationId");
            if (correlationId != null) {
                String executionUuid = messageToExecution.get(correlationId);
                String stageUuid = messageToStage.get(correlationId);
                MutableExecution parent = executionUuid == null ? null : executions.get(executionUuid);
                if (parent != null && stageUuid != null) {
                    parent.finishStage(stageUuid, ((MessageReply) message).isSuccess(),
                            ((MessageReply) message).getError() == null ? null : ((MessageReply) message).getError().getDetails());
                    messageToStage.remove(correlationId, stageUuid);
                    return;
                }
                finish(correlationId, ((MessageReply) message).isSuccess(),
                        ((MessageReply) message).getError() == null ? null : ((MessageReply) message).getError().getDetails());
            }
            return;
        }

        if (message instanceof APIMessage) {
            recordApiRequest((APIMessage) message);
            MutableExecution execution = executions.get(messageToExecution.get(message.getId()));
            if (execution != null) {
                execution.deliveryStarted();
            }
            return;
        }

        String parentExecutionUuid = ThreadContext.get(EXECUTION_UUID_CONTEXT);
        if (parentExecutionUuid == null) {
            parentExecutionUuid = ThreadContext.get(Constants.THREAD_CONTEXT_API);
        }
        if (parentExecutionUuid == null) {
            parentExecutionUuid = ThreadContext.get(Constants.THREAD_CONTEXT_TASK);
        }
        String mappedParentUuid = parentExecutionUuid == null ? null : messageToExecution.get(parentExecutionUuid);
        MutableExecution parent = parentExecutionUuid == null ? null
                : executions.get(mappedParentUuid == null ? parentExecutionUuid : mappedParentUuid);
        if (parent == null && parentExecutionUuid != null) {
            final String inheritedExecutionUuid = parentExecutionUuid;
            parent = executions.computeIfAbsent(inheritedExecutionUuid,
                    id -> MutableExecution.inherited(inheritedExecutionUuid,
                            ThreadContext.get(Constants.THREAD_CONTEXT_TASK_NAME)));
        }
        if (parent != null) {
            String stageUuid = parent.startStage(message);
            messageToExecution.put(message.getId(), parent.executionUuid);
            messageToStage.put(message.getId(), stageUuid);
            return;
        }

        // A NeedReplyMessage is an observable execution even when no API is involved.
        // Fire-and-forget messages are completed at delivery because there is no reply
        // correlation from which a later completion could be inferred.
        MutableExecution execution = executions.get(messageToExecution.get(message.getId()));
        if (execution == null) {
            execution = MutableExecution.message(message.getId(), message);
            executions.put(execution.executionUuid, execution);
            messageToExecution.put(message.getId(), execution.executionUuid);
        }
        execution.deliveryStarted();
        String noReply = message.getHeaderEntry("noReply");
        if (!(message instanceof org.zstack.header.message.NeedReplyMessage)
                || Boolean.parseBoolean(noReply)) {
            execution.finish(true, null);
        }
        trimIfNeeded();
    }

    @Override
    public void recordApiResponse(Message message) {
        if (message == null) {
            return;
        }
        if (message instanceof APIEvent) {
            APIEvent event = (APIEvent) message;
            finish(event.getApiId(), event.isSuccess(), event.getError() == null ? null : event.getError().getDetails());
            return;
        }
        String correlationId = message.getHeaderEntry("correlationId");
        if (correlationId != null && message instanceof MessageReply) {
            MessageReply reply = (MessageReply) message;
            finish(correlationId, reply.isSuccess(), reply.getError() == null ? null : reply.getError().getDetails());
        }
    }

    @Override
    public void recordMessageTimeout(String messageUuid) {
        finishTerminal(messageUuid, "TIMEOUT", "message timeout");
    }

    @Override
    public void recordMessageCancellation(String messageUuid, String reason) {
        finishTerminal(messageUuid, "CANCELLED", reason);
    }

    @Override
    public String startScheduledTask(String taskName, String taskClass) {
        String executionUuid = uuid();
        scheduledThreadContexts.put(executionUuid, new HashMap<>(ThreadContext.getImmutableContext()));
        ThreadContext.put(Constants.THREAD_CONTEXT_API, executionUuid);
        ThreadContext.put(Constants.THREAD_CONTEXT_TASK_NAME, taskName);
        ThreadContext.put(EXECUTION_UUID_CONTEXT, executionUuid);
        MutableExecution execution = MutableExecution.scheduled(executionUuid, taskName, taskClass);
        executions.put(executionUuid, execution);
        execution.started();
        trimIfNeeded();
        return executionUuid;
    }

    @Override
    public void finishScheduledTask(String executionUuid, Throwable error) {
        if (executionUuid == null) {
            return;
        }
        MutableExecution execution = executions.get(executionUuid);
        if (execution != null) {
            execution.finish(error == null, error == null ? null : error.getMessage());
        }
        Map<String, String> previous = scheduledThreadContexts.remove(executionUuid);
        if (previous != null) {
            ThreadContext.clearAll();
            ThreadContext.putAll(previous);
        }
    }

    @Override
    public List<ExecutionInventory> queryLocal(APIQueryExecutionMsg query) {
        if (query == null) {
            return new ArrayList<>();
        }
        List<MutableExecution> candidates = new ArrayList<>(executions.values());
        candidates.removeIf(e -> !e.matches(query));
        candidates.sort(Comparator.comparingLong((MutableExecution e) -> e.acceptedAt).reversed());

        int offset = parseCursor(query.getCursor());
        int limit = query.getLimit() == null ? 50 : Math.max(1, Math.min(200, query.getLimit()));
        return candidates.stream().skip(offset).limit(limit)
                .map(e -> e.inventory(query.getDetail()))
                .collect(Collectors.toList());
    }

    private void finish(String correlationId, boolean success, String error) {
        if (correlationId == null) {
            return;
        }
        String executionUuid = messageToExecution.get(correlationId);
        MutableExecution execution = executions.get(executionUuid == null ? correlationId : executionUuid);
        if (execution != null) {
            execution.finish(success, error);
        }
    }

    private void finishTerminal(String messageUuid, String state, String error) {
        if (messageUuid == null) {
            return;
        }
        String executionUuid = messageToExecution.get(messageUuid);
        String stageUuid = messageToStage.get(messageUuid);
        MutableExecution execution = executions.get(executionUuid == null ? messageUuid : executionUuid);
        if (execution == null) {
            return;
        }
        if (stageUuid != null) {
            execution.finishStage(stageUuid, state, error);
            messageToStage.remove(messageUuid, stageUuid);
        } else {
            execution.finish(state, error);
        }
    }

    private static int parseCursor(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void trimIfNeeded() {
        if (executions.size() <= MAX_RECORDS) {
            return;
        }
        MutableExecution oldest = executions.values().stream()
                .min(Comparator.comparingLong(e -> e.acceptedAt)).orElse(null);
        if (oldest != null && executions.remove(oldest.executionUuid, oldest)) {
            messageToExecution.entrySet().removeIf(e -> e.getValue().equals(oldest.executionUuid));
            messageToStage.entrySet().removeIf(e -> e.getValue().equals(oldest.executionUuid));
        }
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private static Timestamp parseTimestamp(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new Timestamp(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            // Continue with the ISO-8601 and JDBC timestamp forms below.
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

    private static final class MutableExecution {
        private final String executionUuid;
        private final String apiUuid;
        private final String messageUuid;
        private final String taskRunUuid;
        private final String apiName;
        private final String requestKind;
        private final String triggerType;
        private final String triggerName;
        private final String taskClass;
        private final String traceId;
        private final long acceptedAt;
        private final String nodeUuid = Platform.getManagementServerId();
        private final AtomicLong sequence = new AtomicLong();
        private final List<ExecutionEventInventory> events = new ArrayList<>();
        private final Map<String, ExecutionStageInventory> stages = new ConcurrentHashMap<>();
        private final Set<String> observedMessageUuids = ConcurrentHashMap.newKeySet();
        private volatile long startedAt;
        private volatile long finishedAt;
        private volatile long lastHeartbeatAt;
        private volatile String state = "RECEIVED";
        private volatile String error;

        private MutableExecution(String executionUuid, String apiUuid, String messageUuid, String taskRunUuid,
                                 String apiName, String requestKind, String triggerType, String triggerName, String taskClass) {
            this.executionUuid = executionUuid;
            this.apiUuid = apiUuid;
            this.messageUuid = messageUuid;
            this.taskRunUuid = taskRunUuid;
            this.apiName = apiName;
            this.requestKind = requestKind;
            this.triggerType = triggerType;
            this.triggerName = triggerName;
            this.taskClass = taskClass;
            this.traceId = Span.current().getSpanContext().isValid() ? Span.current().getSpanContext().getTraceId() : null;
            this.acceptedAt = System.currentTimeMillis();
            this.lastHeartbeatAt = acceptedAt;
            if (messageUuid != null) {
                observedMessageUuids.add(messageUuid);
            }
        }

        static MutableExecution api(String executionUuid, APIMessage message) {
            return new MutableExecution(executionUuid, message.getId(), message.getId(), currentTaskId(), message.getClass().getName(),
                    "API", "API", message.getClass().getName(), message.getClass().getName());
        }

        static MutableExecution message(String id, Message message) {
            // Message ids are globally unique and are carried unchanged across
            // management nodes, which lets the cluster query de-duplicate a
            // delivery observed on both sides of a CloudBus hop.
            String taskRunUuid = currentTaskId();
            // CloudBus uses the root message id as a fallback THREAD_CONTEXT_TASK
            // for context-free messages. That fallback is not an independent task
            // run and must not be exposed as taskRunUuid.
            if (id.equals(taskRunUuid)) {
                taskRunUuid = null;
            }
            String messageClass = message == null ? "unknown-message" : message.getClass().getName();
            return new MutableExecution(id, null, id, taskRunUuid, messageClass,
                    "MESSAGE", "MESSAGE", messageClass, messageClass);
        }

        static MutableExecution scheduled(String id, String name, String taskClass) {
            return new MutableExecution(id, null, null, uuid(), name, "SCHEDULED_TASK",
                    "SCHEDULED_TASK", name, taskClass);
        }

        static MutableExecution inherited(String id, String name) {
            return new MutableExecution(id, null, null, null,
                    name == null ? "inherited-execution" : name, "MESSAGE", "MESSAGE",
                    name == null ? "inherited-execution" : name, name);
        }

        private static String currentTaskId() {
            String task = org.apache.logging.log4j.ThreadContext.get(Constants.THREAD_CONTEXT_TASK);
            return task == null ? null : task;
        }

        synchronized void accepted() {
            if ("RECEIVED".equals(state)) {
                state = "QUEUED";
                addEvent("API_ACCEPTED", null);
            }
            lastHeartbeatAt = System.currentTimeMillis();
        }

        synchronized void started() {
            if (startedAt == 0) {
                startedAt = System.currentTimeMillis();
            }
            state = "RUNNING";
            lastHeartbeatAt = System.currentTimeMillis();
            addEvent("EXECUTION_STARTED", null);
        }

        synchronized void deliveryStarted() {
            if (startedAt == 0) {
                startedAt = System.currentTimeMillis();
            }
            if (!"SUCCEEDED".equals(state) && !"FAILED".equals(state)) {
                state = "RUNNING";
            }
            lastHeartbeatAt = System.currentTimeMillis();
            addEvent("MESSAGE_DELIVERED", null);
        }

        synchronized String startStage(Message message) {
            String stageUuid = message.getId();
            observedMessageUuids.add(stageUuid);
            if (startedAt == 0) {
                startedAt = System.currentTimeMillis();
            }
            if ("RECEIVED".equals(state) || "QUEUED".equals(state)) {
                state = "RUNNING";
            }
            ExecutionStageInventory stage = new ExecutionStageInventory();
            stage.setStageUuid(stageUuid);
            stage.setName(message.getClass().getName());
            stage.setKind("MESSAGE");
            stage.setState("RUNNING");
            stage.setNodeUuid(nodeUuid);
            stage.setStartedAt(now());
            stages.put(stageUuid, stage);
            addEvent("STAGE_STARTED", stageUuid, null);
            lastHeartbeatAt = System.currentTimeMillis();
            return stageUuid;
        }

        synchronized void finishStage(String stageUuid, boolean success, String details) {
            finishStage(stageUuid, success ? "SUCCEEDED" : "FAILED", details);
        }

        synchronized void finishStage(String stageUuid, String terminalState, String details) {
            ExecutionStageInventory stage = stages.remove(stageUuid);
            if (stage == null) {
                return;
            }
            Timestamp finished = now();
            stage.setFinishedAt(finished);
            stage.setState(terminalState);
            stage.setElapsedMs(Math.max(0, finished.getTime() - stage.getStartedAt().getTime()));
            addEvent("STAGE_" + terminalState, stageUuid, details);
            lastHeartbeatAt = finished.getTime();
        }

        synchronized void finish(boolean success, String error) {
            finish(success ? "SUCCEEDED" : "FAILED", error);
        }

        synchronized void finish(String terminalState, String error) {
            if (finishedAt != 0) {
                return;
            }
            if (startedAt == 0) {
                startedAt = System.currentTimeMillis();
            }
            finishedAt = System.currentTimeMillis();
            lastHeartbeatAt = finishedAt;
            this.error = error;
            state = terminalState;
            addEvent("EXECUTION_" + terminalState, error);
        }

        synchronized boolean matches(APIQueryExecutionMsg query) {
            if (query.getExecutionUuid() != null && !query.getExecutionUuid().equals(executionUuid)) return false;
            if (query.getApiUuid() != null && !query.getApiUuid().equals(apiUuid)) return false;
            if (query.getMessageUuid() != null && !observedMessageUuids.contains(query.getMessageUuid())) return false;
            if (query.getTaskRunUuid() != null && !query.getTaskRunUuid().equals(taskRunUuid)) return false;
            if (query.getTriggerType() != null && !query.getTriggerType().equals(triggerType)) return false;
            if (query.getTriggerName() != null && !query.getTriggerName().equals(triggerName)) return false;
            if (query.getState() != null && !query.getState().equals(state)) return false;
            if (query.getNodeUuid() != null && !query.getNodeUuid().equals(nodeUuid)) return false;
            Timestamp after = parseTimestamp(query.getStartedAfter());
            Timestamp before = parseTimestamp(query.getStartedBefore());
            if (after != null && acceptedAt < after.getTime()) return false;
            if (before != null && acceptedAt > before.getTime()) return false;
            return true;
        }

        synchronized ExecutionInventory inventory(String detail) {
            ExecutionInventory inventory = new ExecutionInventory();
            inventory.setExecutionUuid(executionUuid);
            inventory.setApiUuid(apiUuid);
            inventory.setMessageUuid(messageUuid);
            inventory.setTaskRunUuid(taskRunUuid);
            inventory.setRootMessageUuid(messageUuid);
            inventory.setNodeUuid(nodeUuid);
            inventory.setOperationId(messageUuid == null ? executionUuid : messageUuid);
            inventory.setTraceId(traceId);
            inventory.setApiName(apiName);
            inventory.setRequestKind(requestKind);
            inventory.setState(state);
            ExecutionTriggerInventory trigger = new ExecutionTriggerInventory();
            trigger.setType(triggerType);
            trigger.setName(triggerName);
            trigger.setApiUuid(apiUuid);
            trigger.setTaskRunUuid(taskRunUuid);
            trigger.setMessageUuid(messageUuid);
            inventory.setTrigger(trigger);
            inventory.setAcceptedAt(new Timestamp(acceptedAt));
            inventory.setStartedAt(startedAt == 0 ? null : new Timestamp(startedAt));
            inventory.setFinishedAt(finishedAt == 0 ? null : new Timestamp(finishedAt));
            inventory.setLastHeartbeatAt(new Timestamp(lastHeartbeatAt));
            inventory.setObservedAt(now());
            inventory.setElapsedMs(startedAt == 0 ? null : (finishedAt == 0 ? System.currentTimeMillis() : finishedAt) - acceptedAt);
            inventory.setQueueWaitMs(startedAt == 0 ? null : startedAt - acceptedAt);
            inventory.setExecutionMs(startedAt == 0 ? null : (finishedAt == 0 ? System.currentTimeMillis() : finishedAt) - startedAt);
            inventory.setDownstreamWaitMs(0L);
            List<ExecutionStageInventory> active = new ArrayList<>();
            stages.values().forEach(stage -> active.add(copyStage(stage)));
            inventory.setActiveStages(active);
            String requestedDetail = detail == null ? "summary" : detail;
            inventory.setEvents("summary".equals(requestedDetail) ? new ArrayList<ExecutionEventInventory>() : new ArrayList<>(events));
            inventory.setPartial(false);
            inventory.setSourceNodes(new ArrayList<>(java.util.Collections.singletonList(nodeUuid)));
            inventory.setVisibility("LOCAL");
            inventory.setAttempt(1);
            inventory.setError(error);
            inventory.setTruncated(false);
            return inventory;
        }

        private ExecutionStageInventory copyStage(ExecutionStageInventory source) {
            ExecutionStageInventory stage = new ExecutionStageInventory();
            stage.setStageUuid(source.getStageUuid());
            stage.setParentStageUuid(source.getParentStageUuid());
            stage.setName(source.getName());
            stage.setKind(source.getKind());
            stage.setState(source.getState());
            stage.setNodeUuid(source.getNodeUuid());
            stage.setWaitingOn(source.getWaitingOn());
            stage.setWaitingReason(source.getWaitingReason());
            stage.setStartedAt(source.getStartedAt());
            stage.setFinishedAt(source.getFinishedAt());
            stage.setElapsedMs(source.getElapsedMs());
            return stage;
        }

        private void addEvent(String type, String details) {
            addEvent(type, null, details);
        }

        private void addEvent(String type, String stageUuid, String details) {
            ExecutionEventInventory event = new ExecutionEventInventory();
            event.setSequence(sequence.incrementAndGet());
            event.setTimestamp(now());
            event.setType(type);
            event.setStageUuid(stageUuid);
            event.setNodeUuid(nodeUuid);
            event.setMessageUuid(stageUuid == null ? messageUuid : stageUuid);
            event.setDetails(details);
            events.add(event);
            if (events.size() > 256) {
                events.remove(0);
            }
        }
    }

    public String getId() {
        return "executionObservability";
    }

    @Override
    public boolean start() {
        bus.installBeforeDeliveryMessageInterceptor(this);
        return true;
    }

    @Override
    public boolean stop() {
        executions.clear();
        messageToExecution.clear();
        messageToStage.clear();
        scheduledThreadContexts.clear();
        return true;
    }
}

package org.zstack.observability;

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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

/**
 * A bounded, node-local execution registry. The API handler aggregates this
 * registry from every management node through CloudBus, so no sticky REST
 * session or business-code changes are required.
 */
public class ExecutionObservabilityFacadeImpl implements ExecutionObservabilityFacade,
        ExecutionMessageObserver, ExecutionHttpObserver, ExecutionScheduledTaskObserver, Component,
        RestAPIExtensionPoint, BeforeDeliveryMessageInterceptor {
    private static final CLogger logger = Utils.getLogger(ExecutionObservabilityFacadeImpl.class);
    private static final int MAX_RECORDS = 10000;
    private static final int MAX_INDEX_ENTRIES = MAX_RECORDS * 2;
    private static final int MAX_ACTIVE_STAGES = 256;
    private static final int OBSERVATION_QUEUE_SIZE = 2048;
    private static final String EXECUTION_UUID_HEADER = "__execution_uuid__";
    private static final String EXECUTION_STAGE_CONTEXT = "__execution_stage_uuid__";
    private static final String IGNORED_EXECUTION_CONTEXT = "__execution_observation_ignored__";
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
    private final Set<String> ignoredExecutionContexts = ConcurrentHashMap.newKeySet();
    private final Map<String, String> httpToExecution = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> scheduledThreadContexts = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> executionOrder = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean cleanupQueued = new AtomicBoolean(false);

    private volatile ThreadPoolExecutor observationExecutor;

    private final ExecutionObservationPolicy observationPolicy = new ExecutionObservationPolicy();

    @Autowired
    private CloudBus bus;

    private MutableExecution getOrCreateExecution(String executionUuid,
                                                  java.util.function.Supplier<MutableExecution> factory) {
        MutableExecution existing = executions.get(executionUuid);
        if (existing != null) {
            return existing;
        }

        MutableExecution created = factory.get();
        MutableExecution previous = executions.putIfAbsent(executionUuid, created);
        if (previous != null) {
            return previous;
        }

        executionOrder.offer(executionUuid);
        return created;
    }

    private MutableExecution putExecutionIfAbsent(MutableExecution execution) {
        MutableExecution previous = executions.putIfAbsent(execution.executionUuid, execution);
        if (previous != null) {
            return previous;
        }

        executionOrder.offer(execution.executionUuid);
        return execution;
    }

    private boolean submitAsync(Runnable action, String operation) {
        // @AsyncThread only detaches the core caller. This queue owns the
        // observability backpressure and drop policy.
        ThreadPoolExecutor executor = observationExecutor;
        if (executor == null || executor.isShutdown()) {
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    action.run();
                } catch (Throwable t) {
                    logger.warn("failed to process asynchronous execution observation " + operation, t);
                }
            });
            return true;
        } catch (RejectedExecutionException ignored) {
            logger.debug("dropping asynchronous execution observation " + operation + " because the queue is full");
            return false;
        } catch (Throwable t) {
            logger.warn("failed to enqueue asynchronous execution observation " + operation, t);
            return false;
        }
    }

    @Override
    public void afterAPIRequest(Message message) {
        if (message instanceof APIMessage) {
            try {
                recordApiRequest((APIMessage) message);
            } catch (Throwable t) {
                logger.warn("failed to record API request observation", t);
            }
        }
    }

    @Override
    public void beforeAPIResponse(Message message) {
        try {
            clearIgnoredForResponse(message);
        } catch (Throwable t) {
            logger.warn("failed to clear API observation context", t);
        }
        submitAsync(() -> recordApiResponseInternal(message), "API response");
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
        final Map<String, String> context = new HashMap<>(ThreadContext.getImmutableContext());
        submitAsync(() -> {
            Map<String, String> previous = new HashMap<>(ThreadContext.getImmutableContext());
            try {
                ThreadContext.clearAll();
                ThreadContext.putAll(context);
                recordMessageDelivery(message);
            } finally {
                ThreadContext.clearAll();
                ThreadContext.putAll(previous);
            }
        }, "message delivery");
    }

    public void recordApiRequest(APIMessage message) {
        if (!observationPolicy.shouldObserveApi(message)) {
            markIgnored(message);
            return;
        }

        String executionUuid = message.getHeaderEntry(EXECUTION_UUID_HEADER);
        if (executionUuid == null || executionUuid.isEmpty()) {
            executionUuid = uuid();
            message.putHeaderEntry(EXECUTION_UUID_HEADER, executionUuid);
        }
        final String finalExecutionUuid = executionUuid;
        MutableExecution execution = getOrCreateExecution(finalExecutionUuid,
                () -> MutableExecution.api(finalExecutionUuid, message));
        messageToExecution.put(message.getId(), finalExecutionUuid);
        ThreadContext.put(EXECUTION_UUID_CONTEXT, finalExecutionUuid);
        execution.accepted();
        trimIfNeeded();
    }

    public void recordMessageDelivery(Message message) {
        if (message == null) {
            return;
        }

        if (message instanceof APIMessage
                && !observationPolicy.shouldObserveApi((APIMessage) message)) {
            // Querying executions must not itself become an observed execution.
            markIgnored((APIMessage) message);
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

        if (ThreadContext.get(IGNORED_EXECUTION_CONTEXT) != null) {
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
        if (parentExecutionUuid != null && ignoredExecutionContexts.contains(parentExecutionUuid)) {
            return;
        }
        String mappedParentUuid = parentExecutionUuid == null ? null : messageToExecution.get(parentExecutionUuid);
        MutableExecution parent = parentExecutionUuid == null ? null
                : executions.get(mappedParentUuid == null ? parentExecutionUuid : mappedParentUuid);
        // CloudBus uses the root message id as THREAD_CONTEXT_TASK for a
        // context-free message. That is the message being observed, not an
        // inherited parent execution; let the message branch below create
        // the execution with the message id as its rootMessageUuid.
        if (parent == null && parentExecutionUuid != null
                && !parentExecutionUuid.equals(message.getId())) {
            final String inheritedExecutionUuid = parentExecutionUuid;
            parent = getOrCreateExecution(inheritedExecutionUuid,
                    () -> MutableExecution.inherited(inheritedExecutionUuid,
                            ThreadContext.get(Constants.THREAD_CONTEXT_TASK_NAME)));
        }
        if (parent != null) {
            String stageUuid = parent.startStage(message);
            messageToExecution.put(message.getId(), parent.executionUuid);
            messageToStage.put(message.getId(), stageUuid);
            trimIfNeeded();
            return;
        }

        // A NeedReplyMessage is an observable execution even when no API is involved.
        // Fire-and-forget messages are completed at delivery because there is no reply
        // correlation from which a later completion could be inferred.
        MutableExecution execution = executions.get(messageToExecution.get(message.getId()));
        if (execution == null) {
            MutableExecution created = MutableExecution.message(message.getId(), message);
            execution = putExecutionIfAbsent(created);
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

    public void recordApiResponse(Message message) {
        clearIgnoredForResponse(message);
        recordApiResponseInternal(message);
    }

    private void clearIgnoredForResponse(Message message) {
        if (message instanceof APIEvent) {
            clearIgnored(((APIEvent) message).getApiId());
            return;
        }
        if (message instanceof MessageReply) {
            String correlationId = message.getHeaderEntry("correlationId");
            if (correlationId != null) {
                clearIgnored(correlationId);
            }
        }
    }

    private void recordApiResponseInternal(Message message) {
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

    private void markIgnored(APIMessage message) {
        if (message == null) {
            return;
        }
        ignoredExecutionContexts.add(message.getId());
        ThreadContext.put(IGNORED_EXECUTION_CONTEXT, message.getId());
        trimIfNeeded();
    }

    private void clearIgnored(String apiUuid) {
        if (apiUuid == null) {
            return;
        }
        ignoredExecutionContexts.remove(apiUuid);
        if (apiUuid.equals(ThreadContext.get(IGNORED_EXECUTION_CONTEXT))) {
            ThreadContext.remove(IGNORED_EXECUTION_CONTEXT);
        }
    }

    @Override
    public void recordMessageTimeout(String messageUuid) {
        submitAsync(() -> finishTerminal(messageUuid, "TIMEOUT", "message timeout"),
                "message timeout");
    }

    @Override
    public void recordMessageCancellation(String messageUuid, String reason) {
        submitAsync(() -> finishTerminal(messageUuid, "CANCELLED", reason),
                "message cancellation");
    }

    @Override
    public String startHttpRequest(String method, String url) {
        try {
            return startHttpRequestInternal(method, url);
        } catch (Throwable t) {
            logger.warn("failed to start HTTP execution observation", t);
            return null;
        }
    }

    private String startHttpRequestInternal(String method, String url) {
        String parentExecutionUuid = ThreadContext.get(EXECUTION_UUID_CONTEXT);
        if (parentExecutionUuid == null) {
            parentExecutionUuid = ThreadContext.get(Constants.THREAD_CONTEXT_API);
        }
        if (parentExecutionUuid == null) {
            parentExecutionUuid = ThreadContext.get(Constants.THREAD_CONTEXT_TASK);
        }

        String mappedExecutionUuid = parentExecutionUuid == null ? null : messageToExecution.get(parentExecutionUuid);
        MutableExecution execution = parentExecutionUuid == null ? null
                : executions.get(mappedExecutionUuid == null ? parentExecutionUuid : mappedExecutionUuid);
        if (execution == null) {
            return null;
        }

        String requestUuid = uuid();
        String parentStageUuid = ThreadContext.get(EXECUTION_STAGE_CONTEXT);
        execution.startHttpStage(requestUuid, parentStageUuid, method, sanitizeHttpUrl(url));
        httpToExecution.put(requestUuid, execution.executionUuid);
        trimIfNeeded();
        return requestUuid;
    }

    @Override
    public void finishHttpRequest(String requestUuid, String state, Integer statusCode, String error) {
        submitAsync(() -> finishHttpRequestInternal(requestUuid, state, statusCode, error),
                "HTTP response");
    }

    private void finishHttpRequestInternal(String requestUuid, String state, Integer statusCode, String error) {
        if (requestUuid == null) {
            return;
        }
        String executionUuid = httpToExecution.remove(requestUuid);
        MutableExecution execution = executionUuid == null ? null : executions.get(executionUuid);
        if (execution != null) {
            execution.finishHttpStage(requestUuid, state, statusCode, error);
        }
    }

    @Override
    public String startScheduledTask(String taskName, String taskClass) {
        String executionUuid = null;
        Map<String, String> previous = null;
        try {
            executionUuid = uuid();
            previous = new HashMap<>(ThreadContext.getImmutableContext());
            scheduledThreadContexts.put(executionUuid, previous);
            ThreadContext.put(Constants.THREAD_CONTEXT_API, executionUuid);
            ThreadContext.put(Constants.THREAD_CONTEXT_TASK_NAME, taskName);
            ThreadContext.put(EXECUTION_UUID_CONTEXT, executionUuid);
            MutableExecution execution = MutableExecution.scheduled(executionUuid, taskName, taskClass);
            putExecutionIfAbsent(execution);
            execution.started();
            trimIfNeeded();
            return executionUuid;
        } catch (Throwable t) {
            if (executionUuid != null) {
                scheduledThreadContexts.remove(executionUuid);
            }
            if (previous != null) {
                try {
                    ThreadContext.clearAll();
                    ThreadContext.putAll(previous);
                } catch (Throwable restoreFailure) {
                    logger.warn("failed to restore thread context after starting scheduled task observation", restoreFailure);
                }
            }
            logger.warn("failed to start scheduled task observation", t);
            return null;
        }
    }

    @Override
    public void finishScheduledTask(String executionUuid, Throwable error) {
        if (executionUuid == null) {
            return;
        }
        try {
            Map<String, String> previous = scheduledThreadContexts.remove(executionUuid);
            if (previous != null) {
                ThreadContext.clearAll();
                ThreadContext.putAll(previous);
            }
        } catch (Throwable t) {
            logger.warn(String.format("failed to restore thread context for scheduled task[%s]", executionUuid), t);
        }
        // Context restoration must happen on the task thread; only the
        // terminal observation is handed to the asynchronous path.
        submitAsync(() -> finishScheduledTaskInternal(executionUuid, error), "scheduled task response");
    }

    private void finishScheduledTaskInternal(String executionUuid, Throwable error) {
        MutableExecution execution = executions.get(executionUuid);
        if (execution != null) {
            execution.finish(error == null, error == null ? null : error.getMessage());
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
        if (executions.size() <= MAX_RECORDS
                && messageToExecution.size() <= MAX_INDEX_ENTRIES
                && messageToStage.size() <= MAX_INDEX_ENTRIES
                && ignoredExecutionContexts.size() <= MAX_INDEX_ENTRIES
                && httpToExecution.size() <= MAX_INDEX_ENTRIES
                && scheduledThreadContexts.size() <= MAX_INDEX_ENTRIES) {
            return;
        }

        while (executions.size() > MAX_RECORDS) {
            String oldestUuid = executionOrder.poll();
            if (oldestUuid == null) {
                break;
            }
            executions.remove(oldestUuid);
            scheduledThreadContexts.remove(oldestUuid);
        }

        if (cleanupQueued.compareAndSet(false, true)) {
            boolean queued = submitAsync(() -> {
                try {
                    cleanupIndexes();
                } finally {
                    cleanupQueued.set(false);
                    if (isOverLimit()) {
                        trimIfNeeded();
                    }
                }
            }, "registry cleanup");
            if (!queued) {
                cleanupQueued.set(false);
            }
        }
    }

    private boolean isOverLimit() {
        return executions.size() > MAX_RECORDS
                || messageToExecution.size() > MAX_INDEX_ENTRIES
                || messageToStage.size() > MAX_INDEX_ENTRIES
                || ignoredExecutionContexts.size() > MAX_INDEX_ENTRIES
                || httpToExecution.size() > MAX_INDEX_ENTRIES
                || scheduledThreadContexts.size() > MAX_INDEX_ENTRIES;
    }

    private void cleanupIndexes() {
        messageToExecution.entrySet().removeIf(e -> !executions.containsKey(e.getValue()));
        messageToStage.entrySet().removeIf(e -> {
            String executionUuid = messageToExecution.get(e.getKey());
            return executionUuid == null || !executions.containsKey(executionUuid);
        });
        httpToExecution.entrySet().removeIf(e -> !executions.containsKey(e.getValue()));
        scheduledThreadContexts.entrySet().removeIf(e -> !executions.containsKey(e.getKey()));

        trimMapToSize(messageToExecution, MAX_INDEX_ENTRIES);
        trimMapToSize(messageToStage, MAX_INDEX_ENTRIES);
        trimMapToSize(httpToExecution, MAX_INDEX_ENTRIES);
        trimMapToSize(scheduledThreadContexts, MAX_INDEX_ENTRIES);
        trimSetToSize(ignoredExecutionContexts, MAX_INDEX_ENTRIES);
    }

    private static <K, V> void trimMapToSize(Map<K, V> map, int maxEntries) {
        int excess = map.size() - maxEntries;
        if (excess <= 0) {
            return;
        }
        for (K key : map.keySet()) {
            if (excess-- <= 0) {
                break;
            }
            map.remove(key);
        }
    }

    private static <T> void trimSetToSize(Set<T> set, int maxEntries) {
        int excess = set.size() - maxEntries;
        if (excess <= 0) {
            return;
        }
        for (T value : set) {
            if (excess-- <= 0) {
                break;
            }
            set.remove(value);
        }
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private static String sanitizeHttpUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme != null && host != null) {
                StringBuilder sanitized = new StringBuilder(scheme).append("://");
                if (host.contains(":") && !host.startsWith("[")) {
                    sanitized.append('[').append(host).append(']');
                } else {
                    sanitized.append(host);
                }
                if (uri.getPort() > 0) {
                    sanitized.append(':').append(uri.getPort());
                }
                String path = uri.getRawPath();
                sanitized.append(path == null || path.isEmpty() ? "/" : path);
                return sanitized.toString();
            }
        } catch (URISyntaxException ignored) {
            // Keep malformed URLs from breaking the HTTP call or its callback.
        }
        return "<invalid-url>";
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
        private volatile long downstreamWaitMs;
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
            String parentStageUuid = ThreadContext.get(EXECUTION_STAGE_CONTEXT);
            if (parentStageUuid != null && !parentStageUuid.equals(stageUuid)) {
                stage.setParentStageUuid(parentStageUuid);
            }
            stage.setName(message.getClass().getName());
            stage.setKind("MESSAGE");
            stage.setState("RUNNING");
            stage.setNodeUuid(nodeUuid);
            stage.setStartedAt(now());
            stages.put(stageUuid, stage);
            trimStagesIfNeeded();
            ThreadContext.put(EXECUTION_STAGE_CONTEXT, stageUuid);
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
            addEvent("STAGE_" + terminalState, stageUuid, details, stage);
            if (stageUuid.equals(ThreadContext.get(EXECUTION_STAGE_CONTEXT))) {
                ThreadContext.remove(EXECUTION_STAGE_CONTEXT);
            }
            lastHeartbeatAt = finished.getTime();
        }

        synchronized String startHttpStage(String requestUuid, String parentStageUuid, String method, String url) {
            if (startedAt == 0) {
                startedAt = System.currentTimeMillis();
            }
            if ("RECEIVED".equals(state) || "QUEUED".equals(state)) {
                state = "RUNNING";
            }
            ExecutionStageInventory stage = new ExecutionStageInventory();
            stage.setStageUuid(requestUuid);
            stage.setParentStageUuid(parentStageUuid);
            stage.setName("HTTP " + method + " " + url);
            stage.setKind("HTTP");
            stage.setState("RUNNING");
            stage.setNodeUuid(nodeUuid);
            stage.setStartedAt(now());
            stage.setHttpMethod(method);
            stage.setHttpUrl(url);
            stages.put(requestUuid, stage);
            trimStagesIfNeeded();
            addHttpEvent("HTTP_REQUEST_STARTED", stage, null, null);
            lastHeartbeatAt = System.currentTimeMillis();
            return requestUuid;
        }

        private void trimStagesIfNeeded() {
            while (stages.size() > MAX_ACTIVE_STAGES) {
                ExecutionStageInventory oldest = stages.values().stream()
                        .min(Comparator.comparingLong(stage -> stage.getStartedAt() == null
                                ? Long.MIN_VALUE : stage.getStartedAt().getTime()))
                        .orElse(null);
                if (oldest == null || !stages.remove(oldest.getStageUuid(), oldest)) {
                    return;
                }
                if (oldest.getStageUuid().equals(ThreadContext.get(EXECUTION_STAGE_CONTEXT))) {
                    ThreadContext.remove(EXECUTION_STAGE_CONTEXT);
                }
            }
        }

        synchronized void finishHttpStage(String requestUuid, String terminalState, Integer statusCode, String error) {
            ExecutionStageInventory stage = stages.remove(requestUuid);
            if (stage == null) {
                return;
            }
            Timestamp finished = now();
            long elapsed = Math.max(0, finished.getTime() - stage.getStartedAt().getTime());
            stage.setFinishedAt(finished);
            stage.setState(terminalState);
            stage.setElapsedMs(elapsed);
            stage.setHttpStatusCode(statusCode);
            stage.setHttpElapsedMs(elapsed);
            downstreamWaitMs += elapsed;
            addHttpEvent("HTTP_REQUEST_" + terminalState, stage, statusCode, error);
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
            inventory.setDownstreamWaitMs(downstreamWaitMs);
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
            stage.setHttpMethod(source.getHttpMethod());
            stage.setHttpUrl(source.getHttpUrl());
            stage.setHttpStatusCode(source.getHttpStatusCode());
            stage.setHttpElapsedMs(source.getHttpElapsedMs());
            return stage;
        }

        private void addHttpEvent(String type, ExecutionStageInventory stage, Integer statusCode, String details) {
            ExecutionEventInventory event = new ExecutionEventInventory();
            event.setSequence(sequence.incrementAndGet());
            event.setTimestamp(now());
            event.setType(type);
            event.setStageUuid(stage.getStageUuid());
            event.setStageName(stage.getName());
            event.setStageKind(stage.getKind());
            event.setNodeUuid(nodeUuid);
            event.setMessageUuid(stage.getStageUuid());
            event.setParentStageUuid(stage.getParentStageUuid());
            event.setDetails(details);
            event.setHttpMethod(stage.getHttpMethod());
            event.setHttpUrl(stage.getHttpUrl());
            event.setHttpStatusCode(statusCode);
            event.setHttpElapsedMs(stage.getHttpElapsedMs());
            events.add(event);
            if (events.size() > 256) {
                events.remove(0);
            }
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
            ExecutionStageInventory stage = stageUuid == null ? null : stages.get(stageUuid);
            if (stage != null) {
                event.setStageName(stage.getName());
                event.setStageKind(stage.getKind());
                event.setParentStageUuid(stage.getParentStageUuid());
            }
            event.setDetails(details);
            events.add(event);
            if (events.size() > 256) {
                events.remove(0);
            }
        }

        private void addEvent(String type, String stageUuid, String details, ExecutionStageInventory stage) {
            ExecutionEventInventory event = new ExecutionEventInventory();
            event.setSequence(sequence.incrementAndGet());
            event.setTimestamp(now());
            event.setType(type);
            event.setStageUuid(stageUuid);
            event.setNodeUuid(nodeUuid);
            event.setMessageUuid(stageUuid == null ? messageUuid : stageUuid);
            event.setStageName(stage == null ? null : stage.getName());
            event.setStageKind(stage == null ? null : stage.getKind());
            event.setParentStageUuid(stage == null ? null : stage.getParentStageUuid());
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
        cleanupQueued.set(false);
        observationExecutor = new ThreadPoolExecutor(
                1, 1, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(OBSERVATION_QUEUE_SIZE),
                new ObservationThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        bus.installBeforeDeliveryMessageInterceptor(this);
        return true;
    }

    @Override
    public boolean stop() {
        ThreadPoolExecutor executor = observationExecutor;
        observationExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        cleanupQueued.set(false);
        executions.clear();
        executionOrder.clear();
        messageToExecution.clear();
        messageToStage.clear();
        ignoredExecutionContexts.clear();
        httpToExecution.clear();
        scheduledThreadContexts.clear();
        return true;
    }

    private static final class ObservationThreadFactory implements ThreadFactory {
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    "zs-execution-observation-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}

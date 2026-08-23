package org.zstack.storage.zbs;

import org.zstack.core.CoreGlobalProperty;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.zstack.core.Platform.operr;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.ORG_ZSTACK_CORE_10000;

public class FakeZbsCpuIsolationProvider implements ZbsCpuIsolationProvider {
    private final Map<String, ZbsCpuIsolationFact> facts = new ConcurrentHashMap<>();
    private final AtomicInteger queryCalls = new AtomicInteger();
    private final AtomicInteger updateCalls = new AtomicInteger();
    private final AtomicBoolean holdNextQuery = new AtomicBoolean();
    private final AtomicBoolean holdNextUpdate = new AtomicBoolean();
    private final AtomicReference<HeldQuery> heldQuery = new AtomicReference<>();
    private final AtomicReference<HeldUpdate> heldUpdate = new AtomicReference<>();
    private volatile boolean enabled;
    private volatile boolean autoApply;

    @Override
    public String getProviderType() {
        return "FAKE";
    }

    @Override
    public boolean isAvailable(ZbsNodeRef nodeRef) {
        return CoreGlobalProperty.UNIT_TEST_ON && enabled;
    }

    @Override
    public void query(ZbsNodeRef nodeRef, ReturnValueCompletion<ZbsCpuIsolationFact> completion) {
        queryCalls.incrementAndGet();
        if (!isAvailable(nodeRef)) {
            completion.fail(operr(ORG_ZSTACK_CORE_10000,
                    "FAKE_ZBS_PROVIDER_DISABLED: fake provider never becomes production ready"));
            return;
        }
        ZbsCpuIsolationFact fact = facts.get(nodeRef.getServerUuid());
        if (fact == null) {
            completion.fail(operr(ORG_ZSTACK_CORE_10000,
                    "FAKE_ZBS_FACT_MISSING: no fake fact for physical server[uuid:%s]", nodeRef.getServerUuid()));
            return;
        }
        ZbsCpuIsolationFact response = copy(fact);
        if (holdNextQuery.compareAndSet(true, false)) {
            heldQuery.set(new HeldQuery(completion, response));
            return;
        }
        completion.success(response);
    }

    @Override
    public void update(ZbsNodeRef nodeRef, ZbsCpuIsolationUpdate update, Completion completion) {
        updateCalls.incrementAndGet();
        if (!isAvailable(nodeRef)) {
            completion.fail(operr(ORG_ZSTACK_CORE_10000,
                    "FAKE_ZBS_PROVIDER_DISABLED: fake provider never becomes production ready"));
            return;
        }
        if (holdNextUpdate.compareAndSet(true, false)) {
            heldUpdate.set(new HeldUpdate(nodeRef, update, completion));
            return;
        }
        if (autoApply) {
            applyUpdate(nodeRef, update);
        }
        completion.success();
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
        autoApply = false;
        facts.clear();
        queryCalls.set(0);
        updateCalls.set(0);
        holdNextQuery.set(false);
        holdNextUpdate.set(false);
        heldQuery.set(null);
        heldUpdate.set(null);
    }

    public void setAutoApply(boolean autoApply) {
        this.autoApply = autoApply;
    }

    public void setFact(String serverUuid, ZbsCpuIsolationFact fact) {
        facts.put(serverUuid, copy(fact));
    }

    public int getQueryCalls() {
        return queryCalls.get();
    }

    public int getUpdateCalls() {
        return updateCalls.get();
    }

    public void holdNextQuery() {
        holdNextQuery.set(true);
    }

    public boolean hasHeldQuery() {
        return heldQuery.get() != null;
    }

    public void completeHeldQuery() {
        HeldQuery held = heldQuery.getAndSet(null);
        if (held != null) {
            held.completion.success(held.fact);
        }
    }

    public void holdNextUpdate() {
        holdNextUpdate.set(true);
    }

    public boolean hasHeldUpdate() {
        return heldUpdate.get() != null;
    }

    public void completeHeldUpdate() {
        HeldUpdate held = heldUpdate.getAndSet(null);
        if (held != null) {
            if (autoApply) {
                applyUpdate(held.nodeRef, held.update);
            }
            held.completion.success();
        }
    }

    private void applyUpdate(ZbsNodeRef nodeRef, ZbsCpuIsolationUpdate update) {
        ZbsCpuIsolationFact fact = new ZbsCpuIsolationFact();
        fact.setIsolationState(
                "DISABLE".equals(update.getOperation()) ? "DISABLED" : "READY");
        fact.setConfiguredCpuSet(update.getDesiredCpuSet());
        fact.setEffectiveCpuSet(update.getDesiredCpuSet());
        fact.setMemory(update.getMemory() != null && update.getMemory() == 0L
                ? null : update.getMemory());
        fact.setExpectedServiceCount(1);
        fact.setCoveredServiceCount(1);
        fact.setServiceReady(true);
        facts.put(nodeRef.getServerUuid(), fact);
    }

    private ZbsCpuIsolationFact copy(ZbsCpuIsolationFact source) {
        ZbsCpuIsolationFact copy = new ZbsCpuIsolationFact();
        copy.setIsolationState(source.getIsolationState());
        copy.setConfiguredCpuSet(source.getConfiguredCpuSet());
        copy.setEffectiveCpuSet(source.getEffectiveCpuSet());
        copy.setMemory(source.getMemory());
        copy.setCoveredServiceCount(source.getCoveredServiceCount());
        copy.setExpectedServiceCount(source.getExpectedServiceCount());
        copy.setServiceReady(source.isServiceReady());
        return copy;
    }

    private static class HeldQuery {
        private final ReturnValueCompletion<ZbsCpuIsolationFact> completion;
        private final ZbsCpuIsolationFact fact;

        private HeldQuery(
                ReturnValueCompletion<ZbsCpuIsolationFact> completion,
                ZbsCpuIsolationFact fact) {
            this.completion = completion;
            this.fact = fact;
        }
    }

    private static class HeldUpdate {
        private final ZbsNodeRef nodeRef;
        private final ZbsCpuIsolationUpdate update;
        private final Completion completion;

        private HeldUpdate(
                ZbsNodeRef nodeRef,
                ZbsCpuIsolationUpdate update,
                Completion completion) {
            this.nodeRef = nodeRef;
            this.update = update;
            this.completion = completion;
        }
    }
}

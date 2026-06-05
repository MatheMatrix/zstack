package org.zstack.header.candidate;

import org.zstack.header.host.HostInventory;
import org.zstack.header.host.HostVO;
import org.zstack.utils.DebugUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class CandidateDecisionRecorder<T> {
    private final CandidateDecisionContext context;
    private final LinkedHashMap<String, CandidateRecord<T>> records = new LinkedHashMap<>();
    private final Set<String> finalCandidateUuids = new LinkedHashSet<>();

    public CandidateDecisionRecorder(CandidateDecisionContext context) {
        this.context = context;
    }

    public boolean isEnabled() {
        return context != null;
    }

    public void registerUniverse(String candidateType, Collection<T> inventories, Function<T, String> uuidExtractor) {
        if (!isEnabled() || inventories == null) {
            return;
        }

        for (T inventory : inventories) {
            String uuid = uuidExtractor.apply(inventory);
            if (uuid == null || records.containsKey(uuid)) {
                continue;
            }

            CandidateRecord<T> record = new CandidateRecord<>();
            record.setCandidateType(candidateType);
            record.setCandidateUuid(uuid);
            record.setInventory(inventory);
            records.put(uuid, record);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerHostUniverse(Collection<HostVO> hosts) {
        if (!isEnabled() || hosts == null) {
            return;
        }

        List<HostInventory> inventories = HostInventory.valueOf(new ArrayList<>(hosts));
        registerUniverse(CandidateTypes.HOST, (Collection) inventories, inventory -> ((HostInventory) inventory).getUuid());
    }

    public void pass(String candidateUuid) {
    }

    public void reject(String candidateUuid, CandidateRejectReason reason) {
        CandidateRecord<T> record = records.get(candidateUuid);
        if (record == null) {
            return;
        }

        DebugUtils.Assert(record.getDecision() == null,
                String.format("candidate[%s] decision is already %s", candidateUuid, record.getDecision()));
        record.setDecision(CandidateTypes.REJECTED);
        record.setReason(reason);
    }

    public void rejectIfNotRejected(String candidateUuid, CandidateRejectReason reason) {
        CandidateRecord<T> record = records.get(candidateUuid);
        if (record == null || CandidateTypes.REJECTED.equals(record.getDecision())) {
            return;
        }

        DebugUtils.Assert(record.getDecision() == null,
                String.format("candidate[%s] decision is already %s", candidateUuid, record.getDecision()));
        record.setDecision(CandidateTypes.REJECTED);
        record.setReason(reason);
    }

    public void skip(String candidateUuid, CandidateRejectReason reason) {
        CandidateRecord<T> record = records.get(candidateUuid);
        if (record == null || CandidateTypes.REJECTED.equals(record.getDecision())) {
            return;
        }

        DebugUtils.Assert(record.getDecision() == null,
                String.format("candidate[%s] decision is already %s", candidateUuid, record.getDecision()));
        record.setDecision(CandidateTypes.SKIPPED);
        record.setReason(reason);
    }

    public void select(String candidateUuid) {
        CandidateRecord<T> record = records.get(candidateUuid);
        if (record == null) {
            return;
        }

        DebugUtils.Assert(record.getDecision() == null,
                String.format("candidate[%s] decision is already %s", candidateUuid, record.getDecision()));
        record.setDecision(CandidateTypes.SELECTED);
    }

    public void selectAll(Collection<String> candidateUuids) {
        if (candidateUuids == null) {
            return;
        }

        candidateUuids.forEach(this::select);
    }

    public void markFinalCandidates(Collection<String> candidateUuids) {
        finalCandidateUuids.clear();
        if (candidateUuids != null) {
            finalCandidateUuids.addAll(candidateUuids);
        }
    }

    public CandidateDecisionResult build() {
        CandidateDecisionResult result = new CandidateDecisionResult();
        List<CandidateRecord<?>> candidates = new ArrayList<>();
        for (CandidateRecord<T> record : records.values()) {
            if (record.getDecision() == null) {
                if (finalCandidateUuids.contains(record.getCandidateUuid())) {
                    record.setDecision(CandidateTypes.SELECTED);
                } else {
                    record.setDecision(CandidateTypes.SKIPPED);
                    if (record.getReason() == null) {
                        record.setReason(CandidateRejectReason.of(
                                CandidateReasonCodes.HOST_SKIPPED_BY_PREVIOUS_DECISION,
                                CandidateCategories.UNKNOWN,
                                "candidate was skipped by previous decision"
                        ));
                    }
                }
            }
            candidates.add(record);
        }

        result.setCandidates(candidates);
        result.setSummary(CandidateDecisionSummary.from(candidates));
        result.setTruncated(false);
        return result;
    }
}

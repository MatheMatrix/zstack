package org.zstack.header.candidate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CandidateDecisionSummary {
    private int total;
    private int selected;
    private int rejected;
    private int skipped;
    private Map<String, Integer> byCode = new LinkedHashMap<>();
    private Map<String, Integer> byCategory = new LinkedHashMap<>();
    private Map<String, TypeBreakdown> byType = new LinkedHashMap<>();

    public static CandidateDecisionSummary from(List<CandidateRecord<?>> records) {
        CandidateDecisionSummary summary = new CandidateDecisionSummary();
        for (CandidateRecord<?> record : records) {
            summary.total++;
            if (CandidateTypes.SELECTED.equals(record.getDecision())) {
                summary.selected++;
            } else if (CandidateTypes.REJECTED.equals(record.getDecision())) {
                summary.rejected++;
            } else if (CandidateTypes.SKIPPED.equals(record.getDecision())) {
                summary.skipped++;
            }

            TypeBreakdown breakdown = summary.byType.get(record.getCandidateType());
            if (breakdown == null) {
                breakdown = new TypeBreakdown();
                summary.byType.put(record.getCandidateType(), breakdown);
            }
            breakdown.count(record.getDecision());

            CandidateRejectReason reason = record.getReason();
            if (reason != null) {
                count(summary.byCode, reason.getCode());
                count(summary.byCategory, reason.getCategory());
            }
        }
        return summary;
    }

    private static void count(Map<String, Integer> map, String key) {
        if (key == null) {
            return;
        }
        Integer value = map.get(key);
        map.put(key, value == null ? 1 : value + 1);
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSelected() {
        return selected;
    }

    public void setSelected(int selected) {
        this.selected = selected;
    }

    public int getRejected() {
        return rejected;
    }

    public void setRejected(int rejected) {
        this.rejected = rejected;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public Map<String, Integer> getByCode() {
        return byCode;
    }

    public void setByCode(Map<String, Integer> byCode) {
        this.byCode = byCode;
    }

    public Map<String, Integer> getByCategory() {
        return byCategory;
    }

    public void setByCategory(Map<String, Integer> byCategory) {
        this.byCategory = byCategory;
    }

    public Map<String, TypeBreakdown> getByType() {
        return byType;
    }

    public void setByType(Map<String, TypeBreakdown> byType) {
        this.byType = byType;
    }
}

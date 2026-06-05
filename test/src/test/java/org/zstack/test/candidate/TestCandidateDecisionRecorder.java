package org.zstack.test.candidate;

import org.junit.Test;
import org.zstack.header.candidate.*;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class TestCandidateDecisionRecorder {
    @Test
    public void testBuildSummaryAndStates() {
        CandidateDecisionContext ctx = new CandidateDecisionContext();
        CandidateDecisionRecorder<String> recorder = new CandidateDecisionRecorder<>(ctx);
        recorder.registerUniverse(CandidateTypes.HOST, Arrays.asList("host-1", "host-2", "host-3"), s -> s);

        recorder.reject("host-1", CandidateRejectReason.of(
                CandidateReasonCodes.HOST_MEMORY_NOT_ENOUGH,
                CandidateCategories.CAPACITY,
                "available memory is not enough"
        ).detail("stage", "capacity"));
        recorder.markFinalCandidates(Collections.singletonList("host-2"));

        CandidateDecisionResult result = recorder.build();

        assertEquals(3, result.getCandidates().size());
        assertFalse(result.getTruncated());
        assertEquals(3, result.getSummary().getTotal());
        assertEquals(1, result.getSummary().getSelected());
        assertEquals(1, result.getSummary().getRejected());
        assertEquals(1, result.getSummary().getSkipped());
        assertEquals(Integer.valueOf(1), result.getSummary().getByCode().get(CandidateReasonCodes.HOST_MEMORY_NOT_ENOUGH));
        assertEquals(Integer.valueOf(1), result.getSummary().getByCategory().get(CandidateCategories.CAPACITY));

        assertEquals(CandidateTypes.REJECTED, result.getCandidates().get(0).getDecision());
        assertEquals(CandidateTypes.SELECTED, result.getCandidates().get(1).getDecision());
        assertEquals(CandidateTypes.SKIPPED, result.getCandidates().get(2).getDecision());
    }

    @Test
    public void testRejectCannotBeOverwritten() {
        CandidateDecisionContext ctx = new CandidateDecisionContext();
        CandidateDecisionRecorder<String> recorder = new CandidateDecisionRecorder<>(ctx);
        recorder.registerUniverse(CandidateTypes.HOST, Collections.singletonList("host-1"), s -> s);
        recorder.reject("host-1", CandidateRejectReason.of(
                CandidateReasonCodes.HOST_STATE_NOT_ENABLED,
                CandidateCategories.STATUS,
                "host state is not Enabled"
        ));

        try {
            recorder.select("host-1");
            fail("selecting rejected candidate should fail");
        } catch (RuntimeException expected) {
        }
    }

    @Test
    public void testDetailsWhitelistRejectsUnknownKey() {
        try {
            CandidateRejectReason.of(
                    CandidateReasonCodes.HOST_REJECTED_BY_ALLOCATOR_FLOW,
                    CandidateCategories.UNKNOWN,
                    "host was rejected"
            ).detail("notAllowed", "value");
            fail("unknown detail key should fail");
        } catch (RuntimeException expected) {
        }
    }
}

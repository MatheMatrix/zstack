package org.zstack.header.allocator;

import org.zstack.header.host.HostVO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HostCandidate implements Serializable {
    public final HostVO host;

    public List<String> recommendBy;
    public List<String> notRecommendBy;
    public String reject;
    public String rejectBy;

    public HostCandidate(HostVO host) {
        this.host = Objects.requireNonNull(host);
    }

    public String getUuid() {
        return host.getUuid();
    }

    public void markAsRecommended(String flowName) {
        recommendBy = recommendBy == null ? new ArrayList<>() : recommendBy;
        recommendBy.add(flowName);
    }

    public void markAsNotRecommended(String flowName) {
        notRecommendBy = notRecommendBy == null ? new ArrayList<>() : notRecommendBy;
        notRecommendBy.add(flowName);
    }

    public void markAsRejected(String flowName, String reason) {
        rejectBy = flowName;
        reject = reason;
    }

    @Override
    public String toString() {
        return host.getUuid();
    }

    public RejectedCandidate toRejectedCandidate() {
        return new RejectedCandidate(host.getUuid(), host.getName(), reject, rejectBy);
    }

    public static class RejectedCandidate {
        public final String hostUuid;
        public final String hostName;
        public final String reject;
        public final String rejectBy;

        private RejectedCandidate(String hostUuid, String hostName, String reject, String rejectBy) {
            this.hostUuid = hostUuid;
            this.hostName = hostName;
            this.reject = reject;
            this.rejectBy = rejectBy;
        }
    }
}

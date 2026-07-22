package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.Platform;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.candidate.CandidateCategories;
import org.zstack.header.candidate.CandidateReasonCodes;
import org.zstack.header.candidate.CandidateRejectReason;
import org.zstack.header.host.HostVO;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.function.Function;

import java.util.ArrayList;
import java.util.List;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.*;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class AvoidHostAllocatorFlow extends AbstractHostAllocatorFlow {
    @Override
    public void allocate() {
        throwExceptionIfIAmTheFirstFlow();

        List<HostVO> ret;
        if (isCandidateDecisionEnabled()) {
            ret = new ArrayList<>(candidates.size());
            for (HostVO candidate : candidates) {
                if (spec.getAvoidHostUuids().contains(candidate.getUuid())) {
                    reject(candidate, CandidateRejectReason.of(
                            CandidateReasonCodes.HOST_IN_AVOID_LIST,
                            CandidateCategories.POLICY,
                            "host is in avoid list"
                    ).detail("stage", "policy")
                     .detail("avoidHostUuids", spec.getAvoidHostUuids()));
                    continue;
                }
                pass(candidate);
                ret.add(candidate);
            }
        } else {
            ret = CollectionUtils.transformToList(candidates, new Function<HostVO, HostVO>() {
                @Override
                public HostVO call(HostVO arg) {
                    if (!spec.getAvoidHostUuids().contains(arg.getUuid())) {
                        return arg;
                    }
                    return null;
                }
            });
        }

        if (ret.isEmpty()) {
            fail(Platform.operr(ORG_ZSTACK_COMPUTE_ALLOCATOR_10026, "after rule out avoided host%s, there is no host left in candidates", spec.getAvoidHostUuids()));
        } else {
            next(ret);
        }
    }
}

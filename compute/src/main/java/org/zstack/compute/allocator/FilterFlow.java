package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.Platform;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.allocator.HostAllocatorFilterExtensionPoint;
import org.zstack.header.candidate.CandidateCategories;
import org.zstack.header.candidate.CandidateReasonCodes;
import org.zstack.header.candidate.CandidateRejectReason;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.host.HostVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.*;

/**
 * Created by frank on 7/2/2015.
 */
public class FilterFlow extends AbstractHostAllocatorFlow {
    private final CLogger logger = Utils.getLogger(FilterFlow.class);

    @Autowired
    private PluginRegistry pluginRgty;

    @Override
    public void allocate() {
        throwExceptionIfIAmTheFirstFlow();

        for (HostAllocatorFilterExtensionPoint filter : pluginRgty.getExtensionList(HostAllocatorFilterExtensionPoint.class)) {
            logger.debug(String.format("before being filtered by HostAllocatorFilterExtensionPoint[%s], candidates num: %s", filter.getClass(), candidates.size()));
            List<HostVO> before = isCandidateDecisionEnabled() ? new ArrayList<>(candidates) : null;
            try {
                candidates = filter.filterHostCandidates(candidates, spec);
            } catch (OperationFailureException e) {
                fail(e.getErrorCode());
                return;
            }
            if (isCandidateDecisionEnabled()) {
                Set<String> after = candidates.stream().map(HostVO::getUuid).collect(Collectors.toSet());
                for (HostVO host : before) {
                    if (!after.contains(host.getUuid())) {
                        reject(host, CandidateRejectReason.of(
                                CandidateReasonCodes.HOST_REJECTED_BY_EXTENSION,
                                CandidateCategories.POLICY,
                                filter.filterErrorReason()
                        ).detail("stage", "extension")
                         .detail("extension", filter.getClass().getName()));
                    }
                }
            }
            logger.debug(String.format("after being filtered by HostAllocatorFilterExtensionPoint[%s], candidates num: %s", filter.getClass(), candidates.size()));

            if (candidates.isEmpty()) {
                fail(Platform.operr(ORG_ZSTACK_COMPUTE_ALLOCATOR_10037, "after filtering, HostAllocatorFilterExtensionPoint[%s] returns zero candidate host, it means: %s", filter.getClass().getSimpleName(), filter.filterErrorReason()));
            }
        }

        next(candidates);
    }
}

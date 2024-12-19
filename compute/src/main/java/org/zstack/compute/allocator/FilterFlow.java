package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.Platform;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.allocator.HostAllocatorFilterExtensionPoint;
import org.zstack.header.allocator.HostCandidate;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.host.HostVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.zstack.utils.CollectionUtils.*;

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

        Map<String, HostCandidate> uuidCandidateMap = toMap(candidates, HostCandidate::getUuid, Function.identity());

        List<HostVO> hostsBefore = transform(candidates, candidate -> candidate.host);
        for (HostAllocatorFilterExtensionPoint filter : pluginRgty.getExtensionList(HostAllocatorFilterExtensionPoint.class)) {
            List<HostVO> hostsAfter;

            try {
                hostsAfter = filter.filterHostCandidates(new ArrayList<>(hostsBefore), spec);
            } catch (OperationFailureException e) {
                fail(e.getErrorCode());
                return;
            }

            logger.debug(String.format("after HostAllocatorFilterExtensionPoint[%s], candidates num: %s -> %s",
                    filter.getClass().getSimpleName(), hostsBefore.size(), hostsAfter.size()));
            if (hostsBefore.size() != hostsAfter.size()) {
                Set<String> set = transformToSet(hostsBefore, HostVO::getUuid);
                set.removeAll(transform(hostsAfter, HostVO::getUuid));
                set.forEach(uuid -> reject(uuidCandidateMap.get(uuid), filter.getClass().getSimpleName()));
            }

            if (hostsAfter.isEmpty()) {
                fail(Platform.operr("after filtering, HostAllocatorFilterExtensionPoint[%s] returns zero candidate host, it means: %s", filter.getClass().getSimpleName(), filter.filterErrorReason()));
                return;
            }

            hostsBefore = hostsAfter;
        }

        next();
    }
}

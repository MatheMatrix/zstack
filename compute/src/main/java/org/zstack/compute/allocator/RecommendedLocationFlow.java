package org.zstack.compute.allocator;

import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.cluster.ClusterVO;
import org.zstack.header.host.HostVO;
import org.zstack.header.vm.VmLocationSpec;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import static org.zstack.utils.CollectionUtils.*;

/**
 * Created by Wenhao.Zhang on 2024/12/17
 */
public class RecommendedLocationFlow extends AbstractHostAllocatorFlow {
    private final CLogger logger = Utils.getLogger(RecommendedLocationFlow.class);

    @Override
    public void allocate() {
        throwExceptionIfIAmTheFirstFlow();

        if (spec.isListAllHosts()) {
            next(candidates);
            return;
        }

        final ArrayList<HostVO> list1 = new ArrayList<>(candidates);
        final ArrayList<HostVO> list2 = new ArrayList<>();

        filterRecommendedHosts(list1, list2);
        if (list2.isEmpty()) {
            list2.addAll(list1);
        }

        list1.clear();
        filterOutNotRecommendedHosts(list2, list1);
        if (list1.isEmpty()) {
            list1.addAll(list2);
        }

        list2.clear();
        filterRecommendedClusters(list1, list2);
        if (list2.isEmpty()) {
            list2.addAll(list1);
        }

        list1.clear();
        filterOutNotRecommendedClusters(list2, list1);
        if (list1.isEmpty()) {
            next(list2);
        } else {
            next(list1);
        }
    }

    private void filterRecommendedHosts(ArrayList<HostVO> from, ArrayList<HostVO> to) {
        final Set<String> uuidSet = spec.getLocationSpecs().stream()
                .filter(spec -> HostVO.class.getSimpleName().equals(spec.getResourceType()))
                .filter(VmLocationSpec::recommended)
                .flatMap(spec -> spec.getUuids().stream())
                .collect(Collectors.toSet());
        for (String uuid : uuidSet) {
            HostVO host = findOneOrNull(from, h -> h.getUuid().equals(uuid));
            if (host != null) {
                to.add(host);
            }
        }
    }

    private void filterOutNotRecommendedHosts(ArrayList<HostVO> from, ArrayList<HostVO> to) {
        final Set<String> uuidSet = spec.getLocationSpecs().stream()
                .filter(spec -> HostVO.class.getSimpleName().equals(spec.getResourceType()))
                .filter(VmLocationSpec::notRecommended)
                .flatMap(spec -> spec.getUuids().stream())
                .collect(Collectors.toSet());
        if (isEmpty(uuidSet)) {
            return;
        }

        for (HostVO host : from) {
            if (!uuidSet.contains(host.getUuid())) {
                to.add(host);
            }
        }
    }

    private void filterRecommendedClusters(ArrayList<HostVO> from, ArrayList<HostVO> to) {
        final Set<String> uuidSet = spec.getLocationSpecs().stream()
                .filter(spec -> ClusterVO.class.getSimpleName().equals(spec.getResourceType()))
                .filter(VmLocationSpec::recommended)
                .flatMap(spec -> spec.getUuids().stream())
                .collect(Collectors.toSet());
        for (String uuid : uuidSet) {
            to.addAll(filter(from, h -> h.getClusterUuid().equals(uuid)));
        }
    }

    private void filterOutNotRecommendedClusters(ArrayList<HostVO> from, ArrayList<HostVO> to) {
        final Set<String> uuidSet = spec.getLocationSpecs().stream()
                .filter(spec -> ClusterVO.class.getSimpleName().equals(spec.getResourceType()))
                .filter(VmLocationSpec::notRecommended)
                .flatMap(spec -> spec.getUuids().stream())
                .collect(Collectors.toSet());
        if (isEmpty(uuidSet)) {
            return;
        }

        for (HostVO host : from) {
            if (!uuidSet.contains(host.getClusterUuid())) {
                to.add(host);
            }
        }
    }
}

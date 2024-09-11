package org.zstack.compute.allocator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.Platform;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.header.allocator.AbstractHostAllocatorFlow;
import org.zstack.header.host.HostVO;
import org.zstack.header.host.HostVO_;
import org.zstack.header.network.l2.*;
import org.zstack.header.network.l3.L3NetworkInventory;

import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import java.util.*;
import java.util.stream.Collectors;

import org.zstack.network.l2.L2NetworkHostUtils;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.Utils;


@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class AttachedL2NetworkAllocatorFlow extends AbstractHostAllocatorFlow {
    private static final CLogger logger = Utils.getLogger(AttachedL2NetworkAllocatorFlow.class);
    private static final Logger log = LoggerFactory.getLogger(AttachedL2NetworkAllocatorFlow.class);

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private PluginRegistry pluginRgty;

    @Transactional(readOnly = true)
    private List<HostVO> allocate(Collection<String> l3NetworkUuids, Collection<String> hostUuids) {
        String sql = "select l3.l2NetworkUuid from L3NetworkVO l3 where l3.uuid in (:l3uuids)";
        TypedQuery<String> q = dbf.getEntityManager().createQuery(sql, String.class);
        q.setParameter("l3uuids", l3NetworkUuids);
        List<String> l2uuids = q.getResultList();
        if (l2uuids.isEmpty()) {
            return new ArrayList<>();
        }

        sql = "select ref from L2NetworkClusterRefVO ref where ref.l2NetworkUuid in (:l2uuids)";
        TypedQuery<L2NetworkClusterRefVO> rq = dbf.getEntityManager().createQuery(sql, L2NetworkClusterRefVO.class);
        rq.setParameter("l2uuids", l2uuids);
        List<L2NetworkClusterRefVO> refs = rq.getResultList();
        if (refs.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Set<String>> l2ClusterMap = new HashMap<>();
        for (L2NetworkClusterRefVO ref : refs) {
            Set<String> l2s = l2ClusterMap.computeIfAbsent(ref.getClusterUuid(), k -> new HashSet<>());
            l2s.add(ref.getL2NetworkUuid());
        }

        Set<String> clusterUuids = new HashSet<>();
        for (Map.Entry<String, Set<String>> e : l2ClusterMap.entrySet()) {
            if (e.getValue().containsAll(l2uuids)) {
                clusterUuids.add(e.getKey());
            }
        }

        if (clusterUuids.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> retHostUuids;
        if (hostUuids.isEmpty()) {
            retHostUuids = Q.New(HostVO.class).select(HostVO_.uuid)
                    .in(HostVO_.clusterUuid, clusterUuids).listValues();
        } else {
            retHostUuids = Q.New(HostVO.class).select(HostVO_.uuid)
                    .in(HostVO_.clusterUuid, clusterUuids)
                    .in(HostVO_.uuid, hostUuids).listValues();
        }

        if (retHostUuids.isEmpty()){
            return new ArrayList<>();
        }

        /* in normal case, there is no L2NetworkHostRefVO  */
        List<Tuple> tuples = Q.New(L2NetworkVO.class)
                .select(L2NetworkVO_.uuid, L2NetworkVO_.type)
                .in(L2NetworkVO_.uuid, l2uuids)
                .listTuple();

        List<String> notAttachToAllHostsL2s = new ArrayList<>();
        for (Tuple t : tuples) {
            if (!L2NetworkType.valueOf(t.get(1, String.class)).isAttachToAllHosts()) {
                notAttachToAllHostsL2s.add(t.get(0, String.class));
            }
        }
        List<String> excludeHostUuids = L2NetworkHostUtils.getExcludeHostUuids(notAttachToAllHostsL2s, retHostUuids);
        retHostUuids.removeAll(excludeHostUuids);
        if (retHostUuids.isEmpty()){
            return new ArrayList<>();
        }

        sql = "select h from HostVO h where h.uuid in (:huuids)";
        TypedQuery<HostVO> hq = dbf.getEntityManager().createQuery(sql, HostVO.class);
        hq.setParameter("huuids", retHostUuids);

        if (usePagination()) {
            hq.setFirstResult(paginationInfo.getOffset());
            hq.setMaxResults(paginationInfo.getLimit());
        }

        return hq.getResultList();
    }

    @Override
    public void allocate() {
        if (spec.getL3NetworkUuids().isEmpty()) {
            String sql;
            Set<String> clusterUuids = new HashSet<>();
            clusterUuids.add(spec.getVmInstance().getClusterUuid());
            List<String> hostUuids = null;
            if (!amITheFirstFlow()) {
                hostUuids = candidates.stream().map(HostVO::getUuid).collect(Collectors.toList());
            }

            if (hostUuids == null || hostUuids.isEmpty()) {
                TypedQuery<HostVO> hq;
                if (clusterUuids.isEmpty()) {
                    sql = "select h from HostVO h";
                    hq = dbf.getEntityManager().createQuery(sql, HostVO.class);
                } else {
                    sql = "select h from HostVO h where h.clusterUuid in (:cuuids)";
                    hq = dbf.getEntityManager().createQuery(sql, HostVO.class);
                    hq.setParameter("cuuids", clusterUuids);
                }

                if (usePagination()) {
                    hq.setFirstResult(paginationInfo.getOffset());
                    hq.setMaxResults(paginationInfo.getLimit());
                }
                candidates = hq.getResultList();
            } else {
                sql = "select h from HostVO h where h.clusterUuid in (:cuuids) and h.uuid in (:huuids)";
                TypedQuery<HostVO> hq = dbf.getEntityManager().createQuery(sql, HostVO.class);
                hq.setParameter("cuuids", clusterUuids);
                hq.setParameter("huuids", hostUuids);

                if (usePagination()) {
                    hq.setFirstResult(paginationInfo.getOffset());
                    hq.setMaxResults(paginationInfo.getLimit());
                }
                candidates = hq.getResultList();
            }

            next(candidates);
            logger.debug(String.format("vm clusteruuid:%s, candidates size:%s", spec.getVmInstance().getClusterUuid(), candidates.size()));
            return;
        }

        List<String> l3Uuids = spec.getL3NetworkUuids();
        List<L3NetworkInventory> serviceL3s = new ArrayList<>();
        for (GetL3NetworkForVmNetworkService extp : pluginRgty.getExtensionList(GetL3NetworkForVmNetworkService.class)) {
            serviceL3s.addAll(extp.getL3NetworkForVmNetworkService(spec.getVmInstance()));
        }
        if (!serviceL3s.isEmpty()) {
            l3Uuids.addAll(serviceL3s.stream().map(L3NetworkInventory::getUuid).distinct().collect(Collectors.toList()));
        }


        if (amITheFirstFlow()) {
            candidates = allocate(l3Uuids, new ArrayList<>());
        } else {
            candidates = allocate(l3Uuids, getHostUuidsFromCandidates());
        }

        if (candidates.isEmpty()) {
            fail(Platform.operr("no host found in clusters that has attached to L2Networks which have L3Networks%s", spec.getL3NetworkUuids()));
        } else {
            next(candidates);
        }
    }
}

package org.zstack.compute.allocator;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.config.GlobalConfigVO;
import org.zstack.core.config.GlobalConfigVO_;
import org.zstack.core.db.Q;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.header.allocator.*;
import org.zstack.header.candidate.*;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.host.HostInventory;
import org.zstack.header.host.HostVO;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.utils.DebugUtils;
import org.zstack.utils.SizeUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import java.util.*;
import java.util.stream.Collectors;

import static org.zstack.core.Platform.err;
import static org.zstack.core.Platform.inerr;
import static org.zstack.utils.clouderrorcode.CloudOperationsErrorCode.*;

/**
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class HostAllocatorChain implements HostAllocatorTrigger, HostAllocatorStrategy {
    private static final CLogger logger = Utils.getLogger(HostAllocatorChain.class);

    private HostAllocatorSpec allocationSpec;
    private String name;
    private List<AbstractHostAllocatorFlow> flows;

    private Iterator<AbstractHostAllocatorFlow> it;
    private ErrorCode errorCode;

    private List<HostVO> result = null;
    private boolean isDryRun;
    private ReturnValueCompletion<List<HostInventory>> completion;
    private ReturnValueCompletion<List<HostInventory>> dryRunCompletion;

    private int skipCounter = 0;

    private AbstractHostAllocatorFlow lastFlow;
    private List<HostVO> lastFlowInput;

    private HostAllocationPaginationInfo paginationInfo;

    private Set<ErrorCode> seriesErrorWhenPagination = new HashSet<>();
    private CandidateDecisionRecorder<HostInventory> decisionRecorder;

    @Autowired
    private ErrorFacade errf;
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private HostCapacityOverProvisioningManager ratioMgr;
    @Autowired
    private HostCandidateUniverseBuilder hostCandidateUniverseBuilder;

    public HostAllocatorSpec getAllocationSpec() {
        return allocationSpec;
    }

    public void setAllocationSpec(HostAllocatorSpec allocationSpec) {
        this.allocationSpec = allocationSpec;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<AbstractHostAllocatorFlow> getFlows() {
        return flows;
    }

    public void setFlows(List<AbstractHostAllocatorFlow> flows) {
        this.flows = flows;
    }

    private void done() {
        finishCandidateDecision();
        if (result == null) {
            if (isDryRun) {
                if (HostAllocatorError.NO_AVAILABLE_HOST.toString().equals(errorCode.getCode())) {
                    dryRunCompletion.success(new ArrayList<HostInventory>());
                } else {
                    dryRunCompletion.fail(errorCode);
                }
            } else {
                completion.fail(errorCode);
            }
            return;
        }

        // in case a wrong flow returns an empty result set
        if (result.isEmpty()) {
            if (isDryRun) {
                dryRunCompletion.fail(err(ORG_ZSTACK_COMPUTE_ALLOCATOR_10017, HostAllocatorError.NO_AVAILABLE_HOST,
                        "host allocation flow doesn't indicate any details"));
            } else {
                completion.fail(err(ORG_ZSTACK_COMPUTE_ALLOCATOR_10018, HostAllocatorError.NO_AVAILABLE_HOST,
                        "host allocation flow doesn't indicate any details"));
            }
            return;
        }

        if (isDryRun) {
            dryRunCompletion.success(HostInventory.valueOf(result));
        } else {
            completion.success(HostInventory.valueOf(result));
        }
    }

    private boolean initCandidateDecision() {
        if (!allocationSpec.isCandidateDecisionEnabled()) {
            return true;
        }

        decisionRecorder = new CandidateDecisionRecorder<>(allocationSpec.getCandidateDecisionContext());
        List<HostVO> universe = hostCandidateUniverseBuilder.build(allocationSpec, allocationSpec.getCandidateDecisionContext());
        decisionRecorder.registerHostUniverse(universe);
        result = universe;

        if (!universe.isEmpty()) {
            return true;
        }

        allocationSpec.setCandidateDecisionResult(decisionRecorder.build());
        ErrorCode errCode = err(ORG_ZSTACK_COMPUTE_ALLOCATOR_10018, HostAllocatorError.NO_AVAILABLE_HOST,
                "no visible host in candidate universe");
        errCode.withOpaque("candidateDecisionResult", allocationSpec.getCandidateDecisionResult());
        if (isDryRun) {
            dryRunCompletion.success(new ArrayList<HostInventory>());
        } else {
            completion.fail(errCode);
        }
        return false;
    }

    private boolean isDecisionRecorderEnabled() {
        return decisionRecorder != null && decisionRecorder.isEnabled();
    }

    private void finishCandidateDecision() {
        if (!isDecisionRecorderEnabled()) {
            return;
        }

        if (result != null) {
            decisionRecorder.markFinalCandidates(result.stream().map(HostVO::getUuid).collect(Collectors.toList()));
        }

        CandidateDecisionResult candidateDecisionResult = decisionRecorder.build();
        allocationSpec.setCandidateDecisionResult(candidateDecisionResult);
        if (errorCode != null) {
            errorCode.withOpaque("candidateDecisionResult", candidateDecisionResult);
        }
    }

    private void recordFlowDiff(List<HostVO> before, List<HostVO> after, AbstractHostAllocatorFlow flow) {
        if (!isDecisionRecorderEnabled() || before == null) {
            return;
        }

        Set<String> afterUuids = after == null ? Collections.emptySet() :
                after.stream().map(HostVO::getUuid).collect(Collectors.toSet());
        for (HostVO host : before) {
            if (!afterUuids.contains(host.getUuid())) {
                decisionRecorder.rejectIfNotRejected(host.getUuid(), fallbackReasonForFlow(flow));
            }
        }
    }

    private void recordFlowFailure(ErrorCode errCode) {
        if (!isDecisionRecorderEnabled()) {
            return;
        }

        List<HostVO> active = result != null ? result : lastFlowInput;
        if (active == null) {
            return;
        }

        CandidateRejectReason reason = fallbackReasonForFlow(lastFlow);
        if (errCode != null && errCode.getDetails() != null) {
            reason.setMessage(errCode.getDetails());
        }
        for (HostVO host : active) {
            decisionRecorder.rejectIfNotRejected(host.getUuid(), reason);
        }
    }

    private CandidateRejectReason fallbackReasonForFlow(AbstractHostAllocatorFlow flow) {
        String code = CandidateReasonCodes.HOST_REJECTED_BY_ALLOCATOR_FLOW;
        String category = CandidateCategories.UNKNOWN;
        String stage = "allocator";
        String message = "host was rejected by allocator flow";

        if (flow instanceof HostStateAndHypervisorAllocatorFlow) {
            category = CandidateCategories.STATUS;
            stage = "status";
        } else if (flow instanceof HostCapacityAllocatorFlow) {
            category = CandidateCategories.CAPACITY;
            stage = "capacity";
        } else if (flow instanceof HostPrimaryStorageAllocatorFlow) {
            code = CandidateReasonCodes.HOST_PRIMARY_STORAGE_REQUIREMENT_NOT_SATISFIED;
            category = CandidateCategories.STORAGE;
            stage = "storage";
            message = "host does not satisfy primary storage requirement";
        } else if (flow instanceof AvoidHostAllocatorFlow || flow instanceof FilterFlow) {
            category = CandidateCategories.POLICY;
            stage = "policy";
        }

        return CandidateRejectReason.of(code, category, message)
                .detail("stage", stage)
                .detail("checker", flow == null ? null : flow.getClass().getSimpleName());
    }

    private void startOver() {
        it = flows.iterator();
        result = null;
        skipCounter = 0;
        runFlow(it.next());
    }

    private void runFlow(AbstractHostAllocatorFlow flow) {
        try {
            lastFlow = flow;
            lastFlowInput = result == null ? null : new ArrayList<>(result);
            flow.setCandidates(result);
            flow.setSpec(allocationSpec);
            flow.setTrigger(this);
            flow.setPaginationInfo(paginationInfo);
            flow.allocate();
        } catch (OperationFailureException ofe) {
            if (ofe.getErrorCode().getCode().equals(HostAllocatorConstant.PAGINATION_INTERMEDIATE_ERROR.getCode())) {
                logger.debug(String.format("[Host Allocation]: intermediate failure; " +
                                "because of pagination, will start over allocation again; " +
                                "current pagination info %s; failure details: %s",
                        JSONObjectUtil.toJsonString(paginationInfo), ofe.getErrorCode().getDetails()));
                seriesErrorWhenPagination.add(ofe.getErrorCode());
                startOver();
            } else {
                fail(ofe.getErrorCode());
            }
        } catch (Throwable t) {
            logger.warn("unhandled throwable", t);
            String errMsg = t != null ? t.toString() : "unknown error";
            if (isDryRun) {
                if (dryRunCompletion != null) {
                    dryRunCompletion.fail(inerr(ORG_ZSTACK_COMPUTE_ALLOCATOR_10019, errMsg));
                }
            } else if (completion != null) {
                completion.fail(inerr(ORG_ZSTACK_COMPUTE_ALLOCATOR_10019, errMsg));
            }
        }
    }

    private void start() {
        for (HostAllocatorPreStartExtensionPoint processor : pluginRgty.getExtensionList(HostAllocatorPreStartExtensionPoint.class)) {
            processor.beforeHostAllocatorStart(allocationSpec, flows);
        }

        if (HostAllocatorGlobalConfig.USE_PAGINATION.value(Boolean.class)) {
            if (!allocationSpec.isCandidateDecisionEnabled()) {
                paginationInfo = new HostAllocationPaginationInfo();
                paginationInfo.setLimit(HostAllocatorGlobalConfig.PAGINATION_LIMIT.value(Integer.class));
            }
        }
        if (!initCandidateDecision()) {
            return;
        }
        it = flows.iterator();
        DebugUtils.Assert(it.hasNext(), "can not run an empty host allocation chain");
        runFlow(it.next());
    }

    private void allocate(ReturnValueCompletion<List<HostInventory>> completion) {
        isDryRun = false;
        this.completion = completion;
        start();
    }

    private void dryRun(ReturnValueCompletion<List<HostInventory>> completion) {
        isDryRun = true;
        this.dryRunCompletion = completion;
        start();
    }

    @Override
    public void next(List<HostVO> candidates) {
        DebugUtils.Assert(candidates != null, "cannot pass null to next() method");
        DebugUtils.Assert(!candidates.isEmpty(), "cannot pass empty candidates to next() method");
        recordFlowDiff(lastFlowInput, candidates, lastFlow);
        result = candidates;
        VmInstanceInventory vm = allocationSpec.getVmInstance();
        logger.debug(String.format("[Host Allocation]: flow[%s] successfully found %s candidate hosts for vm[uuid:%s, name:%s]",
                lastFlow.getClass().getName(), result.size(), vm.getUuid(), vm.getName()));
        if (logger.isTraceEnabled()) {
            StringBuilder sb = new StringBuilder("[Host Allocation Details]:");
            for (HostVO vo : result) {
                sb.append(String.format("\ncandidate host[name:%s, uuid:%s, zoneUuid:%s, clusterUuid:%s, hypervisorType:%s]",
                        vo.getName(), vo.getUuid(), vo.getZoneUuid(), vo.getClusterUuid(), vo.getHypervisorType()));
            }
            logger.trace(sb.toString());
        }

        if (it.hasNext()) {
            runFlow(it.next());
            return;
        }

        done();
    }

    @Override
    public void skip() {
        logger.debug(String.format("[Host Allocation]: flow[%s] asks to skip itself, we are running to the next flow",
                lastFlow.getClass()));
        if (it.hasNext()) {
            if (isFirstFlow(lastFlow)) {
                skipCounter++;
            }
            runFlow(it.next());
            return;
        }

        done();
    }

    @Override
    public boolean isFirstFlow(AbstractHostAllocatorFlow flow) {
        return flows.indexOf(flow) == skipCounter;
    }

    @Override
    public void fail(ErrorCode errorCode) {
        if (seriesErrorWhenPagination.isEmpty()) {
            logger.debug(String.format("[Host Allocation] flow[%s] failed to allocate host; %s",
                    lastFlow.getClass().getName(), errorCode.getDetails()));
            this.errorCode = errorCode;
        } else {
            this.errorCode = err(ORG_ZSTACK_COMPUTE_ALLOCATOR_10020, HostAllocatorError.NO_AVAILABLE_HOST, seriesErrorWhenPagination.iterator().next(), "unable to allocate hosts; due to pagination is enabled, " +
                    "there might be several allocation failures happened before;" +
                    " the error list is %s", seriesErrorWhenPagination.stream().map(ErrorCode::getDetails).collect(Collectors.toList()));
            logger.debug(this.errorCode.getDetails());
        }

        recordFlowFailure(this.errorCode);
        result = null;
        done();
    }

    @Override
    public boolean isCandidateDecisionEnabled() {
        return isDecisionRecorderEnabled();
    }

    @Override
    public void pass(String hostUuid) {
        if (isDecisionRecorderEnabled()) {
            decisionRecorder.pass(hostUuid);
        }
    }

    @Override
    public void reject(String hostUuid, CandidateRejectReason reason) {
        if (isDecisionRecorderEnabled()) {
            decisionRecorder.reject(hostUuid, reason);
        }
    }

    @Override
    public void rejectIfNotRejected(String hostUuid, CandidateRejectReason reason) {
        if (isDecisionRecorderEnabled()) {
            decisionRecorder.rejectIfNotRejected(hostUuid, reason);
        }
    }

    @Override
    public void allocate(HostAllocatorSpec spec, ReturnValueCompletion<List<HostInventory>> completion) {
        this.allocationSpec = spec;
        allocate(completion);
    }

    @Override
    public void dryRun(HostAllocatorSpec spec, ReturnValueCompletion<List<HostInventory>> completion) {
        this.allocationSpec = spec;
        dryRun(completion);
    }
}

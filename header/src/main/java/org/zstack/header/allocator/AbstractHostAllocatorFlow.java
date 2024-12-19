package org.zstack.header.allocator;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.host.HostVO;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.List;

import static org.zstack.utils.CollectionUtils.isEmpty;
import static org.zstack.utils.CollectionUtils.transform;

/**
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public abstract class AbstractHostAllocatorFlow {
    private static final CLogger logger = Utils.getLogger(AbstractHostAllocatorFlow.class);
    protected List<HostCandidate> candidates;
    protected List<HostVO> newcomers;
    protected HostAllocatorSpec spec;
    private HostAllocatorTrigger trigger;
    protected HostAllocationPaginationInfo paginationInfo;

    public abstract void allocate();

    public List<HostCandidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<HostCandidate> candidates) {
        this.candidates = candidates;
    }

    // no setter
    public List<HostVO> getNewcomers() {
        return newcomers;
    }

    public HostAllocatorSpec getSpec() {
        return spec;
    }

    public void setSpec(HostAllocatorSpec spec) {
        this.spec = spec;
    }

    public void setTrigger(HostAllocatorTrigger trigger) {
        this.trigger = trigger;
    }

    public HostAllocationPaginationInfo getPaginationInfo() {
        return paginationInfo;
    }

    public void setPaginationInfo(HostAllocationPaginationInfo paginationInfo) {
        this.paginationInfo = paginationInfo;
    }

    protected void next() {
        if (usePagination()) {
            paginationInfo.setOffset(paginationInfo.getOffset() + paginationInfo.getLimit());
        }
        if (!isEmpty(newcomers)) {
            trigger.push(newcomers);
            newcomers.clear();
        }
        trigger.next();
    }

    protected void allocatorTriggerFail(ErrorCode errorCode) {
        trigger.fail(errorCode);
    }

    protected void skip() {
        trigger.skip();
    }

    protected void fail(ErrorCode reason) {
        if (paginationInfo == null || trigger.isFirstFlow(this)) {
            throw new OperationFailureException(reason);
        }

        ErrorCode errorCode = new ErrorCode();
        errorCode.setCode(HostAllocatorConstant.PAGINATION_INTERMEDIATE_ERROR.getCode());
        errorCode.setDetails("no host meet the requirements (in pagination, a middle flow fails)");
        errorCode.setDescription(HostAllocatorConstant.PAGINATION_INTERMEDIATE_ERROR.getDescription());
        errorCode.setCause(reason);
        throw new OperationFailureException(errorCode);
    }

    protected void recommend(HostCandidate candidate) {
        candidate.markAsRecommended(getClass().getSimpleName());
        logger.debug(String.format("%s recommend host[%s]", getClass().getSimpleName(), candidate.getUuid()));
    }

    protected void notRecommend(HostCandidate candidate) {
        candidate.markAsNotRecommended(getClass().getSimpleName());
        logger.debug(String.format("%s does not recommend host[%s]", getClass().getSimpleName(), candidate.getUuid()));
    }

    protected void reject(HostCandidate candidate, String reason) {
        candidate.markAsRejected(getClass().getSimpleName(), reason);
        logger.debug(String.format("%s reject host[%s]: %s", candidate.rejectBy, candidate.getUuid(), candidate.reject));
    }

    protected void rejectAll(String reason) {
        candidates.forEach(c -> reject(c, reason));
    }

    protected void accept(List<HostVO> hosts) {
        newcomers = (newcomers == null) ? new ArrayList<>() : newcomers;
        newcomers.addAll(hosts);
    }

    protected boolean usePagination() {
        return paginationInfo != null && trigger.isFirstFlow(this);
    }

    protected void throwExceptionIfIAmTheFirstFlow() {
        if (candidates == null || candidates.isEmpty()) {
            throw new CloudRuntimeException(String.format("%s cannot be the first flow in the allocation chain",
                    this.getClass().getName()));
        }
    }

    protected boolean amITheFirstFlow() {
        return candidates == null;
    }

    protected List<String> allHostUuidList() {
        return transform(candidates, HostCandidate::getUuid);
    }
}

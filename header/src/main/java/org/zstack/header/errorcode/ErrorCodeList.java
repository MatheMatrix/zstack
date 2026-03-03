package org.zstack.header.errorcode;

import org.apache.commons.collections.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by xing5 on 2016/4/19.
 */
public class ErrorCodeList extends ErrorCode {
    private List<ErrorCode> causes = Collections.synchronizedList(new ArrayList<>());

    public List<ErrorCode> getCauses() {
        return causes;
    }

    public void setCauses(List<ErrorCode> causes) {
        this.causes = causes;
    }


    /**
     *
     * @param t
     * @return if the causes of the two objects both have no value,
     * even the other object is not ErrorCodeList but ErrorCode,
     * return true.
     * if not compare their values.
     */

    @Override
    public boolean equals(Object t) {
        if(!super.equals(t)){
            return false;
        }

        if(t instanceof ErrorCodeList){
            ErrorCodeList other = (ErrorCodeList)t;
            if((this.causes == other.causes || this.causes == null && other.causes.isEmpty()) || other.causes == null && this.causes.isEmpty()){
                return true;
            } else if(causes != null && other.causes != null){
                return this.causes.size() == other.causes.size() && this.causes.containsAll(other.causes);
            } else {
                return false;
            }
        } else if (t instanceof ErrorCode){
            return causes == null || causes.isEmpty();
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        StringBuffer sb = new StringBuffer();
        sb.append(getCode() == null ? "" : getCode());
        sb.append(getDetails() == null ? "" : getDetails());
        sb.append(getOpaque() == null ? "" : getOpaque());
        sb.append(getCause() == null ? "" : getCause().toString());
        sb.append(causes == null || causes.isEmpty() ? "" : causes.toString());
        return sb.toString().hashCode();
    }

    @Override
    public String getReadableDetails() {
        ErrorCodeList root = this;
        StringBuffer errorBuf = new StringBuffer();
        if (CollectionUtils.isNotEmpty(root.causes)) {
            root.causes.forEach(cause -> {
                if (errorBuf.length() > 0) {
                    errorBuf.append(",");
                }
                errorBuf.append(getReadableDetails(cause));
            });
            return errorBuf.toString().trim();
        }

        return super.getReadableDetails();
    }

    private String getReadableDetails(ErrorCode errCode) {
        return errCode.getReadableDetails();
    }

    /**
     * Returns the root cause error by following the first cause chain.
     * If the first cause is itself an ErrorCodeList, recursively follows
     * its first cause up to a maximum depth of 5 levels.
     *
     * @return the deepest first-cause ErrorCode, or this ErrorCodeList if no causes exist
     */
    public ErrorCode getRootCause() {
        return getRootCause(0);
    }

    private ErrorCode getRootCause(int depth) {
        if (causes == null || causes.isEmpty()) {
            return this;
        }
        ErrorCode first = causes.get(0);
        if (depth < 5 && first instanceof ErrorCodeList) {
            return ((ErrorCodeList) first).getRootCause(depth + 1);
        }
        return first;
    }
}

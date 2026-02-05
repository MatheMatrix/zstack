package org.zstack.network.l3;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.cascade.AbstractAsyncCascadeExtension;
import org.zstack.core.cascade.CascadeAction;
import org.zstack.core.cascade.CascadeConstant;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.Q;
import org.zstack.core.db.SQL;
import org.zstack.header.core.Completion;
import org.zstack.header.network.l3.*;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.function.Function;
import org.zstack.utils.logging.CLogger;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class UsedIpCascadeExtension extends AbstractAsyncCascadeExtension {
    private static final CLogger logger = Utils.getLogger(UsedIpCascadeExtension.class);

    @Autowired
    private DatabaseFacade dbf;

    private static final String NAME = UsedIpVO.class.getSimpleName();

    @Override
    public void asyncCascade(CascadeAction action, Completion completion) {
        if (action.isActionCode(CascadeConstant.DELETION_CHECK_CODE)) {
            handleDeletionCheck(action, completion);
        } else if (action.isActionCode(CascadeConstant.DELETION_DELETE_CODE, CascadeConstant.DELETION_FORCE_DELETE_CODE)) {
            handleDeletion(action, completion);
        } else {
            completion.success();
        }
    }

    private void handleDeletionCheck(CascadeAction action, Completion completion) {
        completion.success();
    }

    private void handleDeletion(CascadeAction action, Completion completion) {
        List<UsedIpInventory> invs = usedIpFromAction(action);
        if (invs == null || invs.isEmpty()) {
            completion.success();
            return;
        }

        List<String> uuids = invs.stream().map(UsedIpInventory::getUuid).collect(Collectors.toList());
        SQL.New(UsedIpVO.class).in(UsedIpVO_.uuid, uuids).hardDelete();
        completion.success();
    }

    @Override
    public List<String> getEdgeNames() {
        return Collections.singletonList(IpRangeVO.class.getSimpleName());
    }

    @Override
    public String getCascadeResourceName() {
        return NAME;
    }

    private List<UsedIpInventory> usedIpFromAction(CascadeAction action) {
        if (IpRangeVO.class.getSimpleName().equals(action.getParentIssuer())) {
            List<String> ipRangeUuids = CollectionUtils.transformToList(
                    (List<IpRangeInventory>) action.getParentIssuerContext(),
                    new Function<String, IpRangeInventory>() {
                        @Override
                        public String call(IpRangeInventory arg) {
                            return arg.getUuid();
                        }
                    }
            );

            List<UsedIpVO> vos = Q.New(UsedIpVO.class)
                    .in(UsedIpVO_.ipRangeUuid, ipRangeUuids)
                    .list();
            if (!vos.isEmpty()) {
                return UsedIpInventory.valueOf(vos);
            }
        } else if (NAME.equals(action.getParentIssuer())) {
            return action.getParentIssuerContext();
        }

        return null;
    }

    @Override
    public CascadeAction createActionForChildResource(CascadeAction action) {
        if (CascadeConstant.DELETION_CODES.contains(action.getActionCode())) {
            List<UsedIpInventory> ctx = usedIpFromAction(action);
            if (ctx != null) {
                return action.copy().setParentIssuer(NAME).setParentIssuerContext(ctx);
            }
        }

        return null;
    }
}




package org.zstack.identity.rbac;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.Q;
import org.zstack.header.apimediator.ApiMessageInterceptionException;
import org.zstack.header.apimediator.ApiMessageInterceptor;
import org.zstack.header.identity.role.RoleIdentity;
import org.zstack.header.identity.role.RoleType;
import org.zstack.header.identity.role.RoleVO;
import org.zstack.header.identity.role.RoleVO_;
import org.zstack.header.identity.role.api.APICreateRoleMsg;
import org.zstack.header.identity.role.api.APIDeleteRoleMsg;
import org.zstack.header.identity.role.api.APIUpdateRoleMsg;
import org.zstack.header.message.APIMessage;

import static org.zstack.core.Platform.argerr;
import static org.zstack.utils.CollectionDSL.list;

public class RBACApiInterceptor implements ApiMessageInterceptor {
    @Autowired
    private PluginRegistry pluginRgty;

    @Override
    public APIMessage intercept(APIMessage msg) throws ApiMessageInterceptionException {
        if (msg instanceof APIDeleteRoleMsg) {
            validate((APIDeleteRoleMsg) msg);
        } else if (msg instanceof APIUpdateRoleMsg) {
            validate((APIUpdateRoleMsg) msg);
        } else if (msg instanceof APICreateRoleMsg) {
            validate((APICreateRoleMsg) msg);
        } 

        return msg;
    }

    private void validate(APICreateRoleMsg msg) {
        if (msg.getIdentity() == null){
            return;
        }

        RoleIdentity roleIdentity = RoleIdentity.valueOf(msg.getIdentity());

        if (msg.getStatements() == null) {
            return;
        }

        roleIdentity.getRoleIdentityValidators().forEach(validator -> validator.validateRolePolicy(roleIdentity, msg.getStatements()));
    }

    private void validate(APIUpdateRoleMsg msg) {
        if (Q.New(RoleVO.class).in(RoleVO_.type, list(RoleType.Predefined, RoleType.System)).eq(RoleVO_.uuid, msg.getUuid()).isExists()) {
            throw new ApiMessageInterceptionException(argerr("cannot update a system or predefined role"));
        }

        RoleVO vo = Q.New(RoleVO.class).eq(RoleVO_.uuid, msg.getRoleUuid()).find();

        if (vo.getIdentity() == null || msg.getStatements() == null) {
            return;
        }

        RoleIdentity roleIdentity = RoleIdentity.valueOf(vo.getIdentity());
        roleIdentity.getRoleIdentityValidators().forEach(validator -> validator.validateRolePolicy(roleIdentity, msg.getStatements()));
    }


    private void validate(APIDeleteRoleMsg msg) {
        if (Q.New(RoleVO.class).in(RoleVO_.type, list(RoleType.Predefined, RoleType.System)).eq(RoleVO_.uuid, msg.getUuid()).isExists()) {
            throw new ApiMessageInterceptionException(argerr("cannot delete a system or predefined role"));
        }
    }
}

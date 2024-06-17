package org.zstack.header.identity.login;

import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.identity.LoginType;

import java.util.*;

/**
 * Login implementation interface.
 *
 * Used for support a new kind of login, including authentication, user information and
 * support involve several additional authentication features, like captcha,
 * authentication code and so on.
 */
public interface LoginBackend {
    LoginType getLoginType();

    void login(LoginContext loginContext, ReturnValueCompletion<LoginSessionInfo> completion);

    boolean authenticate(String username, String password);

    String getUserIdByName(String username);

    void collectUserInfoIntoContext(LoginContext loginContext);

    List<AdditionalAuthFeature> getRequiredAdditionalAuthFeature();

    default Set<String> possibleUserUuidSetForGettingProcedures(LoginContext loginContext) {
        return Collections.emptySet();
    }

    default Map<String, Object> generateJwtTokenClaims(LoginContext loginContext, LoginSessionInfo info) {
        return new HashMap<>();
    }
}

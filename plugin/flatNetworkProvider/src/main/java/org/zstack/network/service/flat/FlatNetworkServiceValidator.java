package org.zstack.network.service.flat;

import org.zstack.core.ScatteredValidator;
import org.zstack.header.apimediator.StopRoutingException;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * @ Author : yh.w
 * @ Date   : Created in 10:43 2021/10/26
 */
public class FlatNetworkServiceValidator extends ScatteredValidator {
    private static final List<Method> methods;

    static {
        // method signature: static void xxx(String hostUuid)
        methods = Collections.unmodifiableList(collectValidatorMethods(FlatNetworkServiceValidatorMethod.class, String.class));
    }

    public boolean validate(String hostUuid) {
        try {
            invokeValidatorMethods(methods, hostUuid);
        } catch (SkipApplyFlatNetworkServiceException e) {
            return true;
        }

        return false;
    }
}

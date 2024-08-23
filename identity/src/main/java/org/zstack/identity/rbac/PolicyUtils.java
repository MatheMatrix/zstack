package org.zstack.identity.rbac;

import org.zstack.header.identity.rbac.RBAC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PolicyUtils {
    public static boolean isAdminOnlyAction(String action) {
        return RBAC.isAdminOnlyAPI(apiNamePatternFromAction(action));
    }

    public static String apiNamePatternFromAction(String action) {
        return apiNamePatternFromAction(action, false);
    }

    public static String apiNamePatternFromAction(String action, boolean oldPolicy) {
        if (!oldPolicy) {
            return action.split(":")[0];
        }

        String[] splited = action.split(":");

        if (splited.length != 2) {
            return splited[0];
        } else {
            return splited[1];
        }
    }

    /**
     * Example:<br/>
     *
     * input: "org.zstack.header.vm.APIStartVmInstanceMsg"<br/>
     * output [".**", ".header.**", ".header.vm.**", ".header.vm.*", ".header.vm.APIStartVmInstanceMsg"]<br/>
     */
    public static List<String> findAllMatchedApiPatterns(String api) {
        List<String> results = new ArrayList<>();

        if (!api.startsWith("org.zstack.")) {
            results.add(api);
            return results;
        }

        api = api.substring("org.zstack.".length()); // start without "."
        String[] split = api.split("\\.");
        results.add(".**");
        for (int i = 1; i < split.length; i++) {
            results.add("." + String.join(".", Arrays.copyOfRange(split, 0, i)) + ".**");
        }
        results.add("." + String.join(".", Arrays.copyOfRange(split, 0, split.length - 1)) + ".*");
        results.add("." + api);
        return results;
    }
}

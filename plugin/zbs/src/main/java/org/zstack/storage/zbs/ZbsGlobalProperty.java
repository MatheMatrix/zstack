package org.zstack.storage.zbs;

import org.zstack.core.GlobalProperty;
import org.zstack.core.GlobalPropertyDefinition;

/**
 * @author Xingwei Yu
 * @date 2024/3/27 16:43
 */
@GlobalPropertyDefinition
public class ZbsGlobalProperty {
    @GlobalProperty(name = "Zbs.primaryStorage.agentPackageName", defaultValue = "zbsprimarystorage-5.0.0.tar.gz")
    public static String PRIMARY_STORAGE_PACKAGE_NAME;
    @GlobalProperty(name = "Zbs.primaryStorage.port", defaultValue = "7763")
    public static int PRIMARY_STORAGE_AGENT_PORT;
    @GlobalProperty(name = "Zbs.primaryStorage.ansiblePlaybook", defaultValue = "zbsp.py")
    public static String PRIMARY_STORAGE_PLAYBOOK_NAME;
    @GlobalProperty(name="Zbs.primaryStorageAgent.urlRootPath", defaultValue = "")
    public static String PRIMARY_STORAGE_AGENT_URL_ROOT_PATH;
}

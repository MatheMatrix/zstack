package org.zstack.identity.imports;

import org.zstack.core.config.GlobalConfig;
import org.zstack.core.config.GlobalConfigDef;
import org.zstack.core.config.GlobalConfigDefinition;
import org.zstack.core.config.GlobalConfigValidation;
import org.zstack.identity.imports.entity.AccountImportSourceVO;
import org.zstack.resourceconfig.BindResourceConfig;

/**
 * Created by Wenhao.Zhang on 2024/06/05
 */
@GlobalConfigDefinition
public class AccountImportsGlobalConfig {
    public static final String CATEGORY = "accountImport";

    @BindResourceConfig({AccountImportSourceVO.class})
    @GlobalConfigDef(defaultValue = "false", description = "whether execute account synchronization task after system startup", type = Boolean.class)
    public static GlobalConfig SYNC_ACCOUNTS_ON_START = new GlobalConfig(CATEGORY, "sync.accounts.on.start");

    @GlobalConfigValidation(numberGreaterThan = 1000)
    @BindResourceConfig({AccountImportSourceVO.class})
    @GlobalConfigDef(defaultValue = "5000", description = "account source connection timeout in millis", type = Long.class)
    public static GlobalConfig REMOTE_SERVER_CONNECT_TIMEOUT_MILLIS = new GlobalConfig(CATEGORY, "source.connect.timeout.millis");

    @GlobalConfigValidation(numberGreaterThan = 1000)
    @BindResourceConfig({AccountImportSourceVO.class})
    @GlobalConfigDef(defaultValue = "5000", description = "account source reading timeout in millis", type = Long.class)
    public static GlobalConfig REMOTE_SERVER_READ_TIMEOUT_MILLIS = new GlobalConfig(CATEGORY, "source.read.timeout.millis");

    @BindResourceConfig({AccountImportSourceVO.class})
    @GlobalConfigDef(defaultValue = "false", description = "enable automatic synchronization of account from remote server", type = Boolean.class)
    public static GlobalConfig REMOTE_SERVER_AUTO_SYNC_ENABLE = new GlobalConfig(CATEGORY, "auto.sync.enable");

    @GlobalConfigValidation(numberGreaterThan = 60)
    @BindResourceConfig({AccountImportSourceVO.class})
    @GlobalConfigDef(defaultValue = "86400", description = "time interval in seconds for automatic synchronization of account from remote server", type = Long.class)
    public static GlobalConfig REMOTE_SERVER_AUTO_SYNC_INTERVAL_SECONDS = new GlobalConfig(CATEGORY, "auto.sync.interval.seconds");

    /**
     * @see org.zstack.identity.imports.entity.SyncRetireesStrategy
     */
    @GlobalConfigValidation(validValues = {"None", "Destroy", "Disable"})
    @BindResourceConfig({AccountImportSourceVO.class})
    @GlobalConfigDef(defaultValue = "None", description = "the strategy for deleted users when synchronizing accounts from remote server")
    public static GlobalConfig REMOTE_SERVER_SYNC_RETIREES_STRATEGY = new GlobalConfig(CATEGORY, "sync.retirees.strategy");

    /**
     * @see org.zstack.identity.imports.entity.SyncNewcomersStrategy
     */
    @GlobalConfigValidation(validValues = {"None", "Create"})
    @BindResourceConfig({AccountImportSourceVO.class})
    @GlobalConfigDef(defaultValue = "Create", description = "the strategy for newly created users when synchronizing accounts from remote server")
    public static GlobalConfig REMOTE_SERVER_SYNC_NEWCOMERS_STRATEGY = new GlobalConfig(CATEGORY, "sync.newcomers.strategy");
}

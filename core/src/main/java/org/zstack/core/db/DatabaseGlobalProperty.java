package org.zstack.core.db;

import org.zstack.core.GlobalProperty;
import org.zstack.core.GlobalPropertyDefinition;
import org.zstack.core.propertyvalidator.Vip;
import org.zstack.core.propertyvalidator.RegexValues;

/**
 */
@GlobalPropertyDefinition
public class DatabaseGlobalProperty {
    @GlobalProperty(name="DatabaseFacade.deadlockRetryTimes", defaultValue = "20")
    public static int retryTimes;
    @GlobalProperty(name="DB.url")
    @RegexValues(value = "^jdbc:mysql://.*")
    public static String DbUrl;
    @GlobalProperty(name="DB.user")
    public static String DbUser;
    @GlobalProperty(name="DB.password", encrypted = true)
    public static String DbPassword;
    @GlobalProperty(name="DB.idleConnectionTestPeriod")
    public static String DbIdleConnectionTestPeriod;
    @GlobalProperty(name="DB.maxIdleTime", defaultValue = "60")
    public static String DbMaxIdleTime;
    @GlobalProperty(name="DB.connectTimeout", defaultValue = "5000")
    public static String DbConnectTimeout;
    @GlobalProperty(name="DB.socketTimeout", defaultValue = "60000")
    public static String DbSocketTimeout;
    @GlobalProperty(name="DB.tcpKeepAlive", defaultValue = "true")
    public static String DbTcpKeepAlive;
    @GlobalProperty(name="DB.glock.waitTimeout", defaultValue = "28800")
    public static Long GLockWaitTimeout;
    @GlobalProperty(name="RESTFacade.hostname")
    @Vip(value = false)
    public static Long RESTFacade_hostname;
}

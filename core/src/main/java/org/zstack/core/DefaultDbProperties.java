package org.zstack.core;

import org.zstack.core.db.DatabaseGlobalProperty;
import org.zstack.utils.logging.CLogger;

import static org.zstack.utils.CollectionDSL.e;
import static org.zstack.utils.CollectionDSL.map;
import static org.zstack.utils.StringDSL.ln;

final class DefaultDbProperties {
    private DefaultDbProperties() {
    }

    static void prepare(CLogger logger) {
        if (DatabaseGlobalProperty.DbUrl != null) {
            String dbUrl = DatabaseGlobalProperty.DbUrl;

            if (System.getProperty("DbFacadeDataSource.jdbcUrl") == null) {
                String url = buildDbJdbcUrl(dbUrl, "zstack");

                System.setProperty("DbFacadeDataSource.jdbcUrl", url);
                debug(logger, "default DbFacadeDataSource.jdbcUrl to DB.url [%s]", url);
            }
            if (System.getProperty("RESTApiDataSource.jdbcUrl") == null) {
                String url = buildDbJdbcUrl(dbUrl, "zstack_rest");

                System.setProperty("RESTApiDataSource.jdbcUrl", url);
                debug(logger, "default RESTApiDataSource.jdbcUrl to DB.url [%s]", url);
            }
        }
        if (DatabaseGlobalProperty.DbUser != null) {
            setIfAbsent("DbFacadeDataSource.user", DatabaseGlobalProperty.DbUser, "DB.user", logger);
            setIfAbsent("RESTApiDataSource.user", DatabaseGlobalProperty.DbUser, "DB.user", logger);
        }
        if (DatabaseGlobalProperty.DbPassword != null) {
            setIfAbsent("DbFacadeDataSource.password", DatabaseGlobalProperty.DbPassword, "DB.password", logger);
            setIfAbsent("RESTApiDataSource.password", DatabaseGlobalProperty.DbPassword, "DB.password", logger);
        }
        if (DatabaseGlobalProperty.DbMaxIdleTime != null) {
            setIfAbsent("DbFacadeDataSource.maxIdleTime", DatabaseGlobalProperty.DbMaxIdleTime, "DB.maxIdleTime", logger);
            setIfAbsent("ExtraDataSource.maxIdleTime", DatabaseGlobalProperty.DbMaxIdleTime, "DB.maxIdleTime", logger);
            setIfAbsent("RESTApiDataSource.maxIdleTime", DatabaseGlobalProperty.DbMaxIdleTime, "DB.maxIdleTime", logger);
        }
        if (DatabaseGlobalProperty.DbIdleConnectionTestPeriod != null) {
            setIfAbsent("DbFacadeDataSource.idleConnectionTestPeriod", DatabaseGlobalProperty.DbIdleConnectionTestPeriod, "DB.idleConnectionTestPeriod", logger);
            setIfAbsent("ExtraDataSource.idleConnectionTestPeriod", DatabaseGlobalProperty.DbIdleConnectionTestPeriod, "DB.idleConnectionTestPeriod", logger);
            setIfAbsent("RESTApiDataSource.idleConnectionTestPeriod", DatabaseGlobalProperty.DbIdleConnectionTestPeriod, "DB.idleConnectionTestPeriod", logger);
        }
    }

    static String buildDbJdbcUrl(String dbUrl, String database) {
        if (dbUrl.contains("{database}")) {
            return ln(dbUrl).formatByMap(
                    map(e("database", database))
            ).trim();
        }

        return appendDatabaseToJdbcUrl(dbUrl, database);
    }

    private static String appendDatabaseToJdbcUrl(String dbUrl, String database) {
        String trimmed = dbUrl.trim();
        int queryIndex = trimmed.indexOf('?');
        String base = queryIndex >= 0 ? trimmed.substring(0, queryIndex) : trimmed;
        String query = queryIndex >= 0 ? trimmed.substring(queryIndex) : "";

        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        return String.format("%s/%s%s", base, database, query);
    }

    private static void setIfAbsent(String key, String value, String source, CLogger logger) {
        if (System.getProperty(key) != null) {
            return;
        }

        System.setProperty(key, value);
        debug(logger, "default %s to %s [%s]", key, source, value);
    }

    private static void debug(CLogger logger, String format, Object... args) {
        if (logger != null) {
            logger.debug(String.format(format, args));
        }
    }
}

package org.zstack.core;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.db.DatabaseGlobalProperty;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestDefaultDbJdbcUrl {
    private String oldDbUrl;
    private String oldConnectTimeout;
    private String oldSocketTimeout;
    private String oldTcpKeepAlive;
    private String oldDbFacadeJdbcUrl;
    private String oldRestApiJdbcUrl;

    @Before
    public void setUp() {
        oldDbUrl = DatabaseGlobalProperty.DbUrl;
        oldConnectTimeout = DatabaseGlobalProperty.DbConnectTimeout;
        oldSocketTimeout = DatabaseGlobalProperty.DbSocketTimeout;
        oldTcpKeepAlive = DatabaseGlobalProperty.DbTcpKeepAlive;
        oldDbFacadeJdbcUrl = System.getProperty("DbFacadeDataSource.jdbcUrl");
        oldRestApiJdbcUrl = System.getProperty("RESTApiDataSource.jdbcUrl");

        DatabaseGlobalProperty.DbConnectTimeout = "5000";
        DatabaseGlobalProperty.DbSocketTimeout = "60000";
        DatabaseGlobalProperty.DbTcpKeepAlive = "true";
        System.clearProperty("DbFacadeDataSource.jdbcUrl");
        System.clearProperty("RESTApiDataSource.jdbcUrl");
    }

    @After
    public void tearDown() {
        DatabaseGlobalProperty.DbUrl = oldDbUrl;
        DatabaseGlobalProperty.DbConnectTimeout = oldConnectTimeout;
        DatabaseGlobalProperty.DbSocketTimeout = oldSocketTimeout;
        DatabaseGlobalProperty.DbTcpKeepAlive = oldTcpKeepAlive;
        restoreProperty("DbFacadeDataSource.jdbcUrl", oldDbFacadeJdbcUrl);
        restoreProperty("RESTApiDataSource.jdbcUrl", oldRestApiJdbcUrl);
    }

    @Test
    public void testBuildDefaultDbUrlWithoutForcingJdbcParameters() {
        assertEquals(
                "jdbc:mysql://172.20.0.37:3306/zstack",
                DefaultDbProperties.buildDbJdbcUrl("jdbc:mysql://172.20.0.37:3306", "zstack")
        );
    }

    @Test
    public void testKeepDbUrlQueryAfterDatabaseName() {
        assertEquals(
                "jdbc:mysql://172.20.0.37:3306/zstack?useSSL=false",
                DefaultDbProperties.buildDbJdbcUrl("jdbc:mysql://172.20.0.37:3306?useSSL=false", "zstack")
        );
    }

    @Test
    public void testDoNotRewriteExplicitDatasourceJdbcUrls() {
        DatabaseGlobalProperty.DbUrl = null;
        System.setProperty("DbFacadeDataSource.jdbcUrl", "jdbc:mysql://172.20.0.37:3306/zstack?connectTimeout=1000");
        System.setProperty("RESTApiDataSource.jdbcUrl", "jdbc:mysql://172.20.0.37:3306/zstack_rest?useSSL=false");

        DefaultDbProperties.prepare(null);

        assertEquals(
                "jdbc:mysql://172.20.0.37:3306/zstack?connectTimeout=1000",
                System.getProperty("DbFacadeDataSource.jdbcUrl")
        );
        assertEquals(
                "jdbc:mysql://172.20.0.37:3306/zstack_rest?useSSL=false",
                System.getProperty("RESTApiDataSource.jdbcUrl")
        );
    }

    @Test
    public void testTemplateDbUrlKeepsQueryInPlace() {
        assertEquals(
                "jdbc:mysql://172.20.0.37:3306/zstack_rest?useSSL=false",
                DefaultDbProperties.buildDbJdbcUrl("jdbc:mysql://172.20.0.37:3306/{database}?useSSL=false", "zstack_rest")
        );
    }

    @Test
    public void testDatasourceTimeoutsComeFromPropertiesInsteadOfJdbcUrl() throws Exception {
        String databaseFacadeXml = readFile("conf/springConfigXml/DatabaseFacade.xml");
        String restFacadeXml = readFile("conf/springConfigXml/RESTFacade.xml");

        assertFalse(databaseFacadeXml.contains("zstack?connectTimeout"));
        assertFalse(restFacadeXml.contains("zstack_rest?connectTimeout"));
        assertDatasourceProperties(databaseFacadeXml);
        assertDatasourceProperties(restFacadeXml);
    }

    private void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private String readFile(String path) throws Exception {
        File file = new File(path);
        if (!file.exists()) {
            file = new File("..", path);
        }
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private void assertDatasourceProperties(String xml) {
        assertTrue(xml.contains("<prop key=\"connectTimeout\">${DB.connectTimeout:5000}</prop>"));
        assertTrue(xml.contains("<prop key=\"socketTimeout\">${DB.socketTimeout:60000}</prop>"));
        assertTrue(xml.contains("<prop key=\"tcpKeepAlive\">${DB.tcpKeepAlive:true}</prop>"));
    }
}

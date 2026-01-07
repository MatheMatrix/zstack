package org.zstack.core.config;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.zstack.utils.path.PathUtil;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

/**
 * Centralized configuration reader for app_config.xml
 * This class provides a single source of truth for the properties file name
 * All other components should use this class instead of hardcoding "zstack.properties"
 */
public class AppConfig {
    private static final String DEFAULT_PROPERTIES_FILE = "zstack.properties";
    private static volatile String propertiesFileName = null;

    /**
     * Get the properties file name from app_config.xml
     * This method is thread-safe and caches the result
     *
     * @return properties file name (e.g., "zstack.properties", "myapp.properties")
     */
    public static String getPropertiesFileName() {
        if (propertiesFileName == null) {
            synchronized (AppConfig.class) {
                if (propertiesFileName == null) {
                    propertiesFileName = loadPropertiesFileNameFromConfig();
                }
            }
        }
        return propertiesFileName;
    }

    /**
     * Load properties file name from app_config.xml
     * Falls back to "zstack.properties" if app_config.xml is not found or cannot be parsed
     */
    private static String loadPropertiesFileNameFromConfig() {
        try {
            File appConfigFile = PathUtil.findFileOnClassPath("app_config.xml", true);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(appConfigFile);

            NodeList nodes = doc.getElementsByTagName("propertiesFile");
            if (nodes.getLength() > 0) {
                String fileName = nodes.item(0).getTextContent().trim();
                System.out.println("[AppConfig] Using properties file: " + fileName);
                return fileName;
            }
        } catch (Exception e) {
            System.err.println("[AppConfig] Failed to load app_config.xml, using default: " + DEFAULT_PROPERTIES_FILE);
            e.printStackTrace();
        }

        return DEFAULT_PROPERTIES_FILE;
    }

    /**
     * Reset cached value (mainly for testing)
     */
    public static void reset() {
        propertiesFileName = null;
    }
}
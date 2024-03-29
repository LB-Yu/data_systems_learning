package org.apache.hadoop.yarn.applications.distributedshell;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;

/**
 * Helper class to over write log4j configuration.
 * */
public class Log4jPropertyHelper {

    /**
     * Over write the targetClass's log4j configuration with custom configuration in log4jPath.
     * */
    public static void updateLog4jConfiguration(Class<?> targetClass,
                                                String log4jPath) throws Exception {
        Properties customProperties = new Properties();
        FileInputStream fs = null;
        InputStream is = null;
        try {
            fs = new FileInputStream(log4jPath);
            is = targetClass.getResourceAsStream("/log4j.properties");
            customProperties.load(fs);
            Properties originalProperties = new Properties();
            originalProperties.load(is);
            for (Entry<Object, Object> entry : customProperties.entrySet()) {
                originalProperties.setProperty(entry.getKey().toString(), entry
                        .getValue().toString());
            }
            LogManager.resetConfiguration();
            PropertyConfigurator.configure(originalProperties);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(fs);
        }
    }
}

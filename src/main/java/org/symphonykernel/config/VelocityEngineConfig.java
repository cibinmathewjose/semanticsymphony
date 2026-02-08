package org.symphonykernel.config;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration class for Velocity template engine properties.
 * <p>
 * This class binds properties with the prefix "velocity" from the application
 * configuration files and provides access to them.
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "velocity")
@EnableConfigurationProperties(VelocityEngineConfig.class)
public class VelocityEngineConfig {
   private Map<String, Object> properties = new HashMap<>();

    /**
     * Gets all Velocity engine properties.
     *
     * @return a map containing all configured Velocity properties
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Sets the Velocity engine properties.
     *
     * @param properties a map of property key-value pairs to configure Velocity engine
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    /**
     * Retrieves a specific property value by its key.
     *
     * @param key the property key to look up
     * @return the property value as an Object, or null if the key doesn't exist
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Retrieves a specific property value as a String.
     *
     * @param key the property key to look up
     * @return the property value as a String, or null if the key doesn't exist
     */
    public String getPropertyAsString(String key) {
        Object value = properties.get(key);
        return value != null ? value.toString() : null;
    }
}

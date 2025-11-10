package org.symphonykernel.config;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
@Component
@ConfigurationProperties(prefix = "velocity")
@EnableConfigurationProperties(VelocityEngineConfig.class)
public class VelocityEngineConfig {
   private Map<String, Object> properties = new HashMap<>();

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public String getPropertyAsString(String key) {
        Object value = properties.get(key);
        return value != null ? value.toString() : null;
    }
}

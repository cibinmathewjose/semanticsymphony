package org.symphonykernel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(DBConnectionProperties.CONFIG_PREFIX)
public class DBConnectionProperties {

    public static final String CONFIG_PREFIX = "spring.datasource";

    @Value("${spring.datasource.driver.class}")
    private String driverClassName;
    private String url;
    private String username;
    private String password;

    // Getters and setters for all fields
    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}

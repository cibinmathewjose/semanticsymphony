package org.symphonykernel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for database connections.
 * 
 * <p>This class defines the configuration prefix and other properties
 * required to establish a database connection.
 * 
 * @version 1.0
 * @since 1.0
 */
@ConfigurationProperties(DBConnectionProperties.CONFIG_PREFIX)
public class DBConnectionProperties {

    /**
     * Configuration properties for database connections.
     * 
     * This class defines constants used for configuring the database connection.
     */
    public static final String CONFIG_PREFIX = "spring.datasource";

    @Value("${spring.datasource.driver.class}")
    private String driverClassName;
    private String url;
    private String username;
    private String password;

    // Getters and setters for all fields
    /**
     * Gets the driver class name.
     * 
     * @return the driver class name
     */
    public String getDriverClassName() {
        return driverClassName;
    }

    /**
     * Sets the driver class name.
     * 
     * @param driverClassName the driver class name
     */
    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    /**
     * Gets the database URL.
     * 
     * @return the database URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the database URL.
     * 
     * @param url the database URL
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Gets the username.
     * 
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username.
     * 
     * @param username the username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the password.
     * 
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password.
     * 
     * @param password the password
     */
    public void setPassword(String password) {
        this.password = password;
    }

}

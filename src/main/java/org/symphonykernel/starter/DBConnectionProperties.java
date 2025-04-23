package org.symphonykernel.starter;

import java.sql.Connection;
import java.sql.DriverManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public class DBConnectionProperties {

    @Value("${spring.datasource.driver.class}")
    private String driverClassName;

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    public Connection getConnection() throws Exception {
        // Load JDBC driver
        Class.forName(driverClassName);
        // Get connection
        Connection conn = DriverManager.getConnection(url, username, password);
        return conn;
    }
}

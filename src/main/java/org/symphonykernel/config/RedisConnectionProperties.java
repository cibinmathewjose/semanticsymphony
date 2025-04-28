package org.symphonykernel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;
import redis.clients.jedis.HostAndPort;

@ConfigurationProperties(RedisConnectionProperties.CONFIG_PREFIX)
public class RedisConnectionProperties {

    public static final String CONFIG_PREFIX = "spring.redis";

    @Value("${spring.redis.cluster.nodes}")
    private String clusterNodes;
    HostAndPort url;
    private boolean ssl;
    private String password;

    @PostConstruct
    public void init() {        
        String[] nodes = clusterNodes.split(",");
        for (String node : nodes) {
            String[] parts = node.split(":");
            url = new HostAndPort(parts[0].trim(), Integer.parseInt(parts[1].trim()));
            break;
        }
    }   

    // Getters and setters for all fields
    public String getClusterNodes() {
        return clusterNodes;
    }

    public void settClusterNodes(String clusterNodes) {
        this.clusterNodes = clusterNodes;
    }

    public HostAndPort getUrl() {
        return url;
    }

    public void setUrl(HostAndPort url) {
        this.url = url;
    }

    public boolean getSSL() {
        return ssl;
    }

    public void setSSL(boolean ssl) {
        this.ssl = ssl;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}

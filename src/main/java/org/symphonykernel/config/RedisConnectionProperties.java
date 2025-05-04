package org.symphonykernel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;
import redis.clients.jedis.HostAndPort;

/**
 * Configuration properties for Redis connection.
 */
@ConfigurationProperties(RedisConnectionProperties.CONFIG_PREFIX)
public class RedisConnectionProperties {

    /**
     * Prefix for Redis configuration properties.
     */
    public static final String CONFIG_PREFIX = "spring.redis";

    @Value("${spring.redis.cluster.nodes}")
    private String clusterNodes;
    HostAndPort url;
    private boolean ssl;
    private String password;

    /**
     * Initializes the Redis connection properties by parsing the cluster nodes.
     */
    @PostConstruct
    public void init() {        
        String[] nodes = clusterNodes.split(",");
        for (String node : nodes) {
            String[] parts = node.split(":");
            url = new HostAndPort(parts[0].trim(), Integer.parseInt(parts[1].trim()));
            break;
        }
    }   

    /**
     * Gets the cluster nodes as a comma-separated string.
     *
     * @return the cluster nodes
     */
    public String getClusterNodes() {
        return clusterNodes;
    }

    /**
     * Sets the cluster nodes as a comma-separated string.
     *
     * @param clusterNodes the cluster nodes
     */
    public void settClusterNodes(String clusterNodes) {
        this.clusterNodes = clusterNodes;
    }

    /**
     * Gets the Redis URL as a {@link HostAndPort} object.
     *
     * @return the Redis URL
     */
    public HostAndPort getUrl() {
        return url;
    }

    /**
     * Sets the Redis URL as a {@link HostAndPort} object.
     *
     * @param url the Redis URL
     */
    public void setUrl(HostAndPort url) {
        this.url = url;
    }

    /**
     * Gets whether SSL is enabled for the Redis connection.
     *
     * @return {@code true} if SSL is enabled, {@code false} otherwise
     */
    public boolean getSSL() {
        return ssl;
    }

    /**
     * Sets whether SSL is enabled for the Redis connection.
     *
     * @param ssl {@code true} to enable SSL, {@code false} otherwise
     */
    public void setSSL(boolean ssl) {
        this.ssl = ssl;
    }

    /**
     * Gets the password for the Redis connection.
     *
     * @return the Redis password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password for the Redis connection.
     *
     * @param password the Redis password
     */
    public void setPassword(String password) {
        this.password = password;
    }

}

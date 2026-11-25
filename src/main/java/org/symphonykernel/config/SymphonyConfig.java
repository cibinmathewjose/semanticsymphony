package org.symphonykernel.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Symphony AI agent mode.
 * 
 * <p>This class defines the configuration prefix and other properties
 * required to configure the Symphony AI agent mode.
 * 
 * @version 1.0
 * @since 1.0
 */
@ConfigurationProperties(SymphonyConfig.CONFIG_PREFIX)

public class SymphonyConfig {

    /**
     * Configuration properties for Symphony AI agent mode.
     * 
     * This class defines constants used for configuring the Symphony AI agent mode.
     */
    public static final String CONFIG_PREFIX = "symphony.ai";

    private boolean autonomous;
    private List<String> defaultKnowledge = List.of();
    private int threadPoolSize = 25;
    private long cacheTtlMs = 60000L;

    /**
     * Gets if the Symphony AI agent is autonomous mode.
     * 
     * @return the agent mode
     */
    public boolean isAutonomous() {
        return autonomous;
    }

    /** @return Knowledge names to query together when autonomous routing has no match */
    public List<String> getDefaultKnowledge() {
        return defaultKnowledge;
    }

    /** @param names configured Knowledge names in source order */
    public void setDefaultKnowledge(List<String> names) {
        defaultKnowledge = names == null ? List.of() : names.stream().filter(name -> name != null).map(name -> name.strip())
                .filter(name -> !name.isEmpty()).distinct().toList();
    }

    /**
     * Gets the thread pool size for the Symphony AI agent.
     * 
     * @return the thread pool size
     */
    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * Gets the cache TTL (time-to-live) for knowledge descriptions in milliseconds.
     * 
     * @return the cache TTL in milliseconds
     */
    public long getCacheTtlMs() {
        return cacheTtlMs;
    }

    /**
     * Sets the Symphony AI agent mode.
     * @param agentMode the agent mode
     */
    public void setAutonomous(boolean agentMode) {
        this.autonomous = agentMode;
    }

    /**
     * Sets the thread pool size for the Symphony AI agent.
     * @param threadPoolSize the thread pool size
     */
    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * Sets the cache TTL (time-to-live) for knowledge descriptions in milliseconds.
     * @param cacheTtlMs the cache TTL in milliseconds
     */
    public void setCacheTtlMs(long cacheTtlMs) {
        this.cacheTtlMs = cacheTtlMs;
    }

}

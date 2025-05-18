package org.symphonykernel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import redis.clients.jedis.HostAndPort;

@ConfigurationProperties(SharePointConfig.CONFIG_PREFIX)
public class SharePointConfig {

    /**
     * Prefix for SharePoint configuration properties.
     */
    public static final String CONFIG_PREFIX = "sharepoint";

     private String domain;
    private String clientId;
    private String clientSecret;
    private String tenantId;
    private String siteId;

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    // Getters and setters
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
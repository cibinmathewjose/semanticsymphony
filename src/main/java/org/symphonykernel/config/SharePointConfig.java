package org.symphonykernel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration class for SharePoint integration.
 * This class holds the properties required to connect to a SharePoint site,
 * such as domain, client ID, client secret, tenant ID, and site ID.
 * These properties are mapped from the application's configuration file
 * using the prefix "sharepoint".
 */
@ConfigurationProperties(SharePointConfig.CONFIG_PREFIX)
public class SharePointConfig {

    /**
     * Prefix for SharePoint configuration properties.
     */
    public static final String CONFIG_PREFIX = "sharepoint";

    // The domain of the SharePoint site
    private String domain;

    // The client ID for accessing SharePoint
    private String clientId;

    // The client secret for accessing SharePoint
    private String clientSecret;

    // The tenant ID associated with the SharePoint site
    private String tenantId;

    // The site ID of the SharePoint site
    private String siteId;

    /**
     * Gets the site ID of the SharePoint site.
     * @return the site ID
     */
    public String getSiteId() {
        return siteId;
    }

    /**
     * Sets the site ID of the SharePoint site.
     * @param siteId the site ID to set
     */
    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    // Getters and setters

    /**
     * Gets the client ID for accessing SharePoint.
     * @return the client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Sets the client ID for accessing SharePoint.
     * @param clientId the client ID to set
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Gets the domain of the SharePoint site.
     * @return the domain
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Sets the domain of the SharePoint site.
     * @param domain the domain to set
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /**
     * Gets the client secret for accessing SharePoint.
     * @return the client secret
     */
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Sets the client secret for accessing SharePoint.
     * @param clientSecret the client secret to set
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /**
     * Gets the tenant ID associated with the SharePoint site.
     * @return the tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the tenant ID associated with the SharePoint site.
     * @param tenantId the tenant ID to set
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
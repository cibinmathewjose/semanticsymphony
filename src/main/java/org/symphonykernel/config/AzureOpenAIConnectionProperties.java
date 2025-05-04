package org.symphonykernel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for connecting to Azure OpenAI.
 * 
 * <p>This class defines the configuration prefix and other properties
 * required to establish a connection with Azure OpenAI.
 * 
 * <p>It is used to bind configuration values from application properties.
 * 
 * @version 1.0
 * @since 1.0
 */
@ConfigurationProperties(AzureOpenAIConnectionProperties.CONFIG_PREFIX)
public class AzureOpenAIConnectionProperties {

    /**
     * The configuration prefix used for Azure OpenAI properties.
     */
    public static final String CONFIG_PREFIX = "client.azureopenai";

    /**
     * Azure OpenAI API endpoint.From the Azure AI OpenAI at 'Resource
     * Management' select `Keys and Endpoint` and find it on the right side.
     */
    private String endpoint;

    /**
     * Azure OpenAI API key.From the Azure AI OpenAI at 'Resource Management'
     * select `Keys and Endpoint` and find it on the right side.
     */
    private String key;

    /**
     * Azure OpenAI API deployment name specified in the Azure Open AI studio
     * under Management -> Deployments.
     */
    private String deploymentName;

    /**
     * Gets the Azure OpenAI API endpoint.
     * 
     * @return the endpoint URL
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Sets the Azure OpenAI API endpoint.
     * 
     * @param endpoint the endpoint URL
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Gets the Azure OpenAI API key.
     * 
     * @return the API key
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the Azure OpenAI API key.
     * 
     * @param key the API key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Gets the deployment name for Azure OpenAI.
     * 
     * @return the deployment name
     */
    public String getDeploymentName() {
        return deploymentName;
    }

    /**
     * Sets the deployment name for Azure OpenAI.
     * 
     * @param deploymentName the deployment name
     */
    public void setDeploymentName(String deploymentName) {
        this.deploymentName = deploymentName;
    }
}

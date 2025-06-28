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
    private String name;
    private int maxTokens;
    private int maxInputLength;
    private double temperature;
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
    
    /**
     * Gets the maximum input length allowed for Azure OpenAI.
     * 
     * @return the maximum input length
     */
    public int getMaxInputLength() {
        return maxInputLength;
    }

    /**
     * Sets the maximum input length allowed for Azure OpenAI.
     * 
     * @param tokens the maximum input length
     */
    public void setMaxInputLength(int tokens) {
        this.maxInputLength = tokens;
    }

    /**
     * Gets the maximum number of tokens allowed for Azure OpenAI.
     * 
     * @return the maximum number of tokens
     */
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * Sets the maximum number of tokens allowed for Azure OpenAI.
     * 
     * @param tokens the maximum number of tokens
     */
    public void setMaxTokens(int tokens) {
        this.maxTokens = tokens;
    }

    /**
     * Gets the temperature parameter for Azure OpenAI.
     * 
     * @return the temperature value
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * Sets the temperature parameter for Azure OpenAI.
     * 
     * @param temp the temperature value
     */
    public void setTemperature(double temp) {
        this.temperature = temp;
    }

    /**
     * Gets the name associated with the Azure OpenAI connection.
     * 
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name associated with the Azure OpenAI connection.
     * 
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }
    
}

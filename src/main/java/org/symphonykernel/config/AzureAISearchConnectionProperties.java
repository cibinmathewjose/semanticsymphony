package org.symphonykernel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.azure.core.credential.AzureKeyCredential;

/**
 * Configuration properties for connecting to Azure AI Search.
 * 
 * <p>This class defines the configuration prefix and other properties
 * required to establish a connection with Azure AI Search.
 * 
 * <p>It is used to bind configuration values from application properties.
 * 
 * @author Cibin Jose
 * @version 1.0
 * @since 1.0
 */
@ConfigurationProperties(AzureAISearchConnectionProperties.CONFIG_PREFIX)
public class AzureAISearchConnectionProperties {

    /**
     * The configuration prefix used for Azure AI Search properties.
     */
    public static final String CONFIG_PREFIX = "client.aisearch";

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
     * Creates an AzureKeyCredential using the provided API key.
     * 
     * @return an instance of AzureKeyCredential
     */
    public AzureKeyCredential getAzureKeyCredential() {
        return new AzureKeyCredential(key);
    }

}

package org.symphonykernel.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

@ConfigurationProperties(AzureOpenAIConnectionProperties.CONFIG_PREFIX)
public class AzureOpenAIConnectionProperties {

    public static final String CONFIG_PREFIX = "client.azureopenai";
    
     private String library;
    private String name;
    private int maxTokens;
    private int maxInputLength;
    private double temperature;
    private int maxParallel;
    private int maxProcessingTime=300;
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

    /**
     * Gets the maximum number of parallel connections allowed.
     * 
     * @return the maximum number of parallel connections
     */
    public int getMaxParallel() {
        return maxParallel;
    }

    /**
     * Sets the maximum number of parallel connections allowed.
     * 
     * @param maxParallel the maximum number of parallel connections
     */
    public void setMaxParallel(int maxParallel) {
        this.maxParallel = maxParallel;
    }

    public void setMaxProcessingTime(int maxProcessingTime) {
        this.maxProcessingTime = maxProcessingTime;
    }

    public int getMaxProcessingTime() {
        return maxProcessingTime;
    }
    
    /**
     * Gets the the library to be used to interact with LLM..
     * 
     * @return the library
     */
    public String getLibrary() {
        return library;
    }

    /**
     * Sets the library to be used to interact with LLM.
     * 
     * @param library the library
     */
    public void setLibrary(String library) {
        this.library = library;
    }

    @Autowired
    private Environment environment;

    // --- helper for per-model completion tokens ---

    /**
     * Returns the max completion tokens configured for the given model name.
     * <p>
     * Looks for {@code client.azureopenai.{modelName}.options.maxCompletionTokens}
     * first, and if not found falls back to the global {@code maxTokens}.
     *
     * @param modelName the logical / deployment model name
     * @return the max completion tokens for this model, or {@code null} if not defined
     */
    public Integer getMaxCompletionTokens(String modelName) {
        Integer maxCompletionTokens = null;
        if (modelName != null && !modelName.isBlank() && environment != null) {
            maxCompletionTokens = environment.getProperty( CONFIG_PREFIX + "." + modelName + ".options.maxCompletionTokens", Integer.class);           
        }
        return maxCompletionTokens;
    }
    /**
     * Returns the max tokens configured for the given model name.
     * <p>
     * Looks for {@code client.azureopenai.{modelName}.options.maxTokens}
     * first, and if not found falls back to the global {@code maxTokens}.
     *
     * @param modelName the logical / deployment model name
     * @return the max tokens for this model, or {@code null} if not defined
     */
    public Integer getMaxTokens(String modelName) {
        Integer maxTokens = null;
        if (modelName != null && !modelName.isBlank() && environment != null) {
            maxTokens = environment.getProperty( CONFIG_PREFIX + "." + modelName + ".options.maxTokens", Integer.class);           
        }
        return maxTokens;
    }
    /**
     * Returns the temperature configured for the given model name.
     * <p>
     * Looks for {@code client.azureopenai.{modelName}.options.temperature}
     * first, and if not found falls back to the global {@code temperature}.
     *
     * @param modelName the logical / deployment model name
     * @return the temperature for this model, or {@code null} if not defined
     */
    public Double getTemperature(String modelName) {
        Double temperature = null;
        if (modelName != null && !modelName.isBlank() && environment != null) {
            temperature = environment.getProperty( CONFIG_PREFIX + "." + modelName + ".options.temperature", Double.class);           
        }
        return temperature;     
    }
}

package org.symphonykernel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.azure.core.credential.AzureKeyCredential;

@ConfigurationProperties(AzureAISearchConnectionProperties.CONFIG_PREFIX)
public class AzureAISearchConnectionProperties {

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

 
    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }   
    public AzureKeyCredential getAzureKeyCredential(){
    	return new AzureKeyCredential( key);
    }

}

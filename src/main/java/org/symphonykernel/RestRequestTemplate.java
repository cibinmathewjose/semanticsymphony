package org.symphonykernel;

import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class RestRequestTemplate {
    
    @JsonProperty("Method")
    public HttpMethod method;
    
    @JsonProperty("UrlParams")
    public String urlParams;    
    
    @JsonProperty("HeaderTemplate")
    public JsonNode headerTemplate;    
   
    @JsonProperty("IncludeRequestBody")
    public boolean includeRequestBody;

    @JsonProperty("BodyTemplate")
    public JsonNode bodyTemplate;  
    
    @JsonProperty("ResultSelectors")
    public String resultSelectors;  

   

    public RestRequestTemplate() {
        this.method = HttpMethod.POST;        
        this.includeRequestBody = true;
        this.urlParams = null;
        this.headerTemplate = null;
        this.bodyTemplate = null;
    }

    static RestRequestTemplate template=new RestRequestTemplate();

    public static RestRequestTemplate getDefault() {
        return template;
    }
    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    public String getUrlParams() {
        return urlParams;
    }

    public void setUrlParams(String urlParams) {
        this.urlParams = urlParams;
    }

    public JsonNode getHeaderTemplate() {
        return headerTemplate;
    }

    public void setHeaderTemplate(JsonNode headerTemplate) {
        this.headerTemplate = headerTemplate;
    }

    public boolean isIncludeRequestBody() {
        return includeRequestBody;
    }

    public void setIncludeRequestBody(boolean includeRequestBody) {
        this.includeRequestBody = includeRequestBody;
    }

    public JsonNode getBodyTemplate() {
        return bodyTemplate;
    }

    public void setBodyTemplate(JsonNode bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }
    public String getResultSelectors() {
        return resultSelectors;
    }
    public void setResultSelectors(String resultSelectors) {
        this.resultSelectors = resultSelectors;
    }
}

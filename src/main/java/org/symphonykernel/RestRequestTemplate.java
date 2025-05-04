package org.symphonykernel;

import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a template for REST requests.
 */
public class RestRequestTemplate {

    /**
     * The HTTP method for the request (e.g., GET, POST).
     */
    @JsonProperty("Method")
    public HttpMethod method;

    /**
     * URL parameters for the request.
     */
    @JsonProperty("UrlParams")
    public String urlParams;

    /**
     * Header template for the request.
     */
    @JsonProperty("HeaderTemplate")
    public JsonNode headerTemplate;

    /**
     * Indicates whether to include the request body.
     */
    @JsonProperty("IncludeRequestBody")
    public boolean includeRequestBody;

    /**
     * Body template for the request.
     */
    @JsonProperty("BodyTemplate")
    public JsonNode bodyTemplate;

    /**
     * Selectors for extracting results from the response.
     */
    @JsonProperty("ResultSelectors")
    public String resultSelectors;

    /**
     * Default constructor initializing default values.
     */
    public RestRequestTemplate() {
        this.method = HttpMethod.POST;
        this.includeRequestBody = true;
        this.urlParams = null;
        this.headerTemplate = null;
        this.bodyTemplate = null;
    }

    static RestRequestTemplate template = new RestRequestTemplate();

    /**
     * Returns the default REST request template.
     *
     * @return the default template
     */
    public static RestRequestTemplate getDefault() {
        return template;
    }

    /**
     * Gets the HTTP method for the request.
     *
     * @return the HTTP method
     */
    public HttpMethod getMethod() {
        return method;
    }

    /**
     * Sets the HTTP method for the request.
     *
     * @param method the HTTP method to set
     */
    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    /**
     * Gets the URL parameters for the request.
     *
     * @return the URL parameters
     */
    public String getUrlParams() {
        return urlParams;
    }

    /**
     * Sets the URL parameters for the request.
     *
     * @param urlParams the URL parameters to set
     */
    public void setUrlParams(String urlParams) {
        this.urlParams = urlParams;
    }

    /**
     * Gets the header template for the request.
     *
     * @return the header template
     */
    public JsonNode getHeaderTemplate() {
        return headerTemplate;
    }

    /**
     * Sets the header template for the request.
     *
     * @param headerTemplate the header template to set
     */
    public void setHeaderTemplate(JsonNode headerTemplate) {
        this.headerTemplate = headerTemplate;
    }

    /**
     * Checks if the request body is included.
     *
     * @return true if the request body is included, false otherwise
     */
    public boolean isIncludeRequestBody() {
        return includeRequestBody;
    }

    /**
     * Sets whether to include the request body.
     *
     * @param includeRequestBody true to include the request body, false otherwise
     */
    public void setIncludeRequestBody(boolean includeRequestBody) {
        this.includeRequestBody = includeRequestBody;
    }

    /**
     * Gets the body template for the request.
     *
     * @return the body template
     */
    public JsonNode getBodyTemplate() {
        return bodyTemplate;
    }

    /**
     * Sets the body template for the request.
     *
     * @param bodyTemplate the body template to set
     */
    public void setBodyTemplate(JsonNode bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }

    /**
     * Gets the result selectors for extracting response data.
     *
     * @return the result selectors
     */
    public String getResultSelectors() {
        return resultSelectors;
    }

    /**
     * Sets the result selectors for extracting response data.
     *
     * @param resultSelectors the result selectors to set
     */
    public void setResultSelectors(String resultSelectors) {
        this.resultSelectors = resultSelectors;
    }
}

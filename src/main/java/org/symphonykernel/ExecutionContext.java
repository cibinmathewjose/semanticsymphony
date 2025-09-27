/**
 * Represents the execution context for processing requests.
 * 
 * This class contains various attributes such as HTTP headers, variables,
 * knowledge base, and user session information required for execution.
 * 
 * @author 
 */
package org.symphonykernel;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.symphonykernel.ai.KnowledgeGraphBuilder;
import org.symphonykernel.config.Constants;
import org.symphonykernel.core.IHttpHeaderProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;

/**
 * Represents the execution context for operations.
 * 
 * This class encapsulates the context in which operations are executed.
 */
public class ExecutionContext {

    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionContext.class);
    /** The HTTP header provider for the execution context. */
    private IHttpHeaderProvider header;

    /** The variables associated with the execution context. */
    JsonNode variables;

    /** The knowledge base associated with the execution context. */
    Knowledge kb;

    /** The name of the execution context. */
    String name;

    /** The user's query. */
    String usersQuery;

    /** Indicates whether conversion is enabled. */
    boolean convert;

    /** The chat history associated with the execution context. */
    ChatHistory chatHistory;

    /** The user session information. */
    UserSession info;

    /** The chat request associated with the execution context. */
    ChatRequest request;

    /** The REST request template. */
    RestRequestTemplate tmplate;

    /** The body of the HTTP request. */
    JsonNode body;

    /** The HTTP headers for the execution context. */
    HttpHeaders headers;

    /** The HTTP method for the execution context. */
    HttpMethod method;

    /** The URL associated with the execution context. */
    String url;

    Map<String, JsonNode> resolvedValues;

    FlowItem currentFlowItem;

    /**
     * Default constructor for ExecutionContext.
     * Initializes the resolved values map.
     */
    public ExecutionContext() {       
        resolvedValues=new java.util.HashMap<>();    
    }

    /**
     * Copy constructor for ExecutionContext.
     * Copies the properties from the given ExecutionContext instance.
     * 
     * @param ctx the ExecutionContext instance to copy from
     */
    public ExecutionContext(ExecutionContext ctx) {       
        this();
        setHttpHeaderProvider(ctx.getHttpHeaderProvider());
        setUsersQuery(ctx.getUsersQuery());
        setChatHistory(ctx.getChatHistory());
        resolvedValues=ctx.getResolvedValues();
        setCurrentFlowItem(ctx.getCurrentFlowItem());
    }

    /**
     * Returns the current flow item associated with the execution context.
     * 
     * @return the current flow item
     */
    public FlowItem getCurrentFlowItem() {
        return currentFlowItem;
    }

    /**
     * Sets the current flow item for the execution context.
     * 
     * @param currentFlowItem the current flow item to set
     * @return the updated execution context
     */
    public ExecutionContext setCurrentFlowItem(FlowItem currentFlowItem) {
        this.currentFlowItem = currentFlowItem;
        return this;
    }
  

    /**
     * Returns the URL associated with the execution context.
     * 
     * @return the URL as a string
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the HTTP method for the execution context.
     * 
     * @return the HTTP method
     */
    public HttpMethod getMethod() {
        return method;
    }

    /**
     * Returns the HTTP headers for the execution context.
     * 
     * @return the HTTP headers
     */
    public HttpHeaders getHeaders() {
        return headers;
    }
    
    /**
     * Returns the name of the execution context.
     * 
     * @return the name as a string
     */
    public String getName() {
        return name == null && kb != null ? kb.getName() : name;
    }

    /**
     * Indicates whether conversion is enabled for the execution context.
     * 
     * @return true if conversion is enabled, false otherwise
     */
    public boolean getConvert() {
        return convert;
    }

    /**
     * Returns the user session associated with the execution context.
     * 
     * @return the user session
     */
    public UserSession getUserSession() {
        return info;
    }

    /**
     * Returns the request ID associated with the user session.
     * 
     * @return the request ID as a string
     */
    public String getRequestId() {
        return info!=null ? info.getRequestId() : null;
    }

    /**
     * Returns the chat request associated with the execution context.
     * 
     * @return the chat request
     */
    public ChatRequest getRequest() {
        return request;
    }

    /**
     * Returns the user's query.
     * 
     * @return the user's query as a string
     */
    public String getUsersQuery() {
        return usersQuery;
    }

    /**
     * Returns the HTTP header provider.
     * 
     * @return the HTTP header provider
     */
    public IHttpHeaderProvider getHttpHeaderProvider() {
        return header;
    }

    /**
     * Returns the variables associated with the execution context.
     * 
     * @return the variables as a JsonNode
     */
    public JsonNode getVariables() {
        return variables;
    }

    /**
     * Returns the knowledge associated with the execution context.
     * 
     * @return the knowledge
     */
    public Knowledge getKnowledge() {
        return kb;
    }

    /**
     * Returns the chat history associated with the execution context.
     * 
     * @return the chat history
     */
    public ChatHistory getChatHistory() {
        return chatHistory;
    }

    /**
     * Returns the body of the HTTP request.
     * 
     * @return the body as a JsonNode
     */
    public JsonNode getBody() {
        return body;
    }
    
    /**
     * Returns the REST request template.
     * 
     * @return the REST request template
     */
    public RestRequestTemplate getTmplate() {
        return tmplate;
    }
    
    /**
     * Sets the URL for the execution context.
     * 
     * @param url the URL to set
     * @return the updated execution context
     */
    public ExecutionContext setUrl(String url) {
        this.url = url;
        return this;
    }

    /**
     * Sets the HTTP method for the execution context.
     * 
     * @param method the HTTP method to set
     * @return the updated execution context
     */
    public ExecutionContext setMethod(HttpMethod method) {
        this.method = method;
        return this;
    }
    

    /**
     * Sets the HTTP headers for the execution context.
     * 
     * @param headers the HTTP headers to set
     * @return the updated execution context
     */
    public ExecutionContext setHeaders(HttpHeaders headers) {
        this.headers = headers;
        return this;
    }

    /**
     * Sets the body for the execution context.
     * 
     * @param body the body to set
     * @return the updated execution context
     */
    public ExecutionContext setBody(JsonNode body) {
        this.body = body;
        return this;
    }

    /**
     * Sets the template for the execution context.
     * 
     * @param tmplate the template to set
     * @return the updated execution context
     */
    public ExecutionContext setTmplate(RestRequestTemplate tmplate) {
        this.tmplate = tmplate;
        return this;
    }

    /**
     * Sets the name for the execution context.
     * 
     * @param name the name to set
     * @return the updated execution context
     */
    public ExecutionContext setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Enables or disables conversion for the execution context.
     * 
     * @param convert true to enable conversion, false to disable
     * @return the updated execution context
     */
    public ExecutionContext setConvert(boolean convert) {
        this.convert = convert;
        return this;
    }

    /**
     * Sets the user session for the execution context.
     * 
     * @param info the user session to set
     * @return the updated execution context
     */
    public ExecutionContext setUserSession(UserSession info) {
        this.info = info;       
        return this;
        
    }

    /**
     * Sets the chat request for the execution context.
     * 
     * @param request the chat request to set
     * @return the updated execution context
     */
    public ExecutionContext setRequest(ChatRequest request) {
        this.request = request;
       
        if(request!=null && request.getConversationId() !=null) 
        {
            put(Constants.LOGGER_TRACE_ID,  request.getConversationId());
        }
        return this;
    }

    /**
     * Sets the user's query for the execution context.
     * 
     * @param usersQuery the user's query to set
     * @return the updated execution context
     */
    public ExecutionContext setUsersQuery(String usersQuery) {
        this.usersQuery = usersQuery;
        return this;
    }

    /**
     * Sets the HTTP header provider for the execution context.
     * 
     * @param header the HTTP header provider to set
     * @return the updated execution context
     */
    public ExecutionContext setHttpHeaderProvider(IHttpHeaderProvider header) {
        this.header = header;
        return this;
    }

    /**
     * Sets the variables for the execution context.
     * 
     * @param variables the variables to set
     * @return the updated execution context
     */
    public ExecutionContext setVariables(JsonNode variables) {
        this.variables = variables;
        return this;
    }

    /**
     * Sets the knowledge for the execution context.
     * 
     * @param kb the knowledge to set
     * @return the updated execution context
     */
    public ExecutionContext setKnowledge(Knowledge kb) {
        this.kb = kb;
        return this;
    }

    /**
     * Sets the chat history for the execution context.
     * 
     * @param chatHistory the chat history to set
     * @return the updated execution context
     */
    public ExecutionContext setChatHistory(ChatHistory chatHistory) {
        this.chatHistory = chatHistory;
        return this;
    }

    /**
     * Gets the HTTP header provider for the execution context.
     * 
     * @return the HTTP header provider
     */
    public IHttpHeaderProvider getHeader() {
        return header;
    }

    /**
     * Sets the HTTP header provider for the execution context.
     * 
     * @param header the HTTP header provider to set
     */
    public void setHeader(IHttpHeaderProvider header) {
        this.header = header;
    }
    @Override
    public String toString()
    {
    	 String url = getUrl();         
         String body = getBody() != null ? getBody().toString() : null;
         HttpMethod method = getMethod();
         return String.format("url:{},method:{},body:{}", url,method,body);
    	
    }

    /**
     * Retrieves the resolved values map.
     * 
     * @return a map of resolved values
     */
    public Map<String, JsonNode> getResolvedValues() {
        return resolvedValues;
    }

    /**
     * Checks if the execution context is asynchronous.
     * 
     * @return true if the context is asynchronous, false otherwise
     */
    public boolean isIsAsync() {
        return request != null && "ASYNC_CHAT".equals(request.getKey());
    }

    /**
     * Checks if the execution context is an asynchronous result.
     * 
     * @return true if the context is an asynchronous result, false otherwise
     */
    public boolean isIsAsyncResult() {
        return request != null && "ASYNC_RESULT".equals(request.getKey());
    }

    /**
     * Adds a key-value pair to the resolved values map.
     * 
     * @param key the key to add
     * @param value the value to associate with the key
     */
    public void put(String key, JsonNode value) {
        if( value!=null && key!=null&& !key.isEmpty()) 
            resolvedValues.put(key, value);
    }

    /**
     * Adds a key-value pair to the resolved values map.
     * 
     * @param key the key to add
     * @param value the string value to associate with the key
     */
    public void put(String key, String value) {
        if( value!=null && key!=null&& !key.isEmpty()) 
            resolvedValues.put(key, com.fasterxml.jackson.databind.node.TextNode.valueOf(value));
    }

    /**
     * Adds a key-value pair to the resolved values map.
     * 
     * @param key the key to add
     * @param value the numeric value to associate with the key
     */
    public void put(String key, Number value) {
        if (value != null && key != null && !key.isEmpty())
            resolvedValues.put(key, com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(value.doubleValue()));
    }
}



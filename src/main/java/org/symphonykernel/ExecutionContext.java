package org.symphonykernel;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.symphonykernel.core.IHttpHeaderProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;

public class ExecutionContext {

    private IHttpHeaderProvider header;
    JsonNode variables;
    Knowledge kb;
    String name;
    String usersQuery;
    boolean convert;
    ChatHistory chatHistory;
    UserSession info ;
    ChatRequest request;
    RestRequestTemplate tmplate;
    JsonNode body;
    HttpHeaders headers;
    HttpMethod method;
    String url;

    public String getUrl() {
        return url;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }
    
    public String getName() {
        return name == null && kb != null ? kb.getName() : name;
    }
    public boolean getConvert() {
        return convert;
    }

    public UserSession getUserSession() {
        return info;
    }
    public String getRequestId() {
        return info!=null ? info.getRequestId() : null;
    }

    public ChatRequest getRequest() {
        return request;
    }
    public String getUsersQuery() {
        return usersQuery;
    }

    public IHttpHeaderProvider getHttpHeaderProvider() {
        return header;
    }
    public JsonNode getVariables() {
        return variables;
    }
    public Knowledge getKnowledge() {
        return kb;
    }
    public ChatHistory getChatHistory() {
        return chatHistory;
    }

    public JsonNode getBody() {
        return body;
    }
    
    public RestRequestTemplate getTmplate() {
        return tmplate;
    }
    
    public ExecutionContext setUrl(String url) {
        this.url = url;
        return this;
    }

    public ExecutionContext setMethod(HttpMethod method) {
        this.method = method;
        return this;
    }
    

    public ExecutionContext setHeaders(HttpHeaders headers) {
        this.headers = headers;
        return this;
    }

    public ExecutionContext setBody(JsonNode body) {
        this.body = body;
        return this;
    }
    public ExecutionContext setTmplate(RestRequestTemplate tmplate) {
        this.tmplate = tmplate;
        return this;
    }

    public ExecutionContext setName(String name) {
        this.name = name;
        return this;
    }

    public ExecutionContext setConvert(boolean convert) {
        this.convert = convert;
        return this;
    }

    public ExecutionContext setUserSession(UserSession info) {
        this.info = info;
        return this;
        
    }
    public ExecutionContext setRequest(ChatRequest request) {
        this.request = request;
        return this;
    }
    public ExecutionContext setUsersQuery(String usersQuery) {
        this.usersQuery = usersQuery;
        return this;
    }

    public ExecutionContext setHttpHeaderProvider(IHttpHeaderProvider header) {
        this.header = header;
        return this;
    }

    public ExecutionContext setVariables(JsonNode variables) {
        this.variables = variables;
        return this;
    }

    public ExecutionContext setKnowledge(Knowledge kb) {
        this.kb = kb;
        return this;
    }

    public ExecutionContext setChatHistory(ChatHistory chatHistory) {
        this.chatHistory = chatHistory;
        return this;
    }

    
}

package org.symphonykernel;

import org.symphonykernel.core.IHttpHeaderProvider;

/**
 * Represents a chat request with user and session details.
 * 
 * <p>This class provides methods to set and retrieve chat-related properties.
 * 
 * @version 1.0
 * @since 1.0
 * @author Cibin Jose
 */
public class ChatRequest {

    private String key;
    private String query;
    private String user;
    private String session;
    private String payload;

    private IHttpHeaderProvider httpHeaderProvider;

    /**
     * Sets the HTTP header provider.
     * 
     * @param hp the HTTP header provider
     */
    public void setHeaderProvider(IHttpHeaderProvider hp) {
        this.httpHeaderProvider = hp;
    }

    /**
     * Gets the HTTP header provider.
     * 
     * @return the HTTP header provider
     */
    public IHttpHeaderProvider getHeaderProvider() {
        return httpHeaderProvider;
    }

    /**
     * Gets the user associated with the chat request.
     * 
     * @return the user
     */
    public String getUser() {
        return user;
    }

    /**
     * Sets the user for the chat request.
     * 
     * @param user the user
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Gets the session associated with the chat request.
     * 
     * @return the session
     */
    public String getSession() {
        return session;
    }

    /**
     * Sets the session for the chat request.
     * 
     * @param session the session
     */
    public void setSession(String session) {
        this.session = session;
    }

    /**
     * Sets the key for the chat request.
     * 
     * @param key the key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Gets the key associated with the chat request.
     * 
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Gets the query associated with the chat request.
     * 
     * @return the query
     */
    public String getQuery() {
        return query;
    }

    /**
     * Sets the query for the chat request.
     * 
     * @param query the query
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * Gets the payload associated with the chat request.
     * 
     * @return the payload
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Sets the payload for the chat request.
     * 
     * @param payload the payload
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * Returns a string representation of the chat request.
     * 
     * @return a string representation of the chat request
     */
    @Override
    public String toString() {
        return "{name='" + query + "', paylod=" + payload + "}";
    }

}

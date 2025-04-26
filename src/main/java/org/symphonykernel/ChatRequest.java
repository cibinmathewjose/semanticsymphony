package org.symphonykernel;

import org.symphonykernel.core.IHttpHeaderProvider;

public class ChatRequest {

    private String key;
    private String query;
    private String user;
    private String session;
    private String payload;

    private IHttpHeaderProvider httpHeaderProvider;

    public void setHeaderProvider(IHttpHeaderProvider hp) {
        this.httpHeaderProvider = hp;
    }

    public IHttpHeaderProvider getHeaderProvider() {
        return httpHeaderProvider;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    
    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "{name='" + query + "', paylod=" + payload + "}";
    }

}

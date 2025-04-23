package org.symphonykernel;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.symphonykernel.core.IHttpHeaderProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

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

    public JsonNode getVariables() {
        if (payload != null && !payload.isEmpty()) {
            try {
                // Decode the Base64 string
                byte[] decodedBytes = Base64.getDecoder().decode(payload);
                String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);

                // Parse the JSON string
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readTree(decodedString);

            } catch (Exception e) {
                // Handle invalid Base64 input
                System.out.println(e.getMessage());

            }
        }
        return JsonNodeFactory.instance.objectNode();
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

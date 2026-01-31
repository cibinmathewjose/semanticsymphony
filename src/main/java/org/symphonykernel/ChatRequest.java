package org.symphonykernel;

import java.util.HashMap;
import java.util.Map;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.symphonykernel.core.IHttpHeaderProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;

import ch.qos.logback.core.pattern.parser.Node;

/**
 * Represents a chat request with user and session details.
 *
 * <p>
 * This class provides methods to set and retrieve chat-related properties.
 *
 * @version 1.0
 * @since 1.0
 * @author Cibin Jose
 */
public class ChatRequest {

    private static final Logger logger = LoggerFactory.getLogger(ChatRequest.class);
    private String key;
    private String query;
    private String user;
    private String session;
    private String conversationId;
    private String payload;
    private Map<String, String> contextInfo= new HashMap<>();

    

    /**
     * The HTTP header provider for the chat request.
     */
    // This is used to set the HTTP headers for the request.

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

    public String getPayloadParam(String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return null;
        }
        if (payload != null && !payload.isEmpty() && !"NONE".equals(payload)) {
            try {
                String[] pairs = payload.split("&");
                for (String pair : pairs) {
                    int idx = pair.indexOf('=');
                    if (idx < 0) {
                        continue;
                    }
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                    if (paramName.equals(key)) {
                        String value = pair.length() > idx + 1
                                ? pair.substring(idx + 1)
                                : "";
                        return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                    }
                }
            } catch (Exception e) {
                logger.error("Error decoding payload as query string: {}", e.getMessage());
            }
        }
        return null;
    }
    /**
     * Gets the variables from the payload and context information.
     * 
     * <p>If the payload is valid JSON, it merges the context information into the payload.
     * If the payload is empty, it returns the context information as JSON.
     * 
     * @return a JsonNode representing the variables
     */
    public JsonNode getVariables() {
        if (payload != null && !payload.isEmpty() && !"NONE".equals(payload)) {
            try {
                if(payload.startsWith("{")||payload.startsWith("[")) {
                    //Direct JSON
                    logger.info("JSON payload: {}", payload);
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = objectMapper.readTree(payload);
                    if (!contextInfo.isEmpty()) {
                        if (jsonNode.isArray()) {
                            ArrayNode resultArray = objectMapper.createArrayNode();
                            for (JsonNode item : jsonNode) {
                                if (item.isObject()) {
                                    resultArray.add(mapParams( item));
                                } else {
                                    resultArray.add(item);
                                }
                            }
                            return resultArray;
                        } else {
                            return mapParams(jsonNode);
                        }
                    }
                    return jsonNode;
                }                
                
            } catch (Exception e) {
                logger.error("Error decoding payload: {}", e.getMessage());
            }
        } 
        if (!contextInfo.isEmpty()) {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.valueToTree(contextInfo);
        }
        return NullNode.instance;        
    }



    private JsonNode mapParams( JsonNode jsonNode) throws IllegalArgumentException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> combinedMap = objectMapper.convertValue(jsonNode, Map.class);
        contextInfo.forEach((contextKey, contextValue) -> {
            boolean duplicateKey = combinedMap.keySet().stream()
                    .anyMatch(existingKey -> existingKey.equalsIgnoreCase(contextKey));
            if (duplicateKey ) {
               // combinedMap.entrySet().removeIf(entry -> entry.getKey().equalsIgnoreCase(contextKey));
               if(combinedMap.get(contextKey)==null)
                   combinedMap.put(contextKey, contextValue);
                else
                 logger.warn("Potential duplicates found for {} giving priority to explicitly specified value {} and ignoring context value {}", contextKey, combinedMap.get(contextKey), contextValue);
            }
            else
            {
                combinedMap.put(contextKey, contextValue);
            }
        });
        return objectMapper.valueToTree(combinedMap);
    }

    /**
     * Adds a key-value pair to the context information map.
     *
     * @param key the key for the context information
     * @param value the value for the context information
     */
    public void addContextInfo(String key, String value) {
        if (key != null) {           
            if (value == null || value.isEmpty()) {
                if (contextInfo.containsKey(key)) {
                    contextInfo.remove(key);
                } 
            } else {
                contextInfo.put(key, value);
            }
        } else {
            logger.warn("Key is null. Context info not added.");
        }
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

    /**
     * Gets the conversation ID associated with the chat request.
     * 
     * @return the conversation ID
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Sets the conversation ID for the chat request.
     * 
     * @param conversationId the conversation ID
     */
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Map<String, String> getContextInfo() {
        return contextInfo;
    }

}

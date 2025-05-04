package org.symphonykernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Represents a chat response with message and status details.
 * 
 * <p>This class provides methods to set and retrieve response-related properties.
 * 
 * @version 1.0
 * @since 1.0
 * @author Cibin Jose
 */
public class ChatResponse {

    private String requestId;
    private String message;
    private String messageType;
    private String statusCode;
    private ArrayNode node;
    
    /**
     * Default constructor for ChatResponse.
     * Initializes an empty instance of the class.
     */
    public ChatResponse() {
       
    }

    /**
     * Constructor for ChatResponse.
     * 
     * @param message the message to initialize the ChatResponse with
     */
    public ChatResponse(String message) {
        this.message = message;       
    }

    /**
     * Gets the request ID of the chat response.
     * 
     * @return the request ID
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Sets the request ID for the chat response.
     * 
     * @param requestId the request ID
     */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * Sets the message type for the chat response.
     * 
     * @param message the message type
     */
    public void setMessageType(String message) {
        this.messageType = message;
    }

    /**
     * Gets the message type of the chat response.
     * 
     * @return the message type
     */
    public String getMessageType() {
        return messageType;
    }

    /**
     * Gets the message of the chat response.
     * 
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message for the chat response.
     * 
     * @param message the message
     */
    public void setMessage(String message) {
        this.message = message;
        if (messageType == null && this.message != null) {
            messageType = "Text";
        }
    }

    /**
     * Sets the data for the chat response.
     * 
     * @param message the data as an ArrayNode
     */
    public void setData(ArrayNode message) {
        this.node = message;
        if(this.node!=null)
        {
        if (isAdaptiveCard()) {
            messageType = "AdaptiveCard";
            this.message = node.toString();
            this.node = null;
        } else {
            String data = readAsText();
            if (data != null) {
                messageType = "Text";
                this.message = data;
                this.node = null;
            }
            else
            {
            	this.message=message.toPrettyString();
            	messageType = "JSON";
            }
        }
        }
    }

    /**
     * Gets the data of the chat response.
     * 
     * @return the data as an ArrayNode
     */
    public ArrayNode getData() {
        return node;
    }

    /**
     * Gets the status code of the chat response.
     * 
     * @return the status code
     */
    public String getStatusCode() {
        return statusCode;
    }

    /**
     * Sets the status code for the chat response.
     * 
     * @param statusCode the status code
     */
    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * Checks if the data represents an AdaptiveCard.
     * 
     * @return true if the data is an AdaptiveCard, false otherwise
     */
    public boolean isAdaptiveCard() {
        if (this.node != null) {
            if (this.node.isArray() && this.node.size() > 0) {
                JsonNode firstObject = this.node.get(0);
                if (firstObject.has("type")) {
                    return "AdaptiveCard".equals(firstObject.get("type").asText());
                }
            }
        }
        return false; // Return null if the array is empty, not an array, or "type" is missing.
    }

    /**
     * Reads the data as text.
     * 
     * @return the data as text, or null if not available
     */
    public String readAsText() {
        if (this.node != null) {
            if (this.node.isArray() && this.node.size() > 0) {
                JsonNode firstObject = this.node.get(0);
                if (firstObject.has("TextOutput")) {
                    return firstObject.get("TextOutput").asText();
                }
            }
        }
        return null; // Return null if the array is empty, not an array, or "TextOutput" is missing.
    }
}

package org.symphonykernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class ChatResponse {

    private String requestId;
    private String message;
    private String messageType;
    private String statusCode;
    private ArrayNode node;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public void setMessageType(String message) {
        this.messageType = message;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
        if (messageType == null && this.message != null) {
            messageType = "Text";
        }
    }

    public void setData(ArrayNode message) {
        this.node = message;
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
        }
    }

    public ArrayNode getData() {
        return node;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

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

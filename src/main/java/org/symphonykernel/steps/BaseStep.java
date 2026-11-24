/*
 * The MIT License
 *
 * Copyright 2025 cjose.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.symphonykernel.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IUserSessionBase;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.transformer.JsonTransformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import reactor.core.publisher.Flux;

/**
 *
 * @author cjose
 */
public abstract class BaseStep  implements IStep {

    private static final String STREAM_SCHEMA_VERSION = "1.0";
    private static final String EVENT_TYPE_CONTENT = "content";

    /** Logger instance. */
    protected static final Logger logger = LoggerFactory.getLogger(BaseStep.class);
    /** Jackson ObjectMapper for JSON processing. */
    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    IknowledgeBase knowledgeBase;
       
    @Autowired
    IUserSessionBase sessionBase;
    /**
     * Gets the response data from this step.
     *
     * @param ctx the execution context
     * @return the response data as an ArrayNode
     */
    protected ArrayNode getData(ExecutionContext ctx) {
        return getResponse(ctx).getData();	
    }
    @Override
    public Flux<String> getResponseStream(ExecutionContext ctx) {
		//TODO: Implement streaming response if needed
		 ArrayNode node = getData(ctx);
	     saveStepData(ctx, node);
	     return Flux.just(node.toString());
	}
    @Override
    public Flux<String> streamQueryByName(ExecutionContext context) {
        Knowledge kb = knowledgeBase.GetByName(context.getName());
        if (kb != null) {
            prepareNamedStep(context, kb);
           return getResponseStream(context);
        }
        return Flux.empty();
    }
    @Override
    public JsonNode executeQueryByName(ExecutionContext context) {
        final ArrayNode[] array = new ArrayNode[1];
        Knowledge kb = knowledgeBase.GetByName(context.getName());
        if (kb != null) {
            prepareNamedStep(context, kb);
            ChatResponse a = getResponse(context);
            if (a != null) {
                if (a.getData() != null) {
                    array[0] = a.getData();
                } else if (a.getMessage() != null) {
                    array[0] = JsonNodeFactory.instance.arrayNode().add(createTextNode(a.getMessage()));
                }
            }
        }
        return array[0];
    }

    private void prepareNamedStep(ExecutionContext context, Knowledge kb) {
        context.setKnowledge(kb);
        if (context.getConvert() && kb.getParams() != null && !kb.getParams().isBlank()) {
            try {
                context.setVariables(new JsonTransformer().compareAndReplaceJson(kb.getParams(), context.getVariables()));
            } catch (Exception e) {
                logger.error("Error in Json Transformation", e);
            }
        }
    }
    /**
     * Saves step data as a JsonNode to the session store.
     *
     * @param context the execution context
     * @param data the data to save
     */
    public void saveStepData(ExecutionContext context,JsonNode data)
    {
        try {
        	sessionBase.saveRequestDetails(context.getRequestId(), context.getName(),data.toString());
            } catch (Exception e) {
                logger.error("Error saving step data", e);
            }
    }
    /**
     * Saves step data as a String to the session store.
     *
     * @param context the execution context
     * @param data the data to save
     */
    public void saveStepData(ExecutionContext context,String data)
    {
        try {
        	sessionBase.saveRequestDetails(context.getRequestId(), context.getName(),data);
            } catch (Exception e) {
                logger.error("Error saving step data", e);
            }
    }
     /**
      * Parses a plugin definition JSON string into a JsonNode.
      *
      * @param plugindef the plugin definition JSON
      * @return the parsed JsonNode
      */
     protected JsonNode getParamNode(String plugindef) {
        JsonNode paramNode;
        try {
            paramNode = objectMapper.readTree(plugindef);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing plugin definition JSON", e);
            throw new IllegalArgumentException("Invalid plugin definition JSON", e);
        }
        return paramNode;
    }
    
    /**
     * Creates a JSON object node with a TextOutput field.
     *
     * @param jsonString the text value
     * @return an ObjectNode containing the TextOutput field
     */
    protected JsonNode createTextNode(String jsonString) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("TextOutput", jsonString);
        return objectNode;
    }

    /**
     * Builds a strict JSON stream envelope.
     *
     * @param ctx execution context
     * @param eventType event type (status/content/error/complete)
     * @param stage stream stage
     * @param message human-friendly message
     * @param content content chunk when eventType is content
     * @param sequence event sequence number
     * @param elapsedMs optional elapsed milliseconds
     * @return serialized JSON envelope
     */
    protected String streamEnvelope(
            ExecutionContext ctx,
            String eventType,
            String stage,
            String message,
            String content,
            long sequence,
            Long elapsedMs) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("schemaVersion", STREAM_SCHEMA_VERSION);
        node.put("eventType", eventType == null ? "status" : eventType);
        node.put("stage", stage == null ? "unknown" : stage);
        node.put("sequence", sequence);
        node.put("timestamp", java.time.Instant.now().toString());
        node.put("source", getClass().getSimpleName());
        node.put("requestId", ctx != null ? ctx.getRequestId() : "");
        String conversationId = "";
        if (ctx != null && ctx.getRequest() != null && ctx.getRequest().getConversationId() != null) {
            conversationId = ctx.getRequest().getConversationId();
        }
        node.put("conversationId", conversationId);
        node.put("message", message == null ? "" : message);
        if (content != null) {
            node.put("content", content);
        }
        if (elapsedMs != null) {
            node.put("elapsedMs", elapsedMs);
        }
        return node.toString();
    }

    protected String streamStatus(ExecutionContext ctx, String stage, String message, long sequence) {
        return streamEnvelope(ctx, "status", stage, message, null, sequence, null);
    }

    protected String streamError(ExecutionContext ctx, String stage, String message, long sequence) {
        return streamEnvelope(ctx, "error", stage, message, null, sequence, null);
    }

    protected String streamContent(ExecutionContext ctx, String stage, String content, long sequence) {
        return streamEnvelope(ctx, EVENT_TYPE_CONTENT, stage, "", content, sequence, null);
    }

    protected String streamComplete(ExecutionContext ctx, String stage, String message, long sequence) {
        return streamEnvelope(ctx, "complete", stage, message, null, sequence, null);
    }

    protected boolean isStreamEnvelope(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(chunk);
            return node.isObject() && node.has("schemaVersion") && node.has("eventType") && node.has("stage");
        } catch (Exception ex) {
            return false;
        }
    }

    protected String extractContentFromEnvelope(String chunk) {
        if (!isStreamEnvelope(chunk)) {
            return chunk;
        }
        try {
            JsonNode node = objectMapper.readTree(chunk);
            String eventType = node.path("eventType").asText();
            if (!EVENT_TYPE_CONTENT.equalsIgnoreCase(eventType)) {
                return "";
            }
            return node.path("content").asText("");
        } catch (Exception ex) {
            return "";
        }
    }
      /**
     * Parses a JSON string into a JsonNode.
     *
     * @param jsonString the JSON string to parse
     * @return the parsed JsonNode
     */
    public JsonNode parseJson(String jsonString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            if (jsonString != null) {
                if (jsonString.startsWith("```json")) {
                    jsonString = jsonString.replace("```json", "").replace("```", "");
                }

            } else {
                jsonString = "";
            }
            if (jsonString.startsWith("[") || jsonString.startsWith("{")) {
                return objectMapper.readTree(jsonString);
            } else {
                return createTextNode(jsonString);
            }

        } catch (Exception e) {

            return createTextNode(jsonString);
        }
    }
     /**
      * Wraps a string result into a ChatResponse with a single-element data array.
      *
      * @param result the result string
      * @return a ChatResponse containing the result
      */
     protected ChatResponse makeResponseObject(String result) {
        ArrayNode jsonArray = objectMapper.createArrayNode();
        jsonArray.add(new TextNode(result));
        ChatResponse response = new ChatResponse();
        response.setData(jsonArray);
        return response;
    }

}

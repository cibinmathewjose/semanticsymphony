package org.symphonykernel.steps;

import java.io.StringWriter;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;

@Service
public class VelocityStep extends BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(VelocityStep.class);
    
    static {
        // Initialize Velocity engine
        Velocity.init();
    }

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {
        
        JsonNode input = ctx.getVariables();
        Map<String, JsonNode> resolvedValues =   ctx.getResolvedValues();     
        resolvedValues.put("input", input);
        Knowledge template = ctx.getKnowledge();
        String templateData = template.getData();
        
        try {
            // Create Velocity context and populate with resolved values
            VelocityContext velocityContext = new VelocityContext();
            
            for (Map.Entry<String, JsonNode> entry : resolvedValues.entrySet()) {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                
                // Convert JsonNode to appropriate Java object for Velocity
                Object contextValue = convertJsonNodeToObject(value);
                velocityContext.put(key, contextValue);
            }
            
            // Process template
            StringWriter writer = new StringWriter();
            boolean result = Velocity.evaluate(velocityContext, writer, "VelocityTemplate", templateData);
            
            if (!result) {
                logger.error("Failed to process Velocity template");
                throw new RuntimeException("Velocity template processing failed");
            }
            
            String renderedText = writer.toString();
            logger.info("Rendered template output: " + renderedText);
            
            // Create response with rendered text
            ArrayNode jsonArray = objectMapper.createArrayNode();
            jsonArray.add(new TextNode(renderedText));
            
            ChatResponse response = new ChatResponse();
            response.setData(jsonArray);
            saveStepData(ctx, jsonArray);
            return response;
            
        } catch (Exception e) {
            logger.error("Error processing Velocity template", e);
            ArrayNode errorArray = objectMapper.createArrayNode();
            errorArray.add(new TextNode("Error processing template: " + e.getMessage()));
            
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setData(errorArray);
            return errorResponse;
        }
    }
    
    private Object convertJsonNodeToObject(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            return node.asDouble();
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isArray()) {
            return node;
        } else if (node.isObject()) {
            return node;
        }
        return node.toString();
    }
}

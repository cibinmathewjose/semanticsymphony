package org.symphonykernel.steps;

import java.io.StringWriter;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.config.VelocityEngineConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * A step implementation that processes Apache Velocity templates.
 * <p>
 * This service uses the Velocity template engine to render templates with data
 * from the execution context. It merges resolved values and input variables
 * with Velocity engine configuration properties to generate the final output.
 * </p>
 */
@Service
public class VelocityStep extends BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(VelocityStep.class);
     @Autowired
    private VelocityEngineConfig velocityEngineConfig;
    static {
        // Initialize Velocity engine
        Velocity.init();
    }

    /**
     * Processes the Velocity template and returns the rendered output.
     * <p>
     * This method retrieves the template from the knowledge base, populates
     * the Velocity context with resolved values and configuration properties,
     * then evaluates the template to produce the final rendered text.
     * </p>
     *
     * @param ctx the execution context containing variables, resolved values, and knowledge
     * @return a ChatResponse containing the rendered template output or error message
     */
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

            Map<String, Object> allParams = velocityEngineConfig.getProperties();
              allParams.forEach((key, value) -> {
            velocityContext.put(key, value);
        });

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
    
    /**
     * Converts a JsonNode to an appropriate Java object for Velocity context.
     * <p>
     * This method handles conversion of different JsonNode types (text, number,
     * boolean, array, object) to their corresponding Java representations.
     * </p>
     *
     * @param node the JsonNode to convert
     * @return the converted Java object (String, Double, Boolean, or JsonNode for complex types)
     */
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

package org.symphonykernel.transformer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

/**
 * TemplateResolver is a utility class for resolving placeholders in text templates.
 * It provides methods to check for placeholders and replace them with values from a context map.
 */
@Component
public class TemplateResolver {
    private static final Logger logger = LoggerFactory.getLogger(TemplateResolver.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\$(.*?)}}");
    private static final Pattern JSONPATH_FUNC_PATTERN = Pattern.compile("^(.+?)\\.jsonpath\\((.+)\\)$");
    /**
     * Constant used as a default value when no data is found for a placeholder.
     */
    public static final String NO_DATA_FOUND = "{NO_DATA_FOUND}";
     private static final JsonTransformer transformer = new JsonTransformer();
     private static final ObjectMapper jsonMapper = new ObjectMapper();
    /**
     * Prefix used to indicate that the resolved value is in JSON format.
     */
    
    @Autowired
    private Environment environment;
 

    /**
     * Checks if the given text contains placeholders.
     *
     * @param text the text to check for placeholders
     * @return true if placeholders are found, false otherwise
     */
    public static boolean hasPlaceholders(String text) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        return matcher.find();
    }
    /**
     * Resolves placeholders in the given text using environment properties.
     *
     * @param text the text containing placeholders
     * @return the text with placeholders resolved
     */
    public String resolvePlaceholders(String text)
    {
        return  resolvePlaceholders(text, null);
    }
    /**
     * Resolves placeholders in the given text using the provided context map.
     *
     * @param text the text containing placeholders
     * @param context a map containing placeholder keys and their corresponding values
     * @return the text with placeholders replaced by their corresponding values
     */
    public String resolvePlaceholders(String text, Map<String, JsonNode> context) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1).trim(); 
            if (expression.trim().toLowerCase().startsWith("env.")) {
                String envVar = expression.substring(4).trim();
                String envValue = environment.getProperty(envVar);              
                if (envValue == null) {
                    envValue = NO_DATA_FOUND;
                }
                matcher.appendReplacement(result, Matcher.quoteReplacement(envValue));
                logger.info("Resolved environment variable: {} with value: {}", envVar, envValue);
            }
            else
            {
                if(context!=null)
                {
                    String replacement = resolveExpression(expression, context);           
                    matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                }
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String resolveExpression(String expression, Map<String, JsonNode> context) {
        JsonNode value = null;
        boolean compress = false;
        if(expression.startsWith("|"))
        {
            expression=expression.substring(1).trim();  
            compress=true;
        }

        // Check for JsonPath function syntax: variable.jsonpath($.path.expr)
        Matcher jpMatcher = JSONPATH_FUNC_PATTERN.matcher(expression);
        if (jpMatcher.matches()) {
            String varPath = jpMatcher.group(1).trim();
            String jsonPathExpr = jpMatcher.group(2).trim();
            value = getValueByPath(varPath, context);
            value = applyJsonPath(value, jsonPathExpr);
        } else if (expression.contains("?") && expression.contains(":")) {
            String[] parts = expression.split("[\\?:]", 3);
            if (parts.length == 3) {
                String condition = parts[0].trim().toLowerCase();      
                String trueExpr = parts[1].trim().toLowerCase();      
                String falseExpr = parts[2].trim().toLowerCase();      

                value = getValueByPath(condition, context);
                if (value != null && !value.isNull() && !value.asText().isEmpty()) {
                    value = getValueByPath(trueExpr, context);
                } else {
                    value = getValueByPath(falseExpr, context);
                }
            }
        } else {
            // fallback if not a ternary expression
            value = getValueByPath(expression, context);
        }
        if (isJsonNodeNullorEmpty(value)) {         
            return NO_DATA_FOUND;
        } else {
            if(compress)
            {
                String data=  transformer.compress(value);
                 return JsonTransformer.LLM_OPTIMIZED_DATA+data;
            }
            else
                return JsonTransformer.JSON+ JsonTransformer.getCleanedJsonNode(value).toPrettyString();
        }
    }

    /**
     * Applies a JsonPath expression to a JsonNode value. If the value is a text node
     * containing an escaped JSON string, it is automatically parsed before evaluation.
     *
     * @param node          the JsonNode to evaluate against
     * @param jsonPathExpr  a JsonPath expression (e.g. {@code $.results[0].name})
     * @return the result as a JsonNode, or null if not found
     */
    private static JsonNode applyJsonPath(JsonNode node, String jsonPathExpr) {
        if (isJsonNodeNullorEmpty(node)) {
            return null;
        }
        try {
            // If the node is a text node, try to parse it as JSON (handles escaped JSON strings)
            JsonNode jsonToQuery = unescapeIfNeeded(node);
            String jsonString = jsonToQuery.toString();
            Object result = JsonPath.read(jsonString, jsonPathExpr);
            if (result == null) {
                return null;
            }
            // Convert the JsonPath result back to a Jackson JsonNode
            return jsonMapper.readTree(jsonMapper.writeValueAsString(result));
        } catch (PathNotFoundException e) {
            logger.debug("JsonPath '{}' not found in node", jsonPathExpr);
            return null;
        } catch (Exception e) {
            logger.warn("Error evaluating JsonPath '{}': {}", jsonPathExpr, e.getMessage());
            return null;
        }
    }

    /**
     * If the node is a text node whose content looks like JSON (starts with { or [),
     * parses the text into a proper JsonNode. This handles escaped JSON strings
     * such as those stored as string values in context maps.
     */
    private static JsonNode unescapeIfNeeded(JsonNode node) {
        if (node != null && node.isTextual()) {
            String text = node.asText().trim();
            if ((text.startsWith("{") && text.endsWith("}")) 
                    || (text.startsWith("[") && text.endsWith("]"))) {
                try {
                    return jsonMapper.readTree(text);
                } catch (Exception e) {
                    // Not valid JSON — return the original node
                    logger.debug("Text looks like JSON but failed to parse: {}", e.getMessage());
                }
            }
        }
        return node;
    }
    /**
     * Checks if a JsonNode is null, an empty object, or an empty array.
     *
     * @param node the JsonNode to check
     * @return true if the node is null, an empty object, or an empty array, false otherwise
     */
    public static boolean isJsonNodeNullorEmpty(JsonNode node) {
        return node == null || node.isNull() || (node.isObject() && node.size() == 0) || (node.isArray() && node.size() == 0);
    }

    private static JsonNode getValueByPath(String path, Map<String, JsonNode> context) {
        String[] parts = path.split("\\.");
        JsonNode current = context.get(parts[0].trim().toLowerCase());
        for (int i = 1; i < parts.length && current != null; i++) {
            current = current.get(parts[i].trim());
        }
        return current;
    }
}

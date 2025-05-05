package org.symphonykernel.transformer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * TemplateResolver is a utility class for resolving placeholders in text templates.
 * It provides methods to check for placeholders and replace them with values from a context map.
 */
public class TemplateResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\$(.*?)}}");

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
     * Resolves placeholders in the given text using the provided context map.
     *
     * @param text the text containing placeholders
     * @param context a map containing placeholder keys and their corresponding values
     * @return the text with placeholders replaced by their corresponding values
     */
    public static String resolvePlaceholders(String text, Map<String, JsonNode> context) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1).trim(); 
            String replacement = resolveExpression(expression, context);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static String resolveExpression(String expression, Map<String, JsonNode> context) {
        JsonNode value = null;
        if (expression.contains("?") && expression.contains(":")) {
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
        if (value != null && !value.isNull()) {
            return value.toString();
        } else {
            return "{}";
        }
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

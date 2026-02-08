package org.symphonykernel.transformer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
/**
 * PlatformHelper is a service class that provides utility methods for transforming and processing JSON data.
 * <p>
 * This class includes methods for adaptive card generation, JSON comparison, placeholder replacement, 
 * and resolving payloads based on resolver definitions.
 * </p>
 * 
 * @version 1.0
 * @since 1.0
 */
public class PlatformHelper {

    private static final Logger logger = LoggerFactory.getLogger(PlatformHelper.class);

    /**
     * Generates an adaptive card JSON based on the provided mapping template.
     *
     * @param jsonNode the input JSON data
     * @param mappingTemplate the mapping template for adaptive card generation
     * @return the generated adaptive card JSON as a string
     */
    public String generateAdaptiveCardJson(JsonNode jsonNode, String mappingTemplate) {

        JsonNode data = null;
        if (jsonNode.isObject()) {
            Iterator<Entry<String, JsonNode>> fields = jsonNode.fields();
            while (fields.hasNext()) {
                Entry<String, JsonNode> field = fields.next();
                data = field.getValue();
                break;
            }
        }

        if (data == null) {
            return "";
        }

        String cardTemplateJson = "{\"type\":\"AdaptiveCard\",\"$schema\":\"http://adaptivecards.io/schemas/adaptive-card.json\",\"version\":\"1.0\",\"body\":[]}";

        //String mappingJson = "{\"countryName\":{\"type\":\"TextBlock\",\"dataPath\":\"/country/0/countryName\",\"label\":\"Country\",\"weight\":\"Bolder\"},\"acceptabilityCountry\":{\"type\":\"TextBlock\",\"dataPath\":\"/acceptabilityCountry\",\"label\":\"Acceptability\"},\"waiverExists\":{\"type\":\"TextBlock\",\"dataPath\":\"/waiverExists\",\"label\":\"Waiver Exists\"},\"tentativeRemarks\":{\"type\":\"TextBlock\",\"dataPath\":\"/tentativeRemarks\",\"label\":\"Remarks\",\"wrap\":true}}";
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode cardTemplate;
        JsonNode mapping;
        ArrayNode body;
        try {
            cardTemplate = (ObjectNode) mapper.readTree(cardTemplateJson);
            mapping = mapper.readTree(mappingTemplate);
            body = (ArrayNode) cardTemplate.get("body");
            body.removeAll();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "";
        }

        for (JsonNode item : data) {
            ObjectNode container = JsonNodeFactory.instance.objectNode();
            container.put("type", "Container");

            ArrayNode items = JsonNodeFactory.instance.arrayNode();

            Iterator<String> fieldNames = mapping.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode mappingNode = mapping.get(fieldName);

                String templateType = mappingNode.get("type").asText();
                String dataPath = mappingNode.get("dataPath").asText();
                String label = mappingNode.has("label") ? mappingNode.get("label").asText() : fieldName;
                String weight = mappingNode.has("weight") ? mappingNode.get("weight").asText() : "Default";
                boolean wrap = mappingNode.has("wrap") && mappingNode.get("wrap").asBoolean();

                ObjectNode element = JsonNodeFactory.instance.objectNode();
                element.put("type", templateType);

                JsonNode dataValueNode = item.at(dataPath);
                String dataValue = dataValueNode.isTextual() ? dataValueNode.asText() : dataValueNode.toString();

                if (templateType.equals("TextBlock")) {
                    element.put("text", label + ": " + dataValue);
                    if (!weight.equals("Default")) {
                        element.put("weight", weight);
                    }
                    if (wrap) {
                        element.put("wrap", wrap);
                    }

                } else {
                    element.put("value", dataValue);
                }

                items.add(element);
            }

            container.set("items", items);
            body.add(container);

            // Add a separator
            ObjectNode separator = JsonNodeFactory.instance.objectNode();
            separator.put("type", "Container");
            ObjectNode separatorLine = JsonNodeFactory.instance.objectNode();
            separatorLine.put("type", "ColumnSet");
            ObjectNode column = JsonNodeFactory.instance.objectNode();
            column.put("type", "Column");
            ObjectNode sep = JsonNodeFactory.instance.objectNode();
            sep.put("type", "TextBlock");
            sep.put("text", "--------------------------------");
            ArrayNode emptyArray = JsonNodeFactory.instance.arrayNode();
            column.set("items", emptyArray);
            // column.get("items").add(sep);
            ArrayNode columns = JsonNodeFactory.instance.arrayNode();
            columns.add(column);
            separatorLine.set("columns", columns);
            separator.set("items", JsonNodeFactory.instance.arrayNode());
            //  separator.get("items").add(separatorLine);
            body.add(separator);
        }

        return cardTemplate.toString();
    }

    /**
     * Compares a template JSON with a payload JSON and replaces values based on the template.
     *
     * @param templateJson the template JSON as a string
     * @param payloadNode the payload JSON as a JsonNode
     * @return a JsonNode with replaced values
     * @throws IOException if an error occurs during JSON processing
     */
    public JsonNode compareAndReplaceJsonv2(String templateJson, JsonNode payloadNode) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode templateNode = objectMapper.readTree(templateJson);
        ObjectNode resultNode = objectMapper.createObjectNode();

        Iterator<Map.Entry<String, JsonNode>> templateFields = templateNode.fields();

        while (templateFields.hasNext()) {
            Map.Entry<String, JsonNode> templateField = templateFields.next();
            String fieldName = templateField.getKey();
            JsonNode fieldTemplate = templateField.getValue();

            if (fieldTemplate.isArray() && fieldTemplate.size() > 0 && fieldTemplate.get(0).has("type")) {
                // Handle array of typed items
                ArrayNode arrayResult = objectMapper.createArrayNode();

                for (JsonNode payloadItem : payloadNode) {
                    JsonNode matchedValue = findMatchingFieldv3(fieldName, payloadItem);
                    if (matchedValue != null) {
                        String expectedType = fieldTemplate.get(0).get("type").asText();
                        switch (expectedType) {
                            case "number":
                                arrayResult.add(Long.parseLong(matchedValue.asText()));
                                break;
                            case "boolean":
                                arrayResult.add(Boolean.parseBoolean(matchedValue.asText()));
                                break;
                            default:
                                arrayResult.add(matchedValue.asText());
                                break;
                        }
                    }
                }

                resultNode.set(fieldName, arrayResult);

            } else {
                // Handle single value
                JsonNode matchedValue = findMatchingFieldv3(fieldName, payloadNode.isArray() ? payloadNode.get(0) : payloadNode);
                if (matchedValue != null) {
                    if (fieldTemplate.has("type") && "number".equals(fieldTemplate.get("type").asText())) {
                        ((ObjectNode) resultNode).put(fieldName, Long.parseLong(matchedValue.asText()));
                    } else {
                        ((ObjectNode) resultNode).put(fieldName, matchedValue.asText());
                    }
                }
            }
        }

        return resultNode;
    }

    /**
     * Compares a template JSON with a payload JSON and replaces values based on the template.
     *
     * @param templateJson the template JSON as a string
     * @param payloadNode the payload JSON as a JsonNode
     * @return a JsonNode with replaced values
     * @throws IOException if an error occurs during JSON processing
     */
    public JsonNode compareAndReplaceJson(String templateJson, JsonNode payloadNode) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Parse the template JSON and payload JSON
        JsonNode templateNode = objectMapper.readTree(templateJson);

        // Prepare a new JSON node to hold the result
        JsonNode resultNode = objectMapper.createObjectNode();

        // Traverse through each field in the template JSON
        Iterator<Map.Entry<String, JsonNode>> templateFields = templateNode.fields();

        while (templateFields.hasNext()) {
            Map.Entry<String, JsonNode> templateField = templateFields.next();
            String templateFieldName = templateField.getKey();

            // Find the best matching field name from the payload JSON
            JsonNode payloadValue = findMatchingFieldv2(templateFieldName, payloadNode);

            if (payloadValue != null) {
                // If a match is found, convert the value to the expected type in the template
                if (templateField.getValue().has("type") && "number".equals(templateField.getValue().get("type").asText())) {
                    ((ObjectNode) resultNode).put(templateFieldName, Long.parseLong(payloadValue.asText()));
                } else {
                    ((ObjectNode) resultNode).put(templateFieldName, payloadValue.asText());
                }
                // Add other type checks here (e.g., "string", "boolean", etc.)
            }
        }

        // Return the result as a string
        return resultNode;
    }

    /**
     * Replaces placeholders in a query string with values from user data based on parameter definitions.
     *
     * @param query the query string containing placeholders
     * @param paramDefJson the JSON string defining parameter types
     * @param userDataNode the JSON node containing user data
     * @return the query string with placeholders replaced
     * @throws IOException if an error occurs during JSON processing
     */
    public String replacePlaceholders(String query, String paramDefJson, JsonNode userDataNode) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Parse parameter definitions
        JsonNode paramDefNode = objectMapper.readTree(paramDefJson);
        Map<String, String> paramTypes = new HashMap<>();
       
        paramDefNode.fields().forEachRemaining(entry -> {
        	if(!entry.getValue().isArray())
        		paramTypes.put(entry.getKey(), entry.getValue().get("type").asText());
        	else
        	{
        		paramTypes.put(entry.getKey(), entry.getValue().get(0).get("type").asText());
        	}
        });

        // Replace placeholders
        String replacedQuery = query;
        Pattern pattern = Pattern.compile("\\{([^{}]+)\\}");
        Matcher matcher = pattern.matcher(replacedQuery);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (userDataNode.has(placeholder)) {
                JsonNode valueNode = userDataNode.get(placeholder);
                if(!valueNode.isArray())
                {
	                String value = read(paramTypes, placeholder, valueNode);                
	                matcher.appendReplacement(sb, value);
                }
                else
                {
                	String value=null;
                	   for (JsonNode val : valueNode) {
                		   value = value!=null?value+","+ read(paramTypes, placeholder, val): read(paramTypes, placeholder, val);
                	   }
                	if(value!=null)
                		matcher.appendReplacement(sb, value);
                	else
                		matcher.appendReplacement(sb, "");	
                }
            } else {
                matcher.appendReplacement(sb, "null"); // Or handle missing values differently
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

	private String read(Map<String, String> paramTypes, String placeholder, JsonNode valueNode) {
		String value;
		if ("string".equals(paramTypes.get(placeholder))) {
		    value = "'" + valueNode.asText() + "'";
		} else if ("number".equals(paramTypes.get(placeholder))) {
		    value = valueNode.asText();
		} else {
		    value = valueNode.asText(); // Default to string if type is unknown
		}
		return value;
	}

    private static JsonNode findMatchingFieldv3(String templateFieldName, JsonNode payloadNode) {
       
        int bestDistance = Integer.MAX_VALUE;
        JsonNode bestValue = null;

        // Create a LevenshteinDistance instance
        LevenshteinDistance levenshteinDistance =  LevenshteinDistance.getDefaultInstance();
        JsonNode item = payloadNode;
        if (payloadNode.isArray()) {
            item = payloadNode.get(0);
        }
        // Iterate over payload array and find the best match using fuzzy matching (Levenshtein distance)
        //for (JsonNode item : payloadNode) {
        Iterator<Map.Entry<String, JsonNode>> payloadFields = item.fields();
        while (payloadFields.hasNext()) {
            Map.Entry<String, JsonNode> payloadField = payloadFields.next();
            String payloadFieldName = payloadField.getKey();

            // Calculate the Levenshtein distance between the template field and the payload field name
            int distance = levenshteinDistance.apply(templateFieldName.toLowerCase(), payloadFieldName.toLowerCase());

            // If we find a better match (smaller distance), store it
            if (distance < bestDistance) {
                bestDistance = distance;
                
                bestValue = payloadField.getValue();
            }
        }
        //}
        return bestValue;
    }

    private static JsonNode findMatchingFieldv2(String templateFieldName, JsonNode payloadNode) {
       
        int bestDistance = Integer.MAX_VALUE;
        JsonNode bestValue = null;

        // Create a LevenshteinDistance instance
        LevenshteinDistance levenshteinDistance = LevenshteinDistance.getDefaultInstance();
        JsonNode item = payloadNode;
        if (payloadNode.isArray()) {
            item = payloadNode.get(0);
        }
        // Iterate over payload array and find the best match using fuzzy matching (Levenshtein distance)
        //for (JsonNode item : payloadNode) {
        Iterator<Map.Entry<String, JsonNode>> payloadFields = item.fields();
        while (payloadFields.hasNext()) {
            Map.Entry<String, JsonNode> payloadField = payloadFields.next();
            String payloadFieldName = payloadField.getKey();

            // Calculate the Levenshtein distance between the template field and the payload field name
            int distance = levenshteinDistance.apply(templateFieldName.toLowerCase(), payloadFieldName.toLowerCase());

            // If we find a better match (smaller distance), store it
            if (distance < bestDistance) {
                bestDistance = distance;
               
                bestValue = payloadField.getValue();
            }
        }
        //}
        return bestValue;
    }

    /**
     * Resolves a payload by replacing values based on a resolver JSON definition.
     *
     * @param key the key to resolve
     * @param value the value to resolve
     * @param resolverNode the resolver JSON node
     * @return a resolved JSON node
     */
    public JsonNode resolvePayload(String key, Object value, JsonNode resolverNode) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode resultNode = objectMapper.createObjectNode();

        if (resolverNode != null && resolverNode.isObject()) {
            resolverNode.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode fieldTypeNode = entry.getValue();

                if (fieldName.equals(key)) {
                    if (value instanceof String) {
                        resultNode.put(fieldName, (String) value);
                    } else if (value instanceof Integer || value instanceof Long || value instanceof Double || value instanceof Float) {
                        resultNode.put(fieldName, ((Number) value).doubleValue());
                    } else if (value instanceof Boolean) {
                        resultNode.put(fieldName, (Boolean) value);
                    } else if (value == null) {
                        resultNode.putNull(fieldName);
                    } else {
                        resultNode.set(fieldName, objectMapper.valueToTree(value));
                    }

                } else {
                    resultNode.set(fieldName, fieldTypeNode);
                }
            });
        }
        return resultNode;
    }

    /**
     * Replaces values in a JSON node based on a resolver JSON definition and parameters.
     *
     * @param resolverJson the resolver JSON as a string
     * @param params the parameters to replace in the JSON
     * @return a resolved JSON node
     */
    public JsonNode replaceJsonValue(String resolverJson, Object... params) {
        ObjectMapper objectMapper = new ObjectMapper();

        // Create the result JSON
        ObjectNode resultNode = objectMapper.createObjectNode();
        // Parse resolver JSON
        JsonNode resolverNode;
        try {
            resolverNode = objectMapper.readTree(resolverJson);

            // Iterate through resolver definition fields
            Iterator<Map.Entry<String, JsonNode>> resolverFields = resolverNode.fields();
            int paramIndex = 0; // to track params array

            while (resolverFields.hasNext()) {
                Map.Entry<String, JsonNode> resolverField = resolverFields.next();
                String resultKey = resolverField.getKey();

                // Get the "type" field from resolver and handle accordingly
                JsonNode typeNode = resolverField.getValue().get("type");
                if (typeNode != null && typeNode.isTextual()) {
                    String type = typeNode.asText();

                    // Handle the replacement based on type
                    if (paramIndex < params.length) {
                        Object paramValue = params[paramIndex++]; // Get the current param value

                        if ("number".equals(type) && paramValue instanceof Number) {
                            resultNode.put(resultKey, ((Number) paramValue).intValue());
                        } else if ("string".equals(type) && paramValue instanceof String) {
                            resultNode.put(resultKey, (String) paramValue);
                        } else {
                            resultNode.put(resultKey, paramValue!=null?paramValue.toString():null); // Default case, fallback to string
                        }
                    }
                }
            }
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // Convert the result node to a JSON string and return
        return resultNode;
    }

    /**
     * Resolves a payload array by replacing values based on a resolver JSON definition.
     *
     * @param payloadArray the payload array as a JsonNode
     * @param resolverJson the resolver JSON as a string
     * @return a resolved JSON node
     * @throws IOException if an error occurs during JSON processing
     */
    public JsonNode resolvePayload(ArrayNode payloadArray, String resolverJson) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Parse resolver JSON
        JsonNode resolverNode = objectMapper.readTree(resolverJson);

        // Create the result JSON
        ObjectNode resultNode = objectMapper.createObjectNode();

        // Assuming payload is an array with a single object containing one field.
        if (payloadArray.isArray() && payloadArray.size() > 0) {
            JsonNode firstObject = payloadArray.get(0);

            // Get the first field from the object
            Iterator<Map.Entry<String, JsonNode>> fields = firstObject.fields();
            if (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String payloadValue = field.getValue().asText();

                // Iterate through resolver definition fields. Assuming only one field in resolver definition.
                Iterator<Map.Entry<String, JsonNode>> resolverFields = resolverNode.fields();
                if (resolverFields.hasNext()) {
                    Map.Entry<String, JsonNode> resolverField = resolverFields.next();
                    String resultKey = resolverField.getKey();

                    // Check resolver type
                    JsonNode typeNode = resolverField.getValue().get("type");
                    if (typeNode != null && typeNode.isTextual()) {
                        String type = typeNode.asText();

                        if ("number".equals(type)) {
                            try {
                                int intValue = Integer.parseInt(payloadValue);
                                resultNode.put(resultKey, intValue);
                            } catch (NumberFormatException e) {
                                resultNode.put(resultKey, payloadValue); // Fallback to string if parsing fails
                            }
                        } else if ("string".equals(type)) {
                            resultNode.put(resultKey, payloadValue);
                        } else {
                            resultNode.put(resultKey, payloadValue); // Default to string if type is unknown
                        }
                    } else {
                        resultNode.put(resultKey, payloadValue); // Default to string if type is missing
                    }
                }
            }
        }

        return resultNode;
    }

}

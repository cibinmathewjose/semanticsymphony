package org.symphonykernel.transformer;

import java.util.Iterator;
import java.util.Map;

import org.apache.commons.text.similarity.LevenshteinDistance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class JsonTransformer {

    private final ObjectMapper mapper = new ObjectMapper();

    public JsonNode compareAndReplaceJson(String templateJson, JsonNode payloadNode) throws Exception {
        JsonNode templateNode = mapper.readTree(templateJson);
        JsonNode result = processJson(null, templateNode, payloadNode, templateNode.isArray());
        return result;
    }

    public Object getMatchingFieldValue(String fieldName, JsonNode templateNode, JsonNode payloadNode)  {
        if (templateNode == null || payloadNode == null || !templateNode.has(fieldName)) {
            return null;
        }

        JsonNode fieldTemplate = templateNode.get(fieldName);
        if (!fieldTemplate.has("type")) {
            return null;
        }

        String expectedType = fieldTemplate.get("type").asText();
        JsonNode bestMatch = findBestMatch(fieldName, payloadNode);

        if (bestMatch != null) {
            switch (expectedType) {
                case "number":
                if (bestMatch.isNumber()) {
                    return bestMatch.numberValue();
                } else if (bestMatch.isTextual()) {
                    try {
                        String textValue = bestMatch.asText().trim();
                        if( !textValue.contains(".") )
                        	return Integer.parseInt(textValue);                        	 
                        else
                        	return Double.parseDouble(textValue);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                return null;
                case "boolean":
                    return bestMatch.isBoolean() ? bestMatch.booleanValue() : null;
                case "string":
                    return bestMatch.isTextual() ? bestMatch.textValue() : null;
                case "array":
                    return bestMatch.isArray() ? bestMatch : null;
                case "object":
                    return bestMatch.isObject() ? bestMatch : null;
                default:
                    return null;
            }
        }

        return null;
    }

    public JsonNode processJson(String parentNode, JsonNode inputNode, JsonNode payloadNode, boolean IsInArray) {
        if (isTypedNode(inputNode)) {
            return processValueNode(parentNode, inputNode, payloadNode, IsInArray);
        } else if (inputNode.isObject()) {
            ObjectNode resultNode = mapper.createObjectNode();
            inputNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode childNode = inputNode.get(fieldName);
                if (isTypedNode(childNode)) 
                	resultNode.set(fieldName, processValueNode(parentNode, inputNode, payloadNode, IsInArray));
                else
                	resultNode.set(fieldName, processJson(fieldName, childNode, payloadNode, childNode.isArray()));
            });
            return resultNode;
        } else if (inputNode.isArray()) {
            if (isTypedArray(inputNode)) {
                return processValueArrayNode(parentNode, inputNode, payloadNode, IsInArray);
            } else {
                ArrayNode resultArray = mapper.createArrayNode();
                for (JsonNode item : inputNode) {
                    resultArray.add(processJson(parentNode, item, payloadNode, true));
                }
                return resultArray;
            }
        } else {
            return processValueNode(parentNode, inputNode, payloadNode, IsInArray);
        }
    }

    private JsonNode processValueNode(String parentNodeforArray, JsonNode valueNode, JsonNode payloadNode, boolean IsInArray) {
        if (isTypedNode(valueNode)) {
            ObjectNode resultNode = mapper.createObjectNode();
            valueNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode fieldTemplate = valueNode.get(fieldName);
                if (fieldTemplate.has("type")) {
                    String expectedType = fieldTemplate.get("type").asText();
                    String fieldNameToMatch = IsInArray ? parentNodeforArray : fieldName;
                    JsonNode bestMatch = findBestMatch(fieldNameToMatch, payloadNode);
                    if (bestMatch != null) {
                        bestMatch = castValue(bestMatch, expectedType);
                    }
                    resultNode.set(fieldName, bestMatch);
                }
            });
            return resultNode;
        }

        return valueNode; // Leave as is for non-processed types
    }

    private JsonNode processValueArrayNode(String parentNodeforArray, JsonNode valueNode, JsonNode payloadNode, boolean IsInArray) {
     
        if (isTypedArray(valueNode)) {
            String expectedType = valueNode.get(0).get("type").asText();
            String fieldNameToMatch = parentNodeforArray;
            ArrayNode resultArray = mapper.createArrayNode();
            if (payloadNode.isArray()) {
                for (JsonNode item : payloadNode) {
                    JsonNode bestMatch = findBestMatch(fieldNameToMatch, item);
                    if (bestMatch != null) {
                        resultArray.add(castValue(bestMatch, expectedType));
                    }
                }
            } else {
                JsonNode bestMatch = findBestMatch(fieldNameToMatch, payloadNode);
                if (bestMatch != null) {
                    resultArray.add(castValue(bestMatch, expectedType));
                }
            }
            return resultArray;
        }

        return valueNode; // Leave as is for non-processed types
    }

    private JsonNode castValue(JsonNode value, String type) {
        try {
            JsonNode match;
            switch (type) {
                case "number":
                    match = new LongNode(Long.parseLong(value.asText().trim()));
                    break;
                case "boolean":
                    match = BooleanNode.valueOf(Boolean.parseBoolean(value.asText().trim()));
                    break;
                case "string":
                    match = new TextNode(value.asText().trim());
                    break;
                default:
                    match = value;
                    break;
            }
            return match;
        } catch (Exception e) {
            return NullNode.getInstance();
        }
    }

    private JsonNode findBestMatch(String templateFieldName, JsonNode payloadNode) {
        LevenshteinDistance levenshtein = new LevenshteinDistance();
        JsonNode flattened = flattenPayload(payloadNode);
        JsonNode bestValue = null;
        int bestDistance = Integer.MAX_VALUE;
        String t = templateFieldName!=null? templateFieldName.toLowerCase():"";;
        Iterator<Map.Entry<String, JsonNode>> fields = flattened.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();           
            String k=key.toLowerCase();
            int distance = levenshtein.apply(t, k);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestValue = entry.getValue();
            }
        }

        return bestValue;
    }

    private JsonNode flattenPayload(JsonNode payloadNode) {
        if (payloadNode.isArray() && payloadNode.size() > 0) {
            ObjectNode merged = mapper.createObjectNode();
            for (JsonNode item : payloadNode) {
                if (item.isObject()) {
                    item.fields().forEachRemaining(field
                            -> merged.set(field.getKey(), field.getValue())
                    );
                }
            }
            return merged;
        }
        return payloadNode;
    }

    private boolean isTypedNode(JsonNode node) {
        if (node.isObject()) {
            Iterator<JsonNode> elements = node.elements();
            while (elements.hasNext()) {
                JsonNode inner = elements.next();
                if (inner.isObject() && inner.has("type")) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean isTypedArray(JsonNode node) {
        if (node.isArray() && node.size() == 1) {
            JsonNode inner = node.get(0);
            return inner.isObject() && inner.has("type") && inner.size() == 1;
        }
        return false;
    }

    

  
}

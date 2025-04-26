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

    public JsonNode processJson(String parentNode, JsonNode inputNode, JsonNode payloadNode, boolean IsInArray) {
        if (isTypedNode(inputNode)) {
            return processValueNode(parentNode, inputNode, payloadNode, IsInArray);
        } else if (inputNode.isObject()) {
            ObjectNode resultNode = mapper.createObjectNode();
            inputNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode childNode = inputNode.get(fieldName);
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
            String expectedType = valueNode.elements().next().get("type").asText();
            String fieldNameToMatch = IsInArray ? parentNodeforArray : valueNode.fieldNames().next();
            JsonNode bestMatch = findBestMatch(fieldNameToMatch, payloadNode);
            if (bestMatch != null) {
                bestMatch = castValue(bestMatch, expectedType);
            }
            ObjectNode objectNode = (ObjectNode) valueNode;
            objectNode.set(fieldNameToMatch, bestMatch);
            return objectNode;
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

        Iterator<Map.Entry<String, JsonNode>> fields = flattened.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();

            int distance = levenshtein.apply(templateFieldName.toLowerCase(), key.toLowerCase());
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
        if (node.isObject() && node.size() == 1) {
            JsonNode inner = node.elements().next();
            return inner.isObject() && inner.has("type") && inner.size() == 1;
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

package org.symphonykernel.transformer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * This class provides methods to transform and process JSON data based on templates.
 */
public class JsonTransformer {

    public static final String JSON = "JSON:";
    public static final String LLM_OPTIMIZED_DATA = "COMPRESSED-JSON:";
    private final ObjectMapper mapper = new ObjectMapper();
    private static final ObjectMapper sharedMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(JsonTransformer.class);

    /**
     * Compares a JSON template with a payload and replaces values in the template based on the payload.
     *
     * @param templateJson The JSON template as a string.
     * @param payloadNode The payload JSON node.
     * @return A transformed JSON node.
     * @throws Exception If an error occurs during processing.
     */
    public JsonNode compareAndReplaceJson(String templateJson, JsonNode payloadNode) throws Exception {
        JsonNode templateNode = mapper.readTree(templateJson);
        JsonNode result = processJson(null, templateNode, payloadNode, templateNode.isArray());
        return result;
    }

    /**
     * Finds the best matching field value in the payload based on the template field name and type.
     *
     * @param fieldName The name of the field to match.
     * @param templateNode The template JSON node.
     * @param payloadNode The payload JSON node.
     * @return The matched field value or null if no match is found.
     */
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

    /**
     * Processes a JSON node recursively, transforming it based on the template and payload.
     *
     * @param parentNode The name of the parent node (if applicable).
     * @param inputNode The input JSON node.
     * @param payloadNode The payload JSON node.
     * @param IsInArray Indicates if the node is part of an array.
     * @return A transformed JSON node.
     */
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
				// TODO :Array Type
                String expectedType = null;
                String fieldNameToMatch = IsInArray ? parentNodeforArray : fieldName;
                JsonNode bestMatch = null;

                // Accept both object with "type" and array with first element having "type"
                if (fieldTemplate.has("type")) {
                    expectedType = fieldTemplate.get("type").asText();
                    bestMatch = findBestMatch(fieldNameToMatch, payloadNode);
                } else if (fieldTemplate.isArray() && fieldTemplate.size() > 0 && fieldTemplate.get(0).has("type")) {
                    expectedType = fieldTemplate.get(0).get("type").asText();
                    bestMatch = findBestMatch(fieldNameToMatch, payloadNode);
                }

                if (bestMatch != null && expectedType != null) {
                    bestMatch = castValue(bestMatch, expectedType);
                }
                resultNode.set(fieldName, bestMatch);
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
        	 if (value.isArray() && value.size() == 1) {
                 value = value.get(0);
             }
            JsonNode match;
            switch (type) {
                case "number":
                     String val=value.asText().trim();
                    if( !val.contains(".") )
                        match = new LongNode(Long.parseLong(value.asText().trim()));
                     else
                        match = new DoubleNode(Double.valueOf(value.asText().trim()));                
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
        LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();
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
            ObjectNode merged = sharedMapper.createObjectNode();
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

    /**
     * Cleans a JSON node by removing null values and empty strings.
     *
     * @param inputNode The input JSON node to clean.
     * @return A cleaned JSON node.
     */
    public static JsonNode getCleanedJsonNode(JsonNode inputNode) {
        if (inputNode.isObject()) {
            ObjectNode cleanedObject = sharedMapper.createObjectNode();
            inputNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (!value.isNull() && !(value.isTextual() && value.asText().trim().isEmpty())) {
                    cleanedObject.set(entry.getKey(), getCleanedJsonNode(value));
                }
            });
            return cleanedObject;
        } else if (inputNode.isArray()) {
            ArrayNode cleanedArray = sharedMapper.createArrayNode();
            for (JsonNode item : inputNode) {
                JsonNode cleanedItem = getCleanedJsonNode(item);
                if (!cleanedItem.isNull() && !(cleanedItem.isTextual() && cleanedItem.asText().trim().isEmpty())) {
                    cleanedArray.add(cleanedItem);
                }
            }
            return cleanedArray;
        }
        return inputNode;
    }
    
    public String compress(JsonNode root)  {
        if (root.isArray() && !root.isEmpty() && root.get(0).isObject()) {
            return compressArrayRoot(root);
        } else {
            return compressStandardRoot(root);
        }
    }

    // Helper for compact array format
    private String compressArrayRoot(JsonNode root) {
        StringBuilder sb = new StringBuilder();
        Map<String, String> fieldSchemas = new LinkedHashMap<>();
        collectFieldSchemas(root, "", fieldSchemas);
        sb.append("SCHEMA:\n[");
        List<String> schemaFields = new ArrayList<>();
        for (Map.Entry<String, String> entry : fieldSchemas.entrySet()) {
            schemaFields.add(entry.getKey() + ":" + entry.getValue());
        }
        sb.append(String.join("|", schemaFields)).append("]\n\nDATA:\n");
        List<String> rows = new ArrayList<>();
        for (JsonNode item : root) {
            List<String> rowValues = new ArrayList<>();
            for (String fieldPath : fieldSchemas.keySet()) {
                JsonNode fieldValue = getNestedValue(item, fieldPath);
                rowValues.add(fieldValue != null && !fieldValue.isNull() ? fieldValue.asText() : "");
            }
            rows.add(String.join("|", rowValues));
        }
        sb.append(String.join("\n", rows));
        return sb.toString();
    }

    // Helper for standard format
    private String compressStandardRoot(JsonNode root) {
        StringBuilder sb = new StringBuilder();
        LinkedHashMap<String, String> schema = new LinkedHashMap<>();
        List<String> values = new ArrayList<>();
        flatten(root, "", schema, values);
        sb.append("SCHEMA:\n");
        int i = 0;
        for (Map.Entry<String, String> e : schema.entrySet()) {
            sb.append(i++).append("=")
                    .append(e.getKey()).append(":")
                    .append(e.getValue()).append("\n");
        }
        sb.append("\nDATA:\n");
        sb.append(String.join("|", values));
        return sb.toString();
    }

    private static void flatten(
            JsonNode node,
            String path,
            Map<String, String> schema,
            List<String> values
    ) {
        if (node.isValueNode() || node.isNull()) {
            schema.put(path, typeOf(node));
            values.add(node.isNull() ? "" : node.asText());
            return;
        }

        if (node.isObject()) {
            node.fields().forEachRemaining(e
                    -> flatten(e.getValue(), join(path, e.getKey()), schema, values)
            );
            return;
        }

        if (node.isArray()) {
            if (node.isEmpty()) {
                schema.put(path + "[]", "empty");
                values.add("");
                return;
            }

            JsonNode first = node.get(0);
            
            // Array of primitives
            if (first.isValueNode()) {
                schema.put(path + "[]", typeOf(first));
                List<String> encoded = new ArrayList<>();
                for (JsonNode item : node) {
                    encoded.add(item.asText());
                }
                values.add(String.join(";", encoded));
                return;
            }

            // Array of objects - recursively handle nested structure
            if (first.isObject()) {
                // Collect all unique field paths across all objects (including nested)
                Map<String, String> fieldSchemas = new LinkedHashMap<>();
                
                // Use recursion to collect all nested fields
                collectFieldSchemas(node, path + "[].", fieldSchemas);
                
                // Add all field schemas
                fieldSchemas.forEach(schema::put);
                
                // For each field, collect values across all array items
                for (String fieldPath : fieldSchemas.keySet()) {
                    String fieldName = fieldPath.substring((path + "[].").length());
                    List<String> fieldValues = new ArrayList<>();
                    
                    for (JsonNode item : node) {
                        if (item.isObject()) {
                            JsonNode fieldValue = getNestedValue(item, fieldName);
                            fieldValues.add(fieldValue != null && !fieldValue.isNull() ? fieldValue.asText() : "");
                        } else {
                            fieldValues.add("");
                        }
                    }
                    values.add(String.join(";", fieldValues));
                }
                return;
            }

            // Array of arrays - recursively handle
            int idx = 0;
            for (JsonNode item : node) {
                flatten(item, path + "[" + idx + "]", schema, values);
                idx++;
            }
        }
    }

    private static void collectFieldSchemas(JsonNode arrayNode, String basePath, Map<String, String> fieldSchemas) {
        for (JsonNode item : arrayNode) {
            if (item.isObject()) {
                collectObjectFields(item, basePath, fieldSchemas);
            }
        }
    }

    private static void collectObjectFields(JsonNode objNode, String basePath, Map<String, String> fieldSchemas) {
        objNode.fields().forEachRemaining(field -> {
            String fieldPath = basePath + field.getKey();
            JsonNode fieldValue = field.getValue();
            
            if (fieldValue.isValueNode() || fieldValue.isNull()) {
                if (!fieldSchemas.containsKey(fieldPath)) {
                    fieldSchemas.put(fieldPath, typeOf(fieldValue));
                }
            } else if (fieldValue.isObject()) {
                // Recursively handle nested objects
                collectObjectFields(fieldValue, fieldPath + ".", fieldSchemas);
            } else if (fieldValue.isArray()) {
                // Handle nested arrays
                if (!fieldSchemas.containsKey(fieldPath + "[]")) {
                    fieldSchemas.put(fieldPath + "[]", "array");
                }
            }
        });
    }

    private static JsonNode getNestedValue(JsonNode node, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = node;
        
        for (String part : parts) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(part);
        }
        
        return current;
    }
//
//    private static String encodeObject(Map<String, String> obj) {
//        List<String> parts = new ArrayList<>();
//        obj.forEach((k, v) -> parts.add(k + "=" + v));
//        return "{" + String.join(",", parts) + "}";
//    }

    private static String typeOf(JsonNode node) {
        if (node.isTextual()) {
            return "string";
        }
        if (node.isInt() || node.isLong()) {
            return "number";
        }
        if (node.isFloatingPointNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        return "string";
    }

    private static String join(String base, String key) {
        return base.isEmpty() ? key : base + "." + key;
    }

    public JsonNode decompress(String compressed)  {
        String[] parts = compressed.split("\n\nDATA:\n", 2);
        String schemaPart = parts[0].replace("SCHEMA:\n", "");
        String dataPart = parts.length > 1 ? parts[1] : "";

        // Check if it's compact array format
        if (schemaPart.startsWith("[") && schemaPart.endsWith("]")) {
            return decompressArrayFormat(schemaPart, dataPart);
        }

        Map<String, String> schemaMap = parseSchemaMap(schemaPart);
        String[] dataValues = dataPart.split("\\|", -1);
        return buildObjectFromSchema(schemaMap, dataValues);
    }

    // Helper to parse schema lines into a map
    private Map<String, String> parseSchemaMap(String schemaPart) {
        Map<String, String> schemaMap = new LinkedHashMap<>();
        for (String line : schemaPart.split("\n")) {
            if (line.trim().isEmpty()) continue;
            String[] p = line.split("=", 2);
            String[] meta = p[1].split(":", 2);
            schemaMap.put(meta[0], meta[1]);
        }
        return schemaMap;
    }

    // Helper to build the object from schema and values
    private JsonNode buildObjectFromSchema(Map<String, String> schemaMap, String[] dataValues) {
        ObjectNode root = mapper.createObjectNode();
        int valueIndex = 0;
        for (Map.Entry<String, String> entry : schemaMap.entrySet()) {
            String path = entry.getKey();
            String type = entry.getValue();
            String value = valueIndex < dataValues.length ? dataValues[valueIndex++] : "";
            insertRecursive(root, path, type, value);
        }
        return root;
    }

    private JsonNode decompressArrayFormat(String schemaPart, String dataPart) {
        List<String> fieldNames = new ArrayList<>();
        List<String> fieldTypes = new ArrayList<>();
        parseArraySchema(schemaPart, fieldNames, fieldTypes);
        ArrayNode result = mapper.createArrayNode();
        if (!dataPart.trim().isEmpty()) {
            String[] rows = dataPart.split("\n");
            for (String row : rows) {
                if (row.trim().isEmpty()) continue;
                result.add(buildObjectFromRow(row, fieldNames, fieldTypes));
            }
        }
        return result;
    }

    // Helper to parse array schema
    private void parseArraySchema(String schemaPart, List<String> fieldNames, List<String> fieldTypes) {
        String schemaContent = schemaPart.substring(1, schemaPart.length() - 1);
        String[] fieldDefs = schemaContent.split("\\|");
        for (String fieldDef : fieldDefs) {
            String[] parts = fieldDef.split(":", 2);
            fieldNames.add(parts[0]);
            fieldTypes.add(parts.length > 1 ? parts[1] : "string");
        }
    }

    // Helper to build object from a row
    private ObjectNode buildObjectFromRow(String row, List<String> fieldNames, List<String> fieldTypes) {
        String[] values = row.split("\\|", -1);
        ObjectNode obj = mapper.createObjectNode();
        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            String fieldType = fieldTypes.get(i);
            String value = i < values.length ? values[i] : "";
            if (fieldName.contains(".")) {
                insertNestedField(obj, fieldName, fieldType, value);
            } else {
                insertField(obj, fieldName, fieldType, value);
            }
        }
        return obj;
    }

    private void insertRecursive(
            ObjectNode root,
            String path,
            String type,
            String value
    ) {
        // Handle array of objects with nested fields (e.g., items[].name)
        if (path.contains("[].")) {
            String[] pathParts = path.split("\\[\\]\\.", 2);
            String arrayPath = pathParts[0];
            String fieldPath = pathParts[1];
            
            ArrayNode array = ensureArray(root, arrayPath);
            
            if (!value.isEmpty()) {
                String[] items = value.split(";", -1);
                
                // Ensure array has enough objects
                while (array.size() < items.length) {
                    array.add(mapper.createObjectNode());
                }
                
                // Insert each value into corresponding object
                for (int i = 0; i < items.length; i++) {
                    if (array.get(i).isObject()) {
                        ObjectNode obj = (ObjectNode) array.get(i);
                        
                        // Handle nested paths in fieldPath (e.g., "address.city")
                        if (fieldPath.contains(".")) {
                            insertNestedField(obj, fieldPath, type, items[i]);
                        } else {
                            insertField(obj, fieldPath, type, items[i]);
                        }
                    }
                }
            }
            return;
        }
        
        // Handle simple arrays (e.g., tags[])
        if (path.endsWith("[]")) {
            String field = path.substring(0, path.length() - 2);
            
            // Navigate to parent and create array
            if (field.contains(".")) {
                String[] pathParts = field.split("\\.");
                ObjectNode current = getOrCreateObjectNode(root, pathParts, mapper);
                ArrayNode array = current.putArray(pathParts[pathParts.length - 1]);
                if (!value.isEmpty()) {
                    for (String item : value.split(";")) {
                        addArrayItem(array, type, item);
                    }
                }
            } else {
                ArrayNode array = root.putArray(field);
                if (!value.isEmpty()) {
                    for (String item : value.split(";")) {
                        addArrayItem(array, type, item);
                    }
                }
            }
            return;
        }

        // Handle indexed arrays (e.g., items[0].field)
        if (path.matches(".*\\[\\d+\\].*")) {
            int bracketStart = path.indexOf('[');
            int bracketEnd = path.indexOf(']');
            String arrayPath = path.substring(0, bracketStart);
            int index = Integer.parseInt(path.substring(bracketStart + 1, bracketEnd));
            
            ArrayNode array = ensureArray(root, arrayPath);
            
            // Ensure array has enough elements
            while (array.size() <= index) {
                array.add(mapper.createObjectNode());
            }
            
            // Handle remaining path after [index]
            if (bracketEnd + 1 < path.length()) {
                String remainingPath = path.substring(bracketEnd + 2); // Skip "]."
                if (array.get(index).isObject()) {
                    ObjectNode obj = (ObjectNode) array.get(index);
                    insertNestedField(obj, remainingPath, type, value);
                }
            }
            return;
        }

        // Handle simple fields and nested objects (e.g., "user.name")
        insertNestedField(root, path, type, value);
    }

    private void insertNestedField(ObjectNode obj, String path, String type, String value) {
        String[] parts = path.split("\\.");
        ObjectNode current = getOrCreateObjectNode(obj, parts, mapper);
        String field = parts[parts.length - 1];
        insertField(current, field, type, value);
    }

    private ArrayNode ensureArray(ObjectNode root, String path) {
        String[] parts = path.split("\\.");
        ObjectNode current = getOrCreateObjectNode(root, parts, mapper);
        String field = parts[parts.length - 1];
        if (!current.has(field)) {
            return current.putArray(field);
        }
        return (ArrayNode) current.get(field);
    }

    private void insertField(ObjectNode obj, String field, String type, String value) {
        // Don't insert empty values for optional fields
        if (value == null || value.isEmpty()) {
            return;
        }
        
        switch (type) {
            case "number":
                try {
                    if (value.contains(".")) {
                        obj.put(field, Double.parseDouble(value));
                    } else {
                        obj.put(field, Long.parseLong(value));
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid numbers
                }
                break;
            case "boolean":
                obj.put(field, Boolean.parseBoolean(value));
                break;
            default:
                obj.put(field, value);
        }
    }

    private void addArrayItem(ArrayNode array, String type, String item) {
        switch (type) {
            case "number":
                array.add(Double.parseDouble(item));
                break;
            case "boolean":
                array.add(Boolean.parseBoolean(item));
                break;
            default:
                array.add(item);
        }
    }
     public  List<String> chunkJsonArray( String jsonArrayString,  int maxLength) throws Exception {

        List<String> chunks = new ArrayList<>();
        if(jsonArrayString==null || jsonArrayString.isBlank())
            return chunks;        
       

        JsonNode root = mapper.readTree(jsonArrayString);

        if (!root.isArray()) {
            throw new IllegalArgumentException("Input JSON must be an array");
        }

        ArrayNode array = (ArrayNode) root;
        logger.info("Starting chunking JSON array of size: {}", array.size());

        ArrayNode currentChunk = mapper.createArrayNode();

        for (JsonNode item : array) {
            int itemLength = serializedLength(item);

            // If a single item is larger than maxLength, put it in its own chunk
            if (itemLength > maxLength) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(serialize(currentChunk));
                    logger.info("Created chunk of {} items", currentChunk.size());
                    currentChunk.removeAll();
                }
                ArrayNode oversizedChunk = mapper.createArrayNode();
                oversizedChunk.add(item);
                chunks.add(serialize(oversizedChunk));
                logger.info("Created oversized chunk of 1 item");
                continue;
            }

            // Try to add to current chunk
            currentChunk.add(item);
            int currentChunkLength = serializedLength(currentChunk);

            if (currentChunkLength > maxLength) {
                // Item does not fit in current chunk, move it to a new one
                currentChunk.remove(currentChunk.size() - 1);
                if (!currentChunk.isEmpty()) {
                    chunks.add(serialize(currentChunk));
                    logger.info("Created chunk of {} items", currentChunk.size());
                }
                currentChunk.removeAll();
                currentChunk.add(item);
            }
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(serialize(currentChunk));
            logger.info("Created chunk of {} items", currentChunk.size());
        }

        return chunks;
    }

    private int serializedLength(JsonNode node) throws Exception {
        return serialize(node).length();
    }

    private String serialize(JsonNode node) throws Exception {
        return mapper.writeValueAsString(node);
    }


    public List<String> chunkCompressedJsonArray(String compressedJson, int maxLength) throws Exception {
        List<String> chunks = new ArrayList<>();
        if (compressedJson == null || compressedJson.isBlank()) {
            return chunks;
        }

        String delimiter = "\n\nDATA:\n";
        int idx = compressedJson.indexOf(delimiter);

        // If format doesn't match expected compressed pattern, fallback: no special handling
        if (idx < 0) {
            if (compressedJson.length() <= maxLength) {
                chunks.add(compressedJson);
            } else {
                int start = 0;
                while (start < compressedJson.length()) {
                    int end = Math.min(start + maxLength, compressedJson.length());
                    chunks.add(compressedJson.substring(start, end));
                    start = end;
                }
            }
            return chunks;
        }

        // First item: schema part (e.g. "SCHEMA:\n[USAGE:string|CODE:string|...}")
        String schemaPart = compressedJson.substring(0, idx);
        chunks.add(schemaPart);
        int schemaLength = schemaPart.length() + delimiter.length();

        // Data part: rows separated by newline
        String dataPart = compressedJson.substring(idx + delimiter.length());
        if (dataPart.isBlank()) {
            return chunks;
        }

        String[] rows = dataPart.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        int currentLength = 0;

        for (String row : rows) {
            boolean skipRow = row.isEmpty();
            if (!skipRow) {
                int rowLength = row.length();
                if (rowLength > maxLength) {
                    if (currentLength > 0) {
                        chunks.add(currentChunk.toString());
                        currentChunk.setLength(0);
                        currentLength = 0;
                    }
                    chunks.add(row);
                    skipRow = true;
                }
            }
            if (skipRow) {
                continue;
            }

            int projectedLength = currentLength == 0
                    ? row.length()
                    : currentLength + 1 + row.length() + schemaLength; // +1 for '\n'

            if (projectedLength > maxLength) {
                // Flush current chunk
                if (currentLength > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk.setLength(0);
                    currentLength = 0;
                }
            }

            if (currentLength > 0) {
                currentChunk.append('\n');
            }
            currentChunk.append(row);
            currentLength = currentChunk.length();
        }

        if (currentLength > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }

    /**
     * Helper to traverse or create nested ObjectNodes along a path.
     */
    private static ObjectNode getOrCreateObjectNode(ObjectNode parent, String[] pathParts, ObjectMapper mapper) {
        ObjectNode current = parent;
        for (int i = 0; i < pathParts.length - 1; i++) {
            JsonNode child = current.get(pathParts[i]);
            if (!(child instanceof ObjectNode)) {
                ObjectNode newChild = mapper.createObjectNode();
                current.set(pathParts[i], newChild);
                current = newChild;
            } else {
                current = (ObjectNode) child;
            }
        }
        return current;
    }
}

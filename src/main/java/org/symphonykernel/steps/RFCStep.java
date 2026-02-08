package org.symphonykernel.steps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.transformer.PlatformHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoDestinationManager;
import com.sap.conn.jco.JCoException;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoRecord;
import com.sap.conn.jco.JCoRepository;
import com.sap.conn.jco.JCoRuntimeException;
import com.sap.conn.jco.JCoStructure;
import com.sap.conn.jco.JCoTable;

@Service
/**
 * The SqlStep class implements the IStep interface and provides
 * functionality for executing SQL queries and processing their results.
 * It supports dynamic query execution, caching, and JSON transformation
 * of query results for use within the Symphony kernel framework.
 * <p>
 * SqlStep is a service class that implements the {@link IStep} interface.
 * <p>
 * This class provides methods for executing SQL queries, processing their results,
 * and transforming them into JSON format. It supports dynamic query execution,
 * caching, and integration with the Symphony kernel framework.
 * </p>
 * 
 * @version 1.0
 * @since 1.0
 */
@Component
@ConditionalOnProperty(
    value = "rfc-enabled",
    havingValue = "true"
)
public class RFCStep extends  BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(SqlStep.class);

    @Autowired
    IknowledgeBase knowledgeBase;

    @Autowired
    PlatformHelper platformHelper;
    
    // Using Jackson's ObjectMapper for all JSON operations
    private static final ObjectMapper MAPPER = new ObjectMapper();
    

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {
      
        JsonNode rfc = null;
        JsonNode variables = ctx.getVariables();
        Knowledge kb = ctx.getKnowledge();
        if (kb != null) {          
             String rfCDef =  kb.getData();
            if (kb.getParams() != null && !kb.getParams().isEmpty()) {
                if(variables != null) {             
                
                    try {
                        rfc = objectMapper.readTree(rfCDef);
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException("Could not execut RFC " + kb.getName() + " Invalid RFC definitionEx: {'Dest':'SYS','Name':'test'}", ex);
                    }
                	logger.info("Executing RFC " + kb.getName() + "name "+rfc.get("Name")+"Dest "+rfc.get("Dest")+" with variables " + variables);  
                }
                else
                {
                	throw new RuntimeException("Could not execut RFC " + kb.getName() + " Parameters missing " + kb.getParams());
                }
            } else {
                logger.info("RFC without params");
            }
        }
        ArrayNode node=null;       
      
        try {
            node= callRfc(rfc,variables);
        } catch (Exception e) {
           throw new RuntimeException("Could not execut RFC " + kb.getName() + " Error: " + e.getMessage(), e);
        }
        if (node == null) { // Check the array element
        	node= com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        }
        saveStepData(ctx, node);
        ChatResponse a = new ChatResponse();
        a.setData(node);
        return a;
    }
        /**
     * Executes an RFC call based on JSON configuration.
     * 
     * @param rfcConfig JSON configuration containing Dest, Name, Imports, and optional Exports
     * @param variables Variable context for parameter resolution
     * @return ArrayNode containing the RFC execution results
     * @throws IllegalArgumentException if configuration is invalid
     * @throws RuntimeException if RFC execution fails
     */
    private ArrayNode callRfc(JsonNode rfcConfig, JsonNode variables) throws Exception {
        validateRfcConfig(rfcConfig);
        
        // Extract RFC configuration
        String destinationName = rfcConfig.has("Dest") ? rfcConfig.get("Dest").asText() : null;
        String rfcName = rfcConfig.has("Name") ? rfcConfig.get("Name").asText() : null;
        JsonNode paramsNode = rfcConfig.get("Imports");
        String[] exportParams = extractExportParams(rfcConfig);
        
        logger.info("Calling RFC: {} on destination: {}", rfcName, destinationName);
        
        try {
            JCoDestination destination = JCoDestinationManager.getDestination(destinationName);
            JCoFunction function = getFunctionFromRepository(destination, rfcName);
            
            // Set import parameters
            setImportParametersIfPresent(function, paramsNode, variables);
            
            logger.debug("Executing RFC: {}", rfcName);
            function.execute(destination);
            
            // Process and return results
            return processRfcResults(function, exportParams, rfcName);
            
        } catch (JCoException e) {
            logger.error("JCo Error while calling RFC {}: {}", rfcName, e.getMessage(), e);
            throw new RuntimeException("JCo Error calling RFC " + rfcName + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Validates RFC configuration.
     */
    private void validateRfcConfig(JsonNode rfcConfig) {
        if (rfcConfig == null) {
            throw new IllegalArgumentException("RFC configuration is null");
        }
        
        if (!rfcConfig.has("Name") && rfcConfig.get("Dest").asText() == null) {
            throw new IllegalArgumentException("RFC Name is required in configuration");
        }
        
        if (!rfcConfig.has("Dest") && rfcConfig.get("Dest").asText() == null) {
            throw new IllegalArgumentException("RFC Destination is required in configuration");
        }
    }
    
    /**
     * Extracts export parameter filter list from configuration.
     */
    private String[] extractExportParams(JsonNode rfcConfig) {
        if (!rfcConfig.has("Exports")) {
            return null;
        }
        
        try {
            return MAPPER.readValue(rfcConfig.get("Exports").toString(), String[].class);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse Exports configuration, returning all exports: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Retrieves JCo function from repository.
     */
    private JCoFunction getFunctionFromRepository(JCoDestination destination, String rfcName) throws JCoException {
        JCoRepository repository = destination.getRepository();
        JCoFunction function = repository.getFunction(rfcName);
        
        if (function == null) {
            throw new RuntimeException("RFC " + rfcName + " not found on SAP system.");
        }
        
        return function;
    }
    
    /**
     * Sets import parameters if present.
     */
    private void setImportParametersIfPresent(JCoFunction function, JsonNode paramsNode, JsonNode variables) {
        JCoParameterList imports = function.getImportParameterList();
        if (paramsNode != null && imports != null) {
            setImportParameters(imports, paramsNode, variables);
        }
    }
    
    /**
     * Processes RFC execution results (exports and tables).
     */
    private ArrayNode processRfcResults(JCoFunction function, String[] exportParams, String rfcName) 
            throws JsonProcessingException {
        ArrayNode resultArray = MAPPER.createArrayNode();
        
        // Process Export Parameters
        processExportParameters(function, exportParams, resultArray);
        
        // Process Tables
        processTableParameters(function, exportParams, resultArray);
        
        logger.info("RFC {} executed successfully", rfcName);
        return resultArray;
    }
    
    /**
     * Processes and filters export parameters.
     */
    private void processExportParameters(JCoFunction function, String[] exportParams, ArrayNode resultArray) 
            throws JsonProcessingException {
        JCoParameterList exports = function.getExportParameterList();
        if (exports == null) {
            return;
        }
        
        Map<String, Object> exportsMap = jcoParameterListToMap(exports);
        Map<String, Object> filteredExports = filterParameters(exportsMap, exportParams);
        
        if (!filteredExports.isEmpty()) {
            resultArray.addPOJO(filteredExports);
            
            if (logger.isDebugEnabled()) {
                String exportsJson = MAPPER.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(filteredExports);
                logger.debug("RFC Exports:\n{}", exportsJson);
            }
        }
    }
    
    /**
     * Processes and filters table parameters.
     */
    private void processTableParameters(JCoFunction function, String[] exportParams, ArrayNode resultArray) 
            throws JsonProcessingException {
        JCoParameterList tables = function.getTableParameterList();
        if (tables == null) {
            return;
        }
        
        Map<String, Object> tablesMap = jcoParameterListToMap(tables);
        Map<String, Object> filteredTables = filterParameters(tablesMap, exportParams);
        
        if (!filteredTables.isEmpty()) {
            resultArray.addPOJO(filteredTables);
            
            if (logger.isDebugEnabled()) {
                String tablesJson = MAPPER.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(filteredTables);
                logger.debug("RFC Tables:\n{}", tablesJson);
            }
        }
    }
    
    /**
     * Filters parameter map based on export filter list.
     * 
     * @param paramMap Original parameter map
     * @param exportParams List of parameter names to include (null = include all)
     * @return Filtered parameter map
     */
    private Map<String, Object> filterParameters(Map<String, Object> paramMap, String[] exportParams) {
        if (exportParams == null || exportParams.length == 0) {
            return paramMap;
        }
        
        Map<String, Object> filtered = new HashMap<>();
        for (String key : exportParams) {
            if (paramMap.containsKey(key)) {
                filtered.put(key, paramMap.get(key));
            }
        }
        
        return filtered;
    }
    
    /**
     * Sets import parameters dynamically from JSON configuration.
     * Supports simple values, structures, and tables.
     * 
     * @param imports The JCo import parameter list
     * @param paramsNode The JSON node containing parameter definitions
     * @param variables The variables context for value resolution
     */
    private void setImportParameters(JCoParameterList imports, JsonNode paramsNode, JsonNode variables) {
        if (paramsNode == null || !paramsNode.isObject()) {
            return;
        }
        
        paramsNode.fields().forEachRemaining(entry -> {
            String paramName = entry.getKey();
            JsonNode paramValue = entry.getValue();
            
            try {
                if (paramValue.isObject() && paramValue.has("_type")) {
                    String type = paramValue.get("_type").asText();
                    
                    if ("structure".equalsIgnoreCase(type)) {
                        // Handle structure parameters
                        JCoStructure structure = imports.getStructure(paramName);
                        JsonNode fields = paramValue.get("fields");
                        if (fields != null && fields.isObject()) {
                            setStructureFields(structure, fields, variables);
                        }
                    } else if ("table".equalsIgnoreCase(type)) {
                        // Handle table parameters
                        JCoTable table = imports.getTable(paramName);
                        JsonNode rows = paramValue.get("rows");
                        if (rows != null && rows.isArray()) {
                            setTableRows(table, rows, variables);
                        }
                    }
                } else {
                    // Handle simple parameters
                    Object resolvedValue = resolveValue(paramValue, variables);
                    imports.setValue(paramName, resolvedValue);
                }
                
                logger.debug("Set parameter: {} = {}", paramName, paramValue);
                
            } catch (Exception e) {
                logger.error("Error setting parameter {}: {}", paramName, e.getMessage(), e);
                throw new RuntimeException("Failed to set parameter " + paramName + ": " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Sets fields in a JCoStructure from JSON.
     */
    private void setStructureFields(JCoStructure structure, JsonNode fields, JsonNode variables) {
        fields.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode fieldValue = entry.getValue();
            
            try {
                Object resolvedValue = resolveValue(fieldValue, variables);
                structure.setValue(fieldName, resolvedValue);
                logger.debug("Set structure field: {} = {}", fieldName, resolvedValue);
            } catch (Exception e) {
                logger.error("Error setting structure field {}: {}", fieldName, e.getMessage(), e);
            }
        });
    }
    
    /**
     * Sets rows in a JCoTable from JSON array.
     */
    private void setTableRows(JCoTable table, JsonNode rows, JsonNode variables) {
        for (JsonNode row : rows) {
            if (row.isObject()) {
                table.appendRow();
                setStructureFields((JCoStructure) table, row, variables);
            }
        }
    }
    
    /**
     * Resolves a value from JSON, supporting variable substitution.
     * Format: "${variableName}" or direct value
     */
    private Object resolveValue(JsonNode valueNode, JsonNode variables) {
        if (valueNode.isTextual()) {
            String value = valueNode.asText();
            
            // Check for variable substitution pattern ${variableName}
            if (value.startsWith("${") && value.endsWith("}")) {
                String varName = value.substring(2, value.length() - 1);
                if (variables != null && variables.has(varName)) {
                    return extractRawValue(variables.get(varName));
                }
                logger.warn("Variable {} not found in context, using literal value", varName);
            }
            return value;
        }
        
        return extractRawValue(valueNode);
    }

     
    private static Map<String, Object> jcoParameterListToMap(JCoParameterList parameterList) {
    Map<String, Object> map = new HashMap<>();
    
    if (parameterList == null) {
        return map;
    }
    
    for (int i = 0; i < parameterList.getFieldCount(); i++) {
        String fieldName = parameterList.getMetaData().getName(i);
        
        try {
            if (parameterList.getMetaData().isTable(fieldName)) {
                // Handle table type
                JCoTable table = parameterList.getTable(fieldName);
                map.put(fieldName, jcoTableToListOfMaps(table));
            } else if (parameterList.getMetaData().isStructure(fieldName)) {
                // Handle structure type
                JCoStructure structure = parameterList.getStructure(fieldName);
                map.put(fieldName, jcoStructureToMap(structure));
            } else {
                // Handle simple field types
                map.put(fieldName, parameterList.getValue(fieldName));
            }
        } catch (JCoRuntimeException e) {
            map.put(fieldName, "[Error retrieving value: " + e.getMessage() + "]");
        }
    }
    
    return map;
}
    /**
     * Extracts the raw Java object from a Jackson JsonNode.
     * This ensures JCo receives the appropriate type (String, Integer, Double, Boolean).
     */
    private static Object extractRawValue(JsonNode jsonNode) {
        if (jsonNode.isTextual()) {
            return jsonNode.asText();
        } else if (jsonNode.isIntegralNumber()) {
            // Use asInt or asLong based on expected size, using asInt for common cases
            return jsonNode.asInt(); 
        } else if (jsonNode.isFloatingPointNumber()) {
            return jsonNode.asDouble();
        } else if (jsonNode.isBoolean()) {
            return jsonNode.asBoolean();
        } else if (jsonNode.isNull()) {
            return null;
        } 
        // Fallback for complex types (structures/tables passed in JSON), 
        // which typically require more complex JCo mapping not covered here.
        return jsonNode.toString(); 
    }

    /**
     * Converts a JCoTable into a List of Maps (suitable for JSON serialization).
     */
    private static List<Map<String, Object>> jcoTableToListOfMaps(JCoTable table) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (table.getNumRows() > 0) {
            table.firstRow();
            do {
                list.add(jcoRecordToMap(table));
            } while (table.nextRow());
        }
        return list;
    }

    /**
     * Converts a JCoStructure into a Map (suitable for JSON serialization).
     */
    private static Map<String, Object> jcoStructureToMap(JCoStructure structure) {
        // Reset the iterator to the beginning of the structure
        return jcoRecordToMap(structure);
    }

    /**
     * Converts a JCoRecord (Table or Structure) into a Map of field names to values.
     */
    private static Map<String, Object> jcoRecordToMap(JCoRecord record) {
        Map<String, Object> rowMap = new HashMap<>();
        for (int i = 0; i < record.getFieldCount(); i++) {
            String name = record.getMetaData().getName(i);
            try {
                // This retrieves the raw Java value which Jackson can serialize
                rowMap.put(name, record.getValue(name));
            } catch (JCoRuntimeException e) {
                rowMap.put(name, "[Error retrieving value]");
            }
        }
        return rowMap;
    }
}
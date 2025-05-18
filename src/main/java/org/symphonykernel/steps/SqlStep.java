package org.symphonykernel.steps;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.transformer.JsonTransformer;
import org.symphonykernel.transformer.PlatformHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
/**
 * SqlStep is a step implementation for executing SQL queries.
 * It provides methods to process SQL queries and retrieve results.
 */
public class SqlStep implements IStep {

    private static final Logger logger = LoggerFactory.getLogger(SqlStep.class);

    @Autowired
    Connection connection;

    @Autowired
    IknowledgeBase knowledgeBase;

    @Autowired
    PlatformHelper platformHelper;

    /**
     * Executes an SQL query and returns the result as an ArrayNode.
     *
     * @param query the SQL query to execute
     * @return the result of the query as an ArrayNode
     */
    @Cacheable(value = "cSCPCache", key = "T(org.apache.commons.codec.digest.DigestUtils).sha256Hex(#query)")
    public ArrayNode executeSqlQuery(String query) {
        ArrayNode data = null;
        if (query != null && !query.isEmpty()) {
            try {
            	logger.debug("Executing Query : {}",query);
                Statement statement = connection.createStatement();
                PreparedStatement stm = connection.prepareStatement(query);
                ResultSet resultSet = stm.executeQuery();
                data = getJSON(resultSet);
                logger.debug("Result : {}" , data!=null?data.toString():"null");
                resultSet.close();
                statement.close();
            } catch (Exception e) {
                e.printStackTrace();
            } 
        }
        if (data == null) {
            data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        }
        return data;
    }

    /**
     * Executes a single-value SQL query with the provided parameters.
     *
     * @param query the SQL query to execute
     * @param params the parameters for the query
     * @param <T> the type of the result
     * @return the result of the query
     * @throws Exception if an error occurs during query execution
     */
    public <T> T executeSingleValueQuery(String query, Object... params) throws Exception {
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            preparedStatement = connection.prepareStatement(query);

            // Set parameters if any
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    preparedStatement.setObject(i + 1, params[i]);
                }
            }

            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                // Return the first column's value as type T
                return (T) resultSet.getObject(1);
            } else {
                // No result found
                return null;
            }

        } finally {
            // Close resources in reverse order of creation
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    e.printStackTrace(); // Or log the exception
                }
            }
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    e.printStackTrace(); // Or log the exception
                }
            }
        }
    }

    /**
     * Converts a ResultSet into an ArrayNode.
     *
     * @param rs the ResultSet to convert
     * @return the converted ArrayNode
     * @throws SQLException if an error occurs during conversion
     */
    public ArrayNode getJSON(ResultSet rs) throws SQLException {
        // Create an ObjectMapper for JSON conversion
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode jsonArray = mapper.createArrayNode();

        // Get metadata about the result set
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        int c = 50;
        // Iterate through the ResultSet and create JSON objects
        while (rs.next() || --c <= 0) {
            ObjectNode jsonObject = mapper.createObjectNode();

            // Use column names or indices for key-value pairs
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                Object value = rs.getObject(i);
                if (value != null) {
                    jsonObject.put(columnName, value.toString());
                } else {
                    jsonObject.put(columnName, "");
                }
            }

            jsonArray.add(jsonObject);
        }

        // Print the generated JSON data
        return jsonArray;
    }

    /**
     * Executes a query by name with dynamic mapping.
     *
     * @param name the name of the query
     * @param params the parameters for the query
     * @return the result of the query as a JsonNode
     */
    public JsonNode executeQueryByNameWithDynamicMapping(String name, Object... params) {

        final ArrayNode[] array = new ArrayNode[1];
        Knowledge kb = knowledgeBase.GetByName(name);
        if (kb != null) {
            JsonNode variables = platformHelper.replaceJsonValue(kb.getParams(), params);
            ExecutionContext ctx = new ExecutionContext();
            ctx.setVariables(variables);
            ctx.setKnowledge(kb);
            array[0] = getResponse(ctx).getData();
        }
        return array[0];
    }

    @Override
    public JsonNode executeQueryByName(ExecutionContext context) {
        final ArrayNode[] array = new ArrayNode[1];
        Knowledge kb = knowledgeBase.GetByName(context.getName());
        if (kb != null) {
            JsonNode var = context.getVariables();
            if (context.getConvert()) {
                try {
                    JsonTransformer transformer = new JsonTransformer();
                    var = transformer.compareAndReplaceJson(kb.getParams(), context.getVariables());
                    context.setVariables(var);
                    context.setKnowledge(kb);
                    //var=platformHelper.compareAndReplaceJson(kb.getParams(), variables);           		  
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            array[0] = getResponse(context).getData();
        }
        return array[0];
    }

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {     

        String sqlQuery = null;
        JsonNode variables = ctx.getVariables();
        Knowledge kb = ctx.getKnowledge();
        if (kb != null) {          
            sqlQuery = kb.getData();
            if (kb.getParams() != null && !kb.getParams().isEmpty()) {
                if(variables != null)
                {
                try {
                	logger.info("Executing SQL " + kb.getName() + " with " + variables);
                    sqlQuery = platformHelper.replacePlaceholders(kb.getData(), kb.getParams(), variables);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
                else
                {
                	throw new RuntimeException("Could not execut SQL " + kb.getName() + " Parameters missing " + kb.getParams());
                }
            } else {
                logger.info("SQL without params");
            }
        }
        ArrayNode node;
        // return sqlQuery;
        // Execute the SQL query against your database
        if (sqlQuery == null) { // Check the array element
        	node= com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        }
        node= executeSqlQuery(sqlQuery);
        logger.info("Data " + node);
        ChatResponse a = new ChatResponse();
        a.setData(node);
        return a;
    }

}

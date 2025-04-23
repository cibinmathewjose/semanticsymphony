package org.symphonykernel.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.starter.DBConnectionProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class SqlStep implements IStep {

    @Autowired
    DBConnectionProperties db;

    @Autowired
    IknowledgeBase knowledgeBase;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    PlatformHelper platformHelper;

    private final ObjectMapper mapper = new ObjectMapper();

    @Cacheable(value = "cSCPCache", key = "T(org.apache.commons.codec.digest.DigestUtils).sha256Hex(#query)")
    public ArrayNode executeSqlQuery(String query) {
        ArrayNode data = null;
        Connection connection = null;
        if (query != null && !query.isEmpty()) {
            try {
                connection = db.getConnection();
                Statement statement = connection.createStatement();

                PreparedStatement stm = connection.prepareStatement(query);
                ResultSet resultSet = stm.executeQuery();
                data = getJSON(resultSet);
                System.out.println("Data " + data);
                resultSet.close();
                statement.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null)
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
        if (data == null) {
            data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        }
        return data;
    }

    public <T> T executeSingleValueQuery(String query, Object... params) throws Exception {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            connection = db.getConnection();
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
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace(); // Or log the exception
                }
            }
        }
    }

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

    private JsonNode executeQueryByNameWithDynamicMapping(String name, Object... params) {

        final ArrayNode[] array = new ArrayNode[1];
        Knowledge kb = knowledgeBase.GetByName(name);
        if (kb != null) {
            JsonNode variables = platformHelper.replaceJsonValue(kb.getParams(), params);
            ExecutionContext ctx = new ExecutionContext();
            ctx.setVariables(variables);
            ctx.setKnowledge(kb);
            array[0] = getResponse(ctx);
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
            array[0] = getResponse(context);
        }
        return array[0];
    }

    @Override
    public ArrayNode getResponse(ExecutionContext ctx) {
        // Use OpenAI API to generate SQL query based on user input
        String sqlQuery = null;
        JsonNode variables = ctx.getVariables();
        Knowledge kb = ctx.getKnowledge();
        if (kb != null) {
            if (kb.getParams() != null && !kb.getParams().isEmpty()) {
                //
                try {
                    System.out.println("Executing SQL " + kb.getName() + " with " + variables);
                    sqlQuery = platformHelper.replacePlaceholders(kb.getData(), kb.getParams(), variables);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        // return sqlQuery;
        // Execute the SQL query against your database
        if (sqlQuery == null) { // Check the array element
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        }
        return executeSqlQuery(sqlQuery);

    }

}

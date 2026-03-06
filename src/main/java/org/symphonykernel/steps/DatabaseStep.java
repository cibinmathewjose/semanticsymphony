package org.symphonykernel.steps;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.core.IAIClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * DatabaseStep connects to a named database, introspects its schema,
 * uses an LLM to generate a read-only SQL query from the user's question,
 * and executes it.
 *
 * <p>The Knowledge {@code data} field should contain a JSON config:
 * <pre>{@code
 * {
 *   "dbname": "mydb",
 *   "schemas": ["dbo", "sales"],
 *   "tables": ["customers", "orders"],
 *   "views": ["v_order_summary"],
 *   "maxRows": 100
 * }
 * }</pre>
 *
 * <p>If {@code dbname} is omitted or null, the default JPA {@link javax.sql.DataSource}
 * bean is used. Otherwise, connection properties are resolved from Spring Environment
 * using the prefix {@code symphony.db.<dbname>.*}:
 * <ul>
 *   <li>{@code symphony.db.mydb.url}</li>
 *   <li>{@code symphony.db.mydb.username}</li>
 *   <li>{@code symphony.db.mydb.password}</li>
 *   <li>{@code symphony.db.mydb.driver-class-name}</li>
 * </ul>
 */
@Service
public class DatabaseStep extends BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseStep.class);

    private static final int DEFAULT_MAX_ROWS = 100;

    private static final Pattern FORBIDDEN_SQL_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|MERGE|EXEC|EXECUTE|CALL|GRANT|REVOKE|INTO)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final ConcurrentHashMap<String, String> schemaCache = new ConcurrentHashMap<>();

    @Autowired
    private Environment environment;

    @Autowired
    private IAIClient aiClient;

    @Autowired(required = false)
    private DataSource dataSource;

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {
        Knowledge kb = ctx.getKnowledge();
        ArrayNode jsonArray = objectMapper.createArrayNode();

        try {
            JsonNode config = objectMapper.readTree(kb.getData());
            String dbName = config.has("dbname") ? config.get("dbname").asText(null) : null;
            int maxRows = config.has("maxRows") ? config.get("maxRows").asInt() : DEFAULT_MAX_ROWS;

            List<String> schemas = parseStringArray(config.get("schemas"));
            List<String> tables = parseStringArray(config.get("tables"));
            List<String> views = parseStringArray(config.get("views"));

            String userQuery = extractUserQuery(ctx);
            if (userQuery == null || userQuery.isBlank()) {
                logger.warn("No user query provided for DatabaseStep");
                ChatResponse resp = new ChatResponse();
                resp.setData(jsonArray);
                return resp;
            }

            try (Connection connection = createConnection(dbName)) {
                connection.setReadOnly(true);

                String cacheKey = buildSchemaCacheKey(dbName, schemas, tables, views);
                String schemaDescription = schemaCache.computeIfAbsent(cacheKey, k -> {
                    try {
                        return introspectSchema(connection, schemas, tables, views);
                    } catch (SQLException e) {
                        throw new RuntimeException("Schema introspection failed", e);
                    }
                });
                if (schemaDescription.isBlank()) {
                    logger.warn("No schema metadata found for dbname={}", dbName);
                    ChatResponse resp = new ChatResponse();
                    resp.setData(jsonArray);
                    return resp;
                }

                String contextVariables = buildContextVariables(ctx);
                String generatedSql = generateQuery(userQuery, schemaDescription, contextVariables, ctx.getModelName());

                validateReadOnly(generatedSql);
                logger.info("DatabaseStep executing generated query: {}", generatedSql);

                try (PreparedStatement stmt = connection.prepareStatement(generatedSql)) {
                    stmt.setMaxRows(maxRows);
                    try (ResultSet rs = stmt.executeQuery()) {
                        ArrayNode results = resultSetToJson(rs);
                        jsonArray.addAll(results);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error in DatabaseStep", e);
            ObjectNode err = objectMapper.createObjectNode();
            err.put("error", e.getMessage());
            jsonArray.add(err);
        }

        ChatResponse response = new ChatResponse();
        response.setData(jsonArray);
        saveStepData(ctx, jsonArray);
        return response;
    }

    private String buildSchemaCacheKey(String dbName, List<String> schemas, List<String> tables, List<String> views) {
        return String.valueOf(dbName) + "|" + schemas + "|" + tables + "|" + views;
    }

    /**
     * Creates a JDBC connection. If {@code dbName} is null or blank, the default
     * JPA {@link DataSource} is used. Otherwise, properties are resolved from
     * the Spring Environment with prefix {@code symphony.db.<dbname>.*}.
     */
    private Connection createConnection(String dbName) throws Exception {
        if (dbName == null || dbName.isBlank()) {
            if (dataSource == null) {
                throw new IllegalStateException(
                        "No dbname specified and no default DataSource is available");
            }
            logger.info("Using default JPA DataSource");
            return dataSource.getConnection();
        }

        String prefix = "symphony.db." + dbName + ".";
        String url = environment.getProperty(prefix + "url");
        String username = environment.getProperty(prefix + "username");
        String password = environment.getProperty(prefix + "password");
        String driverClass = environment.getProperty(prefix + "driver-class-name");

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("No database URL configured for symphony.db." + dbName + ".url");
        }

        if (driverClass != null && !driverClass.isBlank()) {
            Class.forName(driverClass);
        }

        logger.info("Connecting to database '{}' at {}", dbName, url);
        return DriverManager.getConnection(url, username, password);
    }

    /**
     * Introspects the database schema using JDBC {@link DatabaseMetaData}.
     * Builds a human-readable description of tables, columns, keys, indexes, and relationships.
     */
    private String introspectSchema(Connection connection, List<String> schemas,
                                     List<String> tables, List<String> views) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        StringBuilder sb = new StringBuilder();

        List<String> schemaList = schemas.isEmpty() ? discoverSchemas(metaData) : schemas;

        for (String schema : schemaList) {
            appendTablesMetadata(metaData, schema, tables, "TABLE", sb);
            appendTablesMetadata(metaData, schema, views, "VIEW", sb);
        }

        return sb.toString();
    }

    private List<String> discoverSchemas(DatabaseMetaData metaData) throws SQLException {
        List<String> discovered = new ArrayList<>();
        try (ResultSet rs = metaData.getSchemas()) {
            while (rs.next()) {
                discovered.add(rs.getString("TABLE_SCHEM"));
            }
        }
        if (discovered.isEmpty()) {
            discovered.add(null);
        }
        return discovered;
    }

    private void appendTablesMetadata(DatabaseMetaData metaData, String schema,
                                       List<String> filterNames, String tableType,
                                       StringBuilder sb) throws SQLException {
        try (ResultSet tablesRs = metaData.getTables(null, schema, "%", new String[]{tableType})) {
            while (tablesRs.next()) {
                String tableName = tablesRs.getString("TABLE_NAME");
                String tableSchema = tablesRs.getString("TABLE_SCHEM");

                if (!filterNames.isEmpty() && !containsIgnoreCase(filterNames, tableName)) {
                    continue;
                }

                String fullName = (tableSchema != null ? tableSchema + "." : "") + tableName;
                sb.append("\n").append(tableType).append(": ").append(fullName).append("\n");

                appendColumns(metaData, tableSchema, tableName, sb);
                appendPrimaryKeys(metaData, tableSchema, tableName, sb);
                appendForeignKeys(metaData, tableSchema, tableName, sb);
                appendIndexes(metaData, tableSchema, tableName, sb);
            }
        }
    }

    private void appendColumns(DatabaseMetaData metaData, String schema,
                                String tableName, StringBuilder sb) throws SQLException {
        sb.append("  Columns:\n");
        try (ResultSet cols = metaData.getColumns(null, schema, tableName, "%")) {
            while (cols.next()) {
                String colName = cols.getString("COLUMN_NAME");
                String typeName = cols.getString("TYPE_NAME");
                int size = cols.getInt("COLUMN_SIZE");
                String nullable = "YES".equals(cols.getString("IS_NULLABLE")) ? "NULL" : "NOT NULL";
                sb.append("    - ").append(colName)
                  .append(" ").append(typeName)
                  .append("(").append(size).append(")")
                  .append(" ").append(nullable).append("\n");
            }
        }
    }

    private void appendPrimaryKeys(DatabaseMetaData metaData, String schema,
                                    String tableName, StringBuilder sb) throws SQLException {
        List<String> pkCols = new ArrayList<>();
        try (ResultSet pks = metaData.getPrimaryKeys(null, schema, tableName)) {
            while (pks.next()) {
                pkCols.add(pks.getString("COLUMN_NAME"));
            }
        }
        if (!pkCols.isEmpty()) {
            sb.append("  Primary Key: ").append(String.join(", ", pkCols)).append("\n");
        }
    }

    private void appendForeignKeys(DatabaseMetaData metaData, String schema,
                                    String tableName, StringBuilder sb) throws SQLException {
        try (ResultSet fks = metaData.getImportedKeys(null, schema, tableName)) {
            boolean hasFK = false;
            while (fks.next()) {
                if (!hasFK) {
                    sb.append("  Foreign Keys:\n");
                    hasFK = true;
                }
                String fkCol = fks.getString("FKCOLUMN_NAME");
                String pkTable = fks.getString("PKTABLE_NAME");
                String pkSchema = fks.getString("PKTABLE_SCHEM");
                String pkCol = fks.getString("PKCOLUMN_NAME");
                String refTable = (pkSchema != null ? pkSchema + "." : "") + pkTable;
                sb.append("    - ").append(fkCol)
                  .append(" -> ").append(refTable).append("(").append(pkCol).append(")\n");
            }
        }
    }

    private void appendIndexes(DatabaseMetaData metaData, String schema,
                                String tableName, StringBuilder sb) throws SQLException {
        try (ResultSet idxs = metaData.getIndexInfo(null, schema, tableName, false, true)) {
            String currentIndex = null;
            List<String> indexCols = new ArrayList<>();
            boolean headerWritten = false;

            while (idxs.next()) {
                String indexName = idxs.getString("INDEX_NAME");
                if (indexName == null) {
                    continue;
                }
                String colName = idxs.getString("COLUMN_NAME");
                boolean nonUnique = idxs.getBoolean("NON_UNIQUE");

                if (!indexName.equals(currentIndex)) {
                    if (currentIndex != null) {
                        if (!headerWritten) {
                            sb.append("  Indexes:\n");
                            headerWritten = true;
                        }
                        sb.append("    - ").append(currentIndex)
                          .append(" (").append(String.join(", ", indexCols)).append(")\n");
                    }
                    currentIndex = indexName;
                    indexCols = new ArrayList<>();
                }
                if (colName != null) {
                    indexCols.add(colName + (nonUnique ? "" : " UNIQUE"));
                }
            }
            if (currentIndex != null) {
                if (!headerWritten) {
                    sb.append("  Indexes:\n");
                }
                sb.append("    - ").append(currentIndex)
                  .append(" (").append(String.join(", ", indexCols)).append(")\n");
            }
        }
    }

    /**
     * Generates a SQL SELECT query using the LLM based on the user's question,
     * database schema description, and available context variables.
     */
    private String generateQuery(String userQuery, String schemaDescription,
                                  String contextVariables, String modelName) {
        String systemPrompt = "You are an expert SQL query generator. Generate a single read-only SQL SELECT statement.\n\n"
                + "Rules:\n"
                + "- Output ONLY the SELECT statement, nothing else\n"
                + "- Do not include ```sql markers or code fences\n"
                + "- Do not add a semicolon at the end\n"
                + "- Use friendly column aliases with underscores (e.g., Order_Count instead of COUNT(*))\n"
                + "- For string comparisons, use UPPER() on both sides\n"
                + "- Always include the schema name prefix for tables and views\n"
                + "- Only include columns relevant to the question\n"
                + "- NEVER generate INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, TRUNCATE, or EXEC statements\n"
                + "- Make sure column and table names exactly match the schema provided\n\n"
                + "Database Schema:\n" + schemaDescription;

        String userPrompt = "Question: " + userQuery;
        if (contextVariables != null && !contextVariables.isBlank()) {
            userPrompt += "\n\nAvailable context variables (use these values in WHERE clauses when relevant):\n"
                    + contextVariables;
        }

        String result = aiClient.execute(new LLMRequest(systemPrompt, userPrompt, null, modelName));

        if (result != null) {
            result = result.trim();
            if (result.startsWith("```sql")) {
                result = result.substring(6);
            }
            if (result.startsWith("```")) {
                result = result.substring(3);
            }
            if (result.endsWith("```")) {
                result = result.substring(0, result.length() - 3);
            }
            result = result.trim();
            if (result.endsWith(";")) {
                result = result.substring(0, result.length() - 1).trim();
            }
        }

        return result;
    }

    /**
     * Validates that the generated SQL is a read-only SELECT statement.
     *
     * @throws SecurityException if the SQL contains forbidden DML/DDL operations
     */
    private void validateReadOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SecurityException("Generated SQL is empty");
        }

        String normalized = sql.trim().toUpperCase(Locale.ROOT);

        if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH")) {
            throw new SecurityException("Only SELECT queries are allowed. Generated SQL starts with: "
                    + normalized.substring(0, Math.min(20, normalized.length())));
        }

        if (FORBIDDEN_SQL_PATTERN.matcher(sql).find()) {
            throw new SecurityException("Generated SQL contains forbidden operations. Only read-only SELECT is allowed.");
        }
    }

    /**
     * Converts a JDBC {@link ResultSet} into a Jackson {@link ArrayNode}.
     */
    private ArrayNode resultSetToJson(ResultSet rs) throws SQLException {
        ArrayNode jsonArray = objectMapper.createArrayNode();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (rs.next()) {
            ObjectNode row = objectMapper.createObjectNode();
            for (int i = 1; i <= columnCount; i++) {
                String colName = metaData.getColumnLabel(i);
                Object value = rs.getObject(i);
                if (value != null) {
                    row.put(colName, value.toString());
                } else {
                    row.putNull(colName);
                }
            }
            jsonArray.add(row);
        }

        return jsonArray;
    }

    /**
     * Extracts the user's question from the execution context.
     * Checks the user's query first, then falls back to variables.
     */
    private String extractUserQuery(ExecutionContext ctx) {
        String query = ctx.getUsersQuery();
        if (query != null && !query.isBlank()) {
            return query;
        }
        JsonNode variables = ctx.getVariables();
        if (variables != null) {
            for (String field : new String[]{"question", "query", "userQuery", "prompt"}) {
                if (variables.has(field)) {
                    return variables.get(field).asText();
                }
            }
        }
        return null;
    }

    /**
     * Builds a string representation of available context variables for the LLM prompt.
     */
    private String buildContextVariables(ExecutionContext ctx) {
        JsonNode variables = ctx.getVariables();
        if (variables == null || variables.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<String, JsonNode>> fields = variables.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            sb.append(entry.getKey()).append(" = ").append(entry.getValue().asText()).append("\n");
        }

        Map<String, JsonNode> resolved = ctx.getResolvedValues();
        if (resolved != null && !resolved.isEmpty()) {
            for (Map.Entry<String, JsonNode> entry : resolved.entrySet()) {
                if (!"input".equals(entry.getKey())) {
                    sb.append(entry.getKey()).append(" = ").append(entry.getValue().asText()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private List<String> parseStringArray(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }

    private boolean containsIgnoreCase(List<String> list, String value) {
        for (String item : list) {
            if (item.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}

package org.symphonykernel.steps;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;

import org.symphonykernel.steps.db.DbIntrospector;
import org.symphonykernel.steps.db.JdbcDbIntrospector;
import org.symphonykernel.steps.db.OracleDbIntrospector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;

import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.QueryCorrectionLearning;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IQueryCorrectionRepository;

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
    private static final int MAX_PLAN_STEPS = 5;
    private static final int MAX_RETRIES_PER_STEP = 2;
    private static final int MAX_LEARNINGS_PER_QUERY = 5;

    private static final Pattern FORBIDDEN_SQL_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|MERGE|EXEC|EXECUTE|CALL|GRANT|REVOKE|INTO)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final ConcurrentHashMap<String, String> schemaCache = new ConcurrentHashMap<>();

    @Value("${symphony.agentic.chat-history-count:2}")
    private int chatHistoryCount;

    @Autowired
    private Environment environment;

    @Autowired
    private IAIClient aiClient;

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private IQueryCorrectionRepository correctionRepository;

    private static final List<DbIntrospector> INTROSPECTORS = List.of(
            new OracleDbIntrospector(),
            new JdbcDbIntrospector()   // universal fallback — must be last
    );

    /**
     * Picks the first {@link DbIntrospector} that supports the connected database.
     */
    private DbIntrospector resolveIntrospector(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();
        logger.info("Database product: {}", productName);
        for (DbIntrospector introspector : INTROSPECTORS) {
            if (introspector.supports(productName)) {
                logger.info("Using introspector: {}", introspector.getClass().getSimpleName());
                return introspector;
            }
        }
        // Should never happen because JdbcDbIntrospector always returns true
        throw new IllegalStateException("No DbIntrospector found for " + productName);
    }

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

            // Step 1: Rewrite the question using chat history for better context
            String chatHistoryText = buildChatHistoryText(ctx.getChatHistory());
            String rewrittenQuery = rewriteQuestion(userQuery, chatHistoryText, ctx.getModelName());
            logger.info("Original query: {} | Rewritten query: {}", userQuery, rewrittenQuery);

            try (Connection connection = createConnection(dbName)) {
                connection.setReadOnly(true);
                DbIntrospector introspector = resolveIntrospector(connection);

                if (!tables.isEmpty() || !views.isEmpty()) {
                    // Tables/views explicitly specified — single-shot query (no planning needed)
                    String cacheKey = buildSchemaCacheKey(dbName, schemas, tables, views);
                    String schemaDescription = schemaCache.computeIfAbsent(cacheKey, k -> {
                        try {
                            return introspector.introspectSchema(connection, schemas, tables, views);
                        } catch (SQLException e) {
                            throw new RuntimeException("Schema introspection failed", e);
                        }
                    });
                    if (!schemaDescription.isBlank()) {
                        String contextVariables = buildContextVariables(ctx);
                        String knowledgePrompt = kb.getSystemPrompt();
                        String learnings = loadRelevantLearnings(dbName, tables);
                        String generatedSql = generateQuery(rewrittenQuery, schemaDescription,
                                contextVariables, knowledgePrompt, chatHistoryText, learnings, ctx.getModelName());
                        validateReadOnly(generatedSql);
                        logger.info("DatabaseStep executing query: {}", generatedSql);
                        try (PreparedStatement stmt = connection.prepareStatement(generatedSql)) {
                            stmt.setMaxRows(maxRows);
                            try (ResultSet rs = stmt.executeQuery()) {
                                jsonArray.addAll(resultSetToJson(rs));
                            }
                        } catch (SQLException e) {
                            // Single-shot also gets error correction now
                            logger.warn("Single-shot query failed: {}. Attempting LLM correction...", e.getMessage());
                            String correctedSql = correctFailedQuery(rewrittenQuery, generatedSql,
                                    e.getMessage(), schemaDescription, learnings, ctx.getModelName());
                            validateReadOnly(correctedSql);
                            logger.info("Retrying with corrected query: {}", correctedSql);
                            try (PreparedStatement stmt2 = connection.prepareStatement(correctedSql)) {
                                stmt2.setMaxRows(maxRows);
                                try (ResultSet rs = stmt2.executeQuery()) {
                                    jsonArray.addAll(resultSetToJson(rs));
                                }
                            }
                            // Save the correction learning for future use
                            saveCorrectionLearning(dbName, rewrittenQuery, generatedSql,
                                    e.getMessage(), correctedSql, tables);
                        }
                    }
                } else {
                    // No tables/views specified — agentic multi-step planning
                    List<String> allTableNames = introspector.listTableNames(connection, schemas, "TABLE");
                    List<String> allViewNames = introspector.listTableNames(connection, schemas, "VIEW");
                    logger.info("DatabaseStep discovered {} tables and {} views", allTableNames.size(), allViewNames.size());

                    String knowledgePrompt = kb.getSystemPrompt();
                    ArrayNode results = executeAgenticPlan(connection, introspector, rewrittenQuery, allTableNames,
                            allViewNames, schemas, dbName, maxRows, knowledgePrompt, chatHistoryText, ctx);
                    jsonArray.addAll(results);
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

    // ==================== AGENTIC ITERATIVE QUERY LOOP ====================

    /**
     * Iterative agentic loop that answers the user's question by repeatedly:
     * <ol>
     *   <li>Asking the LLM to identify relevant tables from available table names</li>
     *   <li>Loading the full schema (columns, PKs, FKs, indexes) for those tables</li>
     *   <li>Asking the LLM to generate a SQL query using the loaded schema and relationships</li>
     *   <li>Executing the query and storing results</li>
     *   <li>Asking the LLM whether the question is fully answered or more queries are needed</li>
     * </ol>
     * If more queries are needed, previous results are fed as context into the next iteration.
     */
    private ArrayNode executeAgenticPlan(Connection connection, DbIntrospector introspector, String userQuery,
                                         List<String> allTableNames, List<String> allViewNames,
                                         List<String> schemas, String dbName, int maxRows,
                                         String knowledgePrompt, String chatHistoryText,
                                         ExecutionContext ctx) throws SQLException {
        ArrayNode finalResults = objectMapper.createArrayNode();
        List<StepResult> stepResults = new ArrayList<>();
        String modelName = ctx.getModelName();

        for (int iteration = 0; iteration < MAX_PLAN_STEPS; iteration++) {
            logger.info("Agentic iteration {}/{}", iteration + 1, MAX_PLAN_STEPS);

            // Step 1: Ask LLM to identify relevant tables for the current (sub-)question
            String previousContext = buildStepContext(stepResults, ctx);
            String learnings = loadRelevantLearnings(dbName, allTableNames);
            List<String> relevantTables = identifyRelevantTables(
                    userQuery, allTableNames, allViewNames, previousContext,
                    knowledgePrompt, learnings, modelName);
            logger.info("Iteration {} - AI selected {} relevant tables: {}",
                    iteration + 1, relevantTables.size(), relevantTables);

            if (relevantTables.isEmpty()) {
                logger.warn("No relevant tables identified in iteration {}", iteration + 1);
                break;
            }

            // Step 2: Load full schema with columns, PKs, FKs, and indexes for selected tables
            String schemaDescription = loadSchemaForTables(connection, introspector, schemas, dbName, relevantTables);
            if (schemaDescription.isBlank()) {
                logger.warn("No schema metadata found for tables: {}", relevantTables);
                break;
            }

            // Step 3: Load past correction learnings for the selected tables
            String tableLearnings = loadRelevantLearnings(dbName, relevantTables);

            // Step 4: Generate SQL query using loaded schema, chat history, and learnings
            ArrayNode stepData = null;
            String lastFailedSql = null;
            String lastErrorMessage = null;
            for (int retry = 0; retry <= MAX_RETRIES_PER_STEP; retry++) {
                String sql = null;
                try {
                    if (lastErrorMessage != null && lastFailedSql != null) {
                        // Use dedicated correction prompt for retries
                        sql = correctFailedQuery(userQuery, lastFailedSql, lastErrorMessage,
                                schemaDescription, tableLearnings, modelName);
                    } else {
                        sql = generateIterativeQuery(
                                userQuery, schemaDescription, previousContext, knowledgePrompt,
                                chatHistoryText, tableLearnings, modelName);
                    }
                    validateReadOnly(sql);
                    logger.info("Iteration {} query (attempt {}): {}", iteration + 1, retry + 1, sql);

                    // Step 5: Execute the query
                    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                        stmt.setMaxRows(maxRows);
                        try (ResultSet rs = stmt.executeQuery()) {
                            stepData = resultSetToJson(rs);
                        }
                    }

                    // If this was a retry that succeeded, save the correction learning
                    if (lastFailedSql != null && lastErrorMessage != null) {
                        saveCorrectionLearning(dbName, userQuery, lastFailedSql,
                                lastErrorMessage, sql, relevantTables);
                    }
                    break; // Success
                } catch (SecurityException e) {
                    if (retry < MAX_RETRIES_PER_STEP) {
                        lastFailedSql = sql;
                        lastErrorMessage = "Validation failed: " + e.getMessage();
                        logger.warn("Iteration {} query rejected (attempt {}): {}",
                                iteration + 1, retry + 1, e.getMessage());
                    } else {
                        throw new SQLException("Generated SQL failed validation: " + e.getMessage());
                    }
                } catch (SQLException e) {
                    if (retry < MAX_RETRIES_PER_STEP) {
                        lastFailedSql = sql;
                        lastErrorMessage = e.getMessage();
                        logger.warn("Iteration {} query failed (attempt {}): {}. Discovering additional tables...",
                                iteration + 1, retry + 1, e.getMessage());
                        List<String> additionalTables = discoverAdditionalTables(
                                userQuery, e.getMessage(), relevantTables,
                                allTableNames, allViewNames, modelName);
                        if (!additionalTables.isEmpty()) {
                            relevantTables.addAll(additionalTables);
                            schemaDescription = loadSchemaForTables(connection, introspector, schemas, dbName, relevantTables);
                            tableLearnings = loadRelevantLearnings(dbName, relevantTables);
                            logger.info("Expanded to {} tables for retry: {}", relevantTables.size(), relevantTables);
                        }
                    } else {
                        throw e;
                    }
                }
            }

            if (stepData != null && stepData.size() > 0) {
                String stepKey = "step_" + (iteration + 1);
                stepResults.add(new StepResult("Iteration " + (iteration + 1), stepKey, stepData));
                finalResults.addAll(stepData);
            }

            // Step 6: Ask LLM if the question is fully answered or more queries are needed
            if (!needsMoreQueries(userQuery, stepResults, modelName)) {
                logger.info("AI determined question is fully answered after {} iteration(s)", iteration + 1);
                break;
            }
            logger.info("AI determined more queries are needed, continuing to iteration {}", iteration + 2);
        }

        if (finalResults.isEmpty() && !stepResults.isEmpty()) {
            finalResults.addAll(stepResults.get(stepResults.size() - 1).data);
        }

        return finalResults;
    }

    /**
     * Asks the LLM to identify which tables and views are relevant for answering
     * the user's question, given the available table names and any previous results.
     */
    private List<String> identifyRelevantTables(String userQuery, List<String> allTableNames,
                                                 List<String> allViewNames, String previousContext,
                                                 String knowledgePrompt, String learnings,
                                                 String modelName) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a database analyst. Given the user's question and the list of available tables/views, ");
        prompt.append("identify ONLY the tables and views needed to write a SQL query that answers the question.\n\n");
        prompt.append("Available tables:\n");
        for (String t : allTableNames) {
            prompt.append("  - ").append(t).append("\n");
        }
        if (!allViewNames.isEmpty()) {
            prompt.append("Available views:\n");
            for (String v : allViewNames) {
                prompt.append("  - ").append(v).append("\n");
            }
        }
        prompt.append("\nUser question: ").append(userQuery);
        prompt.append("\n\nAdditional Instructions:\n").append(knowledgePrompt).append("\n\n");
        if (learnings != null && !learnings.isBlank()) {
            prompt.append("\n\nPast Query Learnings (tables previously needed for similar queries):\n");
            prompt.append(learnings);
        }
        if (previousContext != null && !previousContext.isBlank()) {
            prompt.append("\n\nPrevious query results already obtained:\n").append(previousContext);
            prompt.append("\nIdentify tables needed for the NEXT query to further answer the question. ");
            prompt.append("Do NOT re-select tables that were already queried if not needed again.");
        }
        prompt.append("\n\nReturn ONLY a comma-separated list of the relevant table/view names. ");
        prompt.append("Include tables needed for JOINs even if the user didn't mention them directly. ");
        prompt.append("No explanation, just the names.");

        String response = aiClient.execute(new LLMRequest(prompt.toString(), userQuery, null, modelName));
        List<String> selected = new ArrayList<>();
        if (response != null && !response.isBlank()) {
            for (String name : response.split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    selected.add(trimmed);
                }
            }
        }
        if (selected.isEmpty()) {
            logger.warn("AI returned no relevant tables, falling back to all tables/views");
            selected.addAll(allTableNames);
            selected.addAll(allViewNames);
        }
        return selected;
    }

    /**
     * Generates a SQL query for the current iteration using the loaded schema
     * (which includes full column details, primary keys, foreign keys, and indexes),
     * results from any previous iterations, chat history context, and past learnings.
     */
    private String generateIterativeQuery(String userQuery, String schemaDescription,
                                           String previousContext, String knowledgePrompt,
                                           String chatHistoryText, String learnings,
                                           String modelName) {
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
                + "- Make sure column and table names exactly match the schema provided\n"
                + "- Use the foreign key relationships in the schema to construct correct JOINs\n\n"
                + (knowledgePrompt != null && !knowledgePrompt.isBlank()
                        ? "Additional Instructions:\n" + knowledgePrompt + "\n\n" : "")
                + "Database Schema (with relationships):\n" + schemaDescription;

        if (learnings != null && !learnings.isBlank()) {
            systemPrompt += "\n\nPast Correction Learnings (avoid these mistakes):\n" + learnings;
        }

        StringBuilder userPrompt = new StringBuilder();
        if (chatHistoryText != null && !chatHistoryText.isBlank()) {
            userPrompt.append("Chat History Context:\n").append(chatHistoryText).append("\n\n");
        }
        userPrompt.append("Question: ").append(userQuery);
        if (previousContext != null && !previousContext.isBlank()) {
            userPrompt.append("\n\nResults from previous queries (use these values in WHERE/IN clauses if needed):\n");
            userPrompt.append(previousContext);
            userPrompt.append("\nGenerate the NEXT query to further answer the question using the above results as context.");
        }

        String result = aiClient.execute(new LLMRequest(systemPrompt, userPrompt.toString(), null, modelName));
        return cleanSqlResponse(result);
    }

    /**
     * Asks the LLM whether the user's question has been fully answered by the
     * queries executed so far, or whether additional queries are needed.
     */
    private boolean needsMoreQueries(String userQuery, List<StepResult> stepResults, String modelName) {
        if (stepResults.isEmpty()) {
            return false;
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("The user asked: ").append(userQuery);
        prompt.append("\n\nThe following query results have been obtained so far:\n");
        for (StepResult sr : stepResults) {
            prompt.append("\n").append(sr.description).append(":\n");
            String data = sr.data.toString();
            if (data.length() > 2000) {
                data = data.substring(0, 2000) + "... (truncated)";
            }
            prompt.append(data).append("\n");
        }
        prompt.append("\nIs the user's question fully answered by these results, ");
        prompt.append("or are additional SQL queries needed to complete the answer?\n");
        prompt.append("Reply with ONLY one word: DONE if fully answered, or MORE if additional queries are needed.");

        String response = aiClient.execute(new LLMRequest(prompt.toString(), userQuery, null, modelName));
        if (response != null) {
            String trimmed = response.trim().toUpperCase(Locale.ROOT);
            return trimmed.contains("MORE");
        }
        return false;
    }

    /**
     * When a query fails, asks the AI which additional tables might be needed
     * based on the error message.
     */
    private List<String> discoverAdditionalTables(String userQuery, String errorMessage,
                                                    List<String> currentTables,
                                                    List<String> allTables, List<String> allViews,
                                                    String modelName) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("A SQL query failed with this error:\n").append(errorMessage);
        prompt.append("\nUser question: ").append(userQuery);
        prompt.append("\n\nCurrently loaded tables: ").append(String.join(", ", currentTables));
        prompt.append("\n\nAll available tables:\n");
        for (String t : allTables) {
            prompt.append("  - ").append(t).append("\n");
        }
        for (String v : allViews) {
            prompt.append("  - ").append(v).append("\n");
        }
        prompt.append("\nWhich additional tables/views are needed to fix this error? ");
        prompt.append("Return ONLY a comma-separated list of table names, nothing else. ");
        prompt.append("If no additional tables can help, return NONE.");

        String response = aiClient.execute(new LLMRequest(prompt.toString(), userQuery, null, modelName));
        List<String> additional = new ArrayList<>();
        if (response != null && !response.isBlank() && !response.trim().equalsIgnoreCase("NONE")) {
            for (String name : response.split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty() && !containsIgnoreCase(currentTables, trimmed)) {
                    additional.add(trimmed);
                }
            }
        }
        return additional;
    }

    private String loadSchemaForTables(Connection connection, DbIntrospector introspector,
                                       List<String> schemas, String dbName, List<String> tables) {
        String cacheKey = buildSchemaCacheKey(dbName, schemas, tables, List.of());
        return schemaCache.computeIfAbsent(cacheKey, k -> {
            try {
                return introspector.introspectSchema(connection, schemas, tables, List.of());
            } catch (SQLException e) {
                throw new RuntimeException("Schema introspection failed", e);
            }
        });
    }

    private String buildStepContext(List<StepResult> previousResults, ExecutionContext ctx) {
        StringBuilder sb = new StringBuilder();
        String ctxVars = buildContextVariables(ctx);
        if (!ctxVars.isBlank()) {
            sb.append(ctxVars).append("\n");
        }
        for (StepResult prev : previousResults) {
            sb.append("Query '").append(prev.resultKey).append("' (").append(prev.description).append("):\n");
            String data = prev.data.toString();
            if (data.length() > 2000) {
                data = data.substring(0, 2000) + "... (truncated)";
            }
            sb.append(data).append("\n\n");
        }
        return sb.toString();
    }

    private String cleanSqlResponse(String result) {
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

    // ==================== STEP RESULT ====================

    private static class StepResult {
        final String description;
        final String resultKey;
        final ArrayNode data;

        StepResult(String description, String resultKey, ArrayNode data) {
            this.description = description;
            this.resultKey = resultKey;
            this.data = data;
        }
    }
    // ==================== SCHEMA DISCOVERY ====================

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

        // Validate dbName to prevent property key injection
        if (!dbName.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid database name: only alphanumeric, underscore, and hyphen characters are allowed");
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
     * Generates a SQL SELECT query using the LLM based on the user's question,
     * database schema description, context variables, chat history, and past learnings.
     */
    private String generateQuery(String userQuery, String schemaDescription,
                                  String contextVariables, String knowledgePrompt,
                                  String chatHistoryText, String learnings,
                                  String modelName) {
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
                + (knowledgePrompt != null && !knowledgePrompt.isBlank()
                        ? "Additional Instructions:\n" + knowledgePrompt + "\n\n" : "")
                + "Database Schema:\n" + schemaDescription;

        if (learnings != null && !learnings.isBlank()) {
            systemPrompt += "\n\nPast Correction Learnings (avoid these mistakes):\n" + learnings;
        }

        StringBuilder userPrompt = new StringBuilder();
        if (chatHistoryText != null && !chatHistoryText.isBlank()) {
            userPrompt.append("Chat History Context:\n").append(chatHistoryText).append("\n\n");
        }
        userPrompt.append("Question: ").append(userQuery);
        if (contextVariables != null && !contextVariables.isBlank()) {
            userPrompt.append("\n\nAvailable context variables (use these values in WHERE clauses when relevant):\n")
                    .append(contextVariables);
        }

        String result = aiClient.execute(new LLMRequest(systemPrompt, userPrompt.toString(), null, modelName));
        return cleanSqlResponse(result);
    }

    // ==================== QUESTION REWRITING & CHAT HISTORY ====================

    /**
     * Rewrites the user's question using chat history context to make it self-contained
     * and more suitable for SQL query generation. Resolves pronouns, references, and
     * follow-up context from previous conversation turns.
     */
    private String rewriteQuestion(String originalQuestion, String chatHistoryText, String modelName) {
        if (chatHistoryText == null || chatHistoryText.isBlank()) {
            return originalQuestion; // No history to contextualize with
        }

        String systemPrompt = "You are a query analysis assistant. Your job is to analyze the user's question "
                + "and rewrite it for optimal SQL query generation against a relational database.\n\n"
                + "Instructions:\n"
                + "1. Analyze the current question in the context of the chat history.\n"
                + "2. Resolve any pronouns or references (e.g., 'those', 'that table', 'the same customer') "
                + "using chat history context.\n"
                + "3. Expand abbreviations or vague terms into precise database-friendly language.\n"
                + "4. If the question is a follow-up, incorporate the relevant context from previous messages "
                + "to make it self-contained.\n"
                + "5. Preserve the original intent — do not add assumptions beyond what the chat history supports.\n"
                + "6. Output ONLY the rewritten question, nothing else. No explanation.";

        String userPrompt = "Chat History:\n" + chatHistoryText + "\n\nCurrent Question: " + originalQuestion;

        String rewritten = aiClient.execute(new LLMRequest(systemPrompt, userPrompt, null, modelName));
        if (rewritten != null && !rewritten.isBlank()) {
            return rewritten.trim();
        }
        return originalQuestion;
    }

    /**
     * Builds a text representation of the recent chat history, limited by
     * the {@code symphony.agentic.chat-history-count} configuration.
     */
    private String buildChatHistoryText(List<Message> chatHistory) {
        if (chatHistory == null || chatHistory.isEmpty()) {
            return "";
        }
        int start = Math.max(0, chatHistory.size() - chatHistoryCount);
        List<Message> recentHistory = chatHistory.subList(start, chatHistory.size());
        StringBuilder sb = new StringBuilder();
        for (Message msg : recentHistory) {
            sb.append(msg.getMessageType()).append(": ").append(msg.getText()).append("\n");
        }
        return sb.toString();
    }

    // ==================== ERROR CORRECTION WITH LLM ====================

    /**
     * Uses the LLM to correct a failed SQL query based on the error message,
     * schema description, and past correction learnings.
     */
    private String correctFailedQuery(String userQuery, String failedSql, String errorMessage,
                                       String schemaDescription, String learnings, String modelName) {
        String systemPrompt = "You are an expert SQL debugger. A SQL query failed with an error. "
                + "Analyze the error and generate a corrected query.\n\n"
                + "Database Schema:\n" + schemaDescription + "\n\n"
                + "Rules:\n"
                + "- Output ONLY the corrected SELECT statement, nothing else\n"
                + "- Do not include ```sql markers or code fences\n"
                + "- Do not add a semicolon at the end\n"
                + "- Analyze the error carefully — common issues include:\n"
                + "  - Wrong column names (check schema for exact names)\n"
                + "  - Missing schema prefix on table/view names\n"
                + "  - Incorrect JOIN conditions (use foreign key relationships)\n"
                + "  - Data type mismatches in comparisons\n"
                + "  - Missing UPPER() for case-insensitive string comparisons\n"
                + "  - Ambiguous column references (add table alias)\n"
                + "- Use the foreign key relationships in the schema for correct JOINs\n"
                + "- NEVER generate INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, TRUNCATE, or EXEC statements";

        if (learnings != null && !learnings.isBlank()) {
            systemPrompt += "\n\nPast Correction Learnings (apply these lessons):\n" + learnings;
        }

        String userPrompt = "Original Question: " + userQuery
                + "\n\nFailed SQL:\n" + failedSql
                + "\n\nError Message: " + errorMessage
                + "\n\nGenerate the CORRECTED SQL query that fixes this error.";

        String result = aiClient.execute(new LLMRequest(systemPrompt, userPrompt, null, modelName));
        return cleanSqlResponse(result);
    }

    // ==================== CORRECTION LEARNINGS ====================

    /**
     * Saves a query correction learning to the repository for future reference.
     * Silently skips if no {@link IQueryCorrectionRepository} is configured.
     */
    private void saveCorrectionLearning(String dbName, String question, String failedSql,
                                         String errorMessage, String correctedSql,
                                         List<String> tables) {
        if (correctionRepository == null) {
            logger.debug("No IQueryCorrectionRepository configured, skipping correction learning save");
            return;
        }
        try {
            String tablesTouched = tables != null ? String.join(",", tables) : "";
            QueryCorrectionLearning learning = new QueryCorrectionLearning(
                    dbName, question, failedSql, errorMessage, correctedSql, tablesTouched);
            correctionRepository.save(learning);
            logger.info("Saved query correction learning for tables: {}", tablesTouched);
        } catch (Exception e) {
            logger.warn("Failed to save correction learning: {}", e.getMessage());
        }
    }

    /**
     * Loads past correction learnings relevant to the current query context.
     * Returns a formatted string of learnings, or empty string if none found.
     */
    private String loadRelevantLearnings(String dbName, List<String> tables) {
        if (correctionRepository == null || tables == null || tables.isEmpty()) {
            return "";
        }
        try {
            List<QueryCorrectionLearning> learnings =
                    correctionRepository.findRelevantLearnings(dbName, tables, MAX_LEARNINGS_PER_QUERY);
            if (learnings == null || learnings.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < learnings.size(); i++) {
                QueryCorrectionLearning l = learnings.get(i);
                sb.append("Learning ").append(i + 1).append(":\n");
                sb.append("  Question: ").append(l.getOriginalQuestion()).append("\n");
                sb.append("  Failed SQL: ").append(l.getFailedSql()).append("\n");
                sb.append("  Error: ").append(l.getErrorMessage()).append("\n");
                sb.append("  Corrected SQL: ").append(l.getCorrectedSql()).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.warn("Failed to load correction learnings: {}", e.getMessage());
            return "";
        }
    }

    // ==================== VALIDATION ====================

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

    @Override
    public Flux<String> getResponseStream(ExecutionContext ctx) {
        ChatResponse response = getResponse(ctx);
        ArrayNode data = response.getData();
        if (data != null && data.size() > 0) {
            String markdown = jsonArrayToMarkdownTable(data);
            return Flux.just("Generating output:").concatWith(Flux.just(markdown));
        }
        String message = response.getMessage();
        if (message != null && !message.isBlank()) {
            return Flux.just("Generating output:").concatWith(Flux.just(message));
        }
        return Flux.just("No results found.");
    }

    /**
     * Converts a Jackson {@link ArrayNode} of row objects into a Markdown table.
     */
    private String jsonArrayToMarkdownTable(ArrayNode rows) {
        if (rows == null || rows.isEmpty()) {
            return "No results found.";
        }

        // Collect all column names preserving insertion order
        LinkedHashSet<String> columnSet = new LinkedHashSet<>();
        for (JsonNode row : rows) {
            row.fieldNames().forEachRemaining(columnSet::add);
        }
        List<String> columns = new ArrayList<>(columnSet);

        StringBuilder sb = new StringBuilder();

        // Header row
        sb.append("|");
        for (String col : columns) {
            sb.append(" ").append(col).append(" |");
        }
        sb.append("\n");

        // Separator row
        sb.append("|");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(" --- |");
        }
        sb.append("\n");

        // Data rows
        for (JsonNode row : rows) {
            sb.append("|");
            for (String col : columns) {
                JsonNode value = row.get(col);
                String text = (value == null || value.isNull()) ? "" : value.asText();
                sb.append(" ").append(text).append(" |");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}

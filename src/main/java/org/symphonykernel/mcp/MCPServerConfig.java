package org.symphonykernel.mcp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;
import org.symphonykernel.UserSession;
import org.symphonykernel.ai.Agent;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.steps.HumanInLoopStateStore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Configures a Spring AI MCP Server that exposes all Symphony knowledge steps as MCP tools.
 * <p>
 * External agents (Claude Desktop, Cursor, any MCP client) can discover and call these tools over SSE/stdio transport.
 * <br>
 * Enabled via: {@code symphony.mcp.server.enabled=true}
 * </p>
 */
@Configuration
@ConditionalOnProperty(value = "symphony.mcp.server.enabled", havingValue = "true", matchIfMissing = false)
public class MCPServerConfig {

    private static final Logger logger = LoggerFactory.getLogger(MCPServerConfig.class);
    private static final String LIST_PREDEFINED_WORKFLOWS_TOOL = "list_predefined_workflows";
    private static final String SUBMIT_HUMAN_INPUT_TOOL = "submit_human_input";
    private static final String RESUME_WORKFLOW_TOOL = "resume_workflow";

    @Autowired
    private MCPToolRegistry toolRegistry;

    @Autowired
    private IknowledgeBase knowledgeBase;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.symphonykernel.ai.KnowledgeExecuterFactory factory;

    @Autowired
    private HumanInLoopStateStore humanInLoopStateStore;

    @Autowired
    private Agent agent;

        /**
         * Registers all Symphony knowledge entries as MCP tool specifications.
         * Each knowledge entry becomes a callable tool via the MCP protocol.
         *
         * @return the list of MCP tool specifications
         */
    @Bean
    public List<SyncToolSpecification> symphonyMcpTools() {
        List<SyncToolSpecification> tools = new ArrayList<>();

        tools.add(buildListPredefinedWorkflowsTool());
        tools.add(buildSubmitHumanInputTool());
        tools.add(buildResumeWorkflowTool());

        for (MCPToolDescriptor descriptor : toolRegistry.listTools()) {
            if (!isPredefinedWorkflow(descriptor)) {
                continue;
            }
            try {
                McpSchema.JsonSchema inputSchema = buildJsonSchema(descriptor);
                Tool tool = new Tool(descriptor.getName(), descriptor.getDescription(), null, inputSchema, null, null, null);
                SyncToolSpecification spec = new SyncToolSpecification(tool, (exchange, args) -> {
                    return executeTool(descriptor.getName(), args);
                });
                tools.add(spec);
            } catch (Exception e) {
                logger.warn("Failed to create MCP tool for '{}': {}", descriptor.getName(), e.getMessage());
            }
        }

        logger.info("Registered {} Symphony tools as MCP endpoints", tools.size());
        return tools;
    }

        private SyncToolSpecification buildSubmitHumanInputTool() {
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
            "object",
            Map.of(
                "requestId", Map.of("type", "string", "description", "Paused workflow request id"),
                "input", Map.of("type", "string", "description", "Human response input")),
            List.of("requestId", "input"),
            null,
            null,
            null);

        Tool tool = new Tool(
            SUBMIT_HUMAN_INPUT_TOOL,
            "Submits human input for a paused workflow",
            null,
            inputSchema,
            null,
            null,
            null);

        return new SyncToolSpecification(tool, (exchange, args) -> submitHumanInput(args));
        }

        private SyncToolSpecification buildResumeWorkflowTool() {
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
            "object",
            Map.of(
                "requestId", Map.of("type", "string", "description", "Paused workflow request id"),
                "input", Map.of("type", "string", "description", "Optional human response input")),
            List.of("requestId"),
            null,
            null,
            null);

        Tool tool = new Tool(
            RESUME_WORKFLOW_TOOL,
            "Resumes a paused workflow by request id",
            null,
            inputSchema,
            null,
            null,
            null);

        return new SyncToolSpecification(tool, (exchange, args) -> resumeWorkflow(args));
        }

    private SyncToolSpecification buildListPredefinedWorkflowsTool() {
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema("object", Map.of(), null, null, null, null);
        Tool tool = new Tool(
                LIST_PREDEFINED_WORKFLOWS_TOOL,
                "Lists predefined workflow capabilities published by Symphony (type, name, description, input schema)",
                null,
                inputSchema,
                null,
                null,
                null);
        return new SyncToolSpecification(tool, (exchange, args) -> listPredefinedWorkflowCapabilities());
    }

    private McpSchema.CallToolResult listPredefinedWorkflowCapabilities() {
        try {
            List<Map<String, Object>> workflows = new ArrayList<>();
            for (MCPToolDescriptor descriptor : toolRegistry.listTools()) {
                if (!isPredefinedWorkflow(descriptor)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", descriptor.getName());
                item.put("description", descriptor.getDescription());
                item.put("type", descriptor.getQueryType());
                item.put("inputSchema", descriptor.getInputSchema() != null ? descriptor.getInputSchema() : Map.of());
                workflows.add(item);
            }
            workflows.sort(Comparator.comparing(item -> item.get("name").toString()));

            String payload = objectMapper.writeValueAsString(Map.of(
                    "count", workflows.size(),
                    "workflows", workflows));

            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(payload)),
                    false);
        } catch (Exception e) {
            logger.error("Error listing predefined workflow capabilities: {}", e.getMessage(), e);
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("Error: " + e.getMessage())),
                    true);
        }
    }

    private McpSchema.CallToolResult submitHumanInput(Map<String, Object> args) {
        String requestId = getRequiredStringArg(args, "requestId");
        String input = getRequiredStringArg(args, "input");
        if (requestId == null || input == null) {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("requestId and input are required")),
                    true);
        }

        boolean accepted = humanInLoopStateStore.submitInput(requestId, input);
        boolean awaiting = humanInLoopStateStore.isAwaitingInput(requestId);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "requestId", requestId,
                    "accepted", accepted,
                    "awaitingInput", awaiting,
                    "message", accepted ? "Input accepted" : "No pending human loop found for requestId"));
        } catch (Exception e) {
            payload = "{\"requestId\":\"" + requestId + "\",\"accepted\":" + accepted + "}";
        }

        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(payload)),
                !accepted);
    }

    private McpSchema.CallToolResult resumeWorkflow(Map<String, Object> args) {
        String requestId = getRequiredStringArg(args, "requestId");
        if (requestId == null) {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("requestId is required")),
                    true);
        }
        String input = getOptionalStringArg(args, "input");

        ChatResponse response = agent.resumePausedWorkflow(requestId, input);
        boolean isError = response == null || "ERROR".equalsIgnoreCase(response.getStatusCode());

        try {
            String responsePayload = objectMapper.writeValueAsString(Map.of(
                    "requestId", response != null && response.getRequestId() != null ? response.getRequestId() : requestId,
                    "status", response != null ? response.getStatusCode() : "ERROR",
                    "message", response != null ? response.getMessage() : "Resume failed",
                    "data", response != null && response.getData() != null ? response.getData() : objectMapper.createArrayNode()));
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(responsePayload)),
                    isError);
        } catch (Exception e) {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(response != null ? (response.getMessage() != null ? response.getMessage() : "Resume completed") : "Resume failed")),
                    isError);
        }
    }

    private String getRequiredStringArg(Map<String, Object> args, String key) {
        String value = getOptionalStringArg(args, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String getOptionalStringArg(Map<String, Object> args, String key) {
        if (args == null || key == null) {
            return null;
        }
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private boolean isPredefinedWorkflow(MCPToolDescriptor descriptor) {
        if (descriptor == null || descriptor.getQueryType() == null) {
            return false;
        }
        String queryType = descriptor.getQueryType();
        return QueryType.SYMPHONY.name().equalsIgnoreCase(queryType)
                || QueryType.AGENTIC.name().equalsIgnoreCase(queryType);
    }

    /**
     * Builds a JSON schema for the given MCP tool descriptor.
     *
     * @param descriptor the MCP tool descriptor
     * @return the JSON schema for the tool's input
     */
    private McpSchema.JsonSchema buildJsonSchema(MCPToolDescriptor descriptor) {
        Map<String, Object> schema = descriptor.getInputSchema();
        String type = schema != null && schema.containsKey("type") ? schema.get("type").toString() : "object";
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = schema != null && schema.containsKey("properties") 
            ? (Map<String, Object>) schema.get("properties") 
            : Map.of();
        return new McpSchema.JsonSchema(type, properties, null, null, null, null);
    }

    /**
     * Executes a Symphony step by its knowledge name with the provided arguments.
     *
     * @param knowledgeName the name of the Symphony knowledge step/tool
     * @param args the arguments to pass to the tool
     * @return the result of the tool execution as a {@link McpSchema.CallToolResult}
     */
    private McpSchema.CallToolResult executeTool(String knowledgeName, Map<String, Object> args) {
        try {
            Knowledge kb = knowledgeBase.GetByName(knowledgeName);
            if (kb == null) {
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("Tool not found: " + knowledgeName)),
                    true
                );
            }

            IStep step = factory.getExecuter(kb);
            if (step == null) {
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("No executor found for: " + knowledgeName)),
                    true
                );
            }

            JsonNode variables = objectMapper.valueToTree(args);
            String requestId = java.util.UUID.randomUUID().toString();
            ExecutionContext ctx = new ExecutionContext();
            ctx.setKnowledge(kb);
            ctx.setName(knowledgeName);
            ctx.setVariables(variables);
            ctx.setConvert(true);

            UserSession session = new UserSession();
            session.setRequestId(requestId);
            session.setSessionID(requestId);
            session.setStatus("PROCESSING");
            ctx.setUserSession(session);

            ChatRequest request = new ChatRequest();
            request.setConversationId(requestId);
            request.setSession(requestId);
            request.setQuery(args != null && args.get("query") != null ? args.get("query").toString() : "");
            ctx.setRequest(request);

            ChatResponse response = step.getResponse(ctx);
            if (response.getRequestId() == null || response.getRequestId().isBlank()) {
                response.setRequestId(requestId);
            }
            String resultJson;
            if (response.getData() != null) {
                resultJson = objectMapper.writeValueAsString(response.getData());
            } else if (response.getMessage() != null) {
                resultJson = response.getMessage();
            } else {
                resultJson = "{}";
            }

            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(resultJson)),
                false
            );
        } catch (Exception e) {
            logger.error("Error executing MCP tool '{}': {}", knowledgeName, e.getMessage(), e);
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("Error: " + e.getMessage())),
                true
            );
        }
    }
}

package org.symphonykernel.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Configures a Spring AI MCP Server that exposes all Symphony knowledge
 * steps as MCP tools. External agents (Claude Desktop, Cursor, any MCP
 * client) can discover and call these tools over SSE/stdio transport.
 * <p>
 * Enabled via: symphony.mcp.server.enabled=true
 * </p>
 */
@Configuration
@ConditionalOnProperty(value = "symphony.mcp.server.enabled", havingValue = "true", matchIfMissing = false)
public class MCPServerConfig {

    private static final Logger logger = LoggerFactory.getLogger(MCPServerConfig.class);

    @Autowired
    private MCPToolRegistry toolRegistry;

    @Autowired
    private IknowledgeBase knowledgeBase;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.symphonykernel.ai.KnowledgeGraphBuilder knowledgeGraphBuilder;

    /**
     * Registers all Symphony knowledge entries as MCP tool specifications.
     * Each knowledge entry becomes a callable tool via the MCP protocol.
     *
     * @return the list of MCP tool specifications
     */
    @Bean
    public List<SyncToolSpecification> symphonyMcpTools() {
        List<SyncToolSpecification> tools = new ArrayList<>();

        for (MCPToolDescriptor descriptor : toolRegistry.listTools()) {
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

            IStep step = knowledgeGraphBuilder.getExecuter(kb);
            if (step == null) {
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("No executor found for: " + knowledgeName)),
                    true
                );
            }

            JsonNode variables = objectMapper.valueToTree(args);
            ExecutionContext ctx = new ExecutionContext();
            ctx.setKnowledge(kb);
            ctx.setName(knowledgeName);
            ctx.setVariables(variables);
            ctx.setConvert(true);

            ChatResponse response = step.getResponse(ctx);
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

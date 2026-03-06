package org.symphonykernel.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * MCP Client service that connects to external MCP servers and discovers
 * their tools. These external tools can be used by the AgenticPlanner
 * alongside built-in Symphony steps.
 *
 * <p>Configure external MCP servers via:
 * <pre>
 * symphony.mcp.client.servers[0].name=my-server
 * symphony.mcp.client.servers[0].url=http://localhost:8080
 * </pre>
 * Enabled via: symphony.mcp.client.enabled=true
 */
@Service
@ConditionalOnProperty(value = "symphony.mcp.client.enabled", havingValue = "true", matchIfMissing = false)
public class MCPClientService {

    private static final Logger logger = LoggerFactory.getLogger(MCPClientService.class);

    @Autowired
    private MCPClientProperties clientProperties;

    @Autowired
    private MCPToolRegistry toolRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    /** Map of connected MCP sync clients keyed by server name. */
    private final ConcurrentHashMap<String, McpSyncClient> clients = new ConcurrentHashMap<>();

    /** Initializes connections to all configured MCP servers. */
    @PostConstruct
    public void init() {
        if (clientProperties.getServers() == null || clientProperties.getServers().isEmpty()) {
            logger.info("No external MCP servers configured");
            return;
        }
        for (MCPClientProperties.ServerConfig server : clientProperties.getServers()) {
            connectToServer(server);
        }
    }

    /** Closes all MCP client connections. */
    @PreDestroy
    public void cleanup() {
        for (McpSyncClient client : clients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("Error closing MCP client: {}", e.getMessage());
            }
        }
        clients.clear();
    }

    private void connectToServer(MCPClientProperties.ServerConfig server) {
        try {
            var transport = HttpClientSseClientTransport.builder(server.getUrl()).build();
            McpSyncClient client = McpClient.sync(transport).build();
            client.initialize();

            McpSchema.ListToolsResult toolsResult = client.listTools();
            if (toolsResult != null && toolsResult.tools() != null) {
                for (McpSchema.Tool tool : toolsResult.tools()) {
                    MCPToolDescriptor descriptor = new MCPToolDescriptor(
                        server.getName() + "/" + tool.name(),
                        tool.description(),
                        parseJsonSchema(tool.inputSchema()),
                        "MCP_EXTERNAL"
                    );
                    toolRegistry.registerExternalTool(descriptor);
                }
                logger.info("Discovered {} tools from MCP server '{}'", toolsResult.tools().size(), server.getName());
            }

            clients.put(server.getName(), client);
        } catch (Exception e) {
            logger.error("Failed to connect to MCP server '{}' at {}: {}", server.getName(), server.getUrl(), e.getMessage());
        }
    }

    /**
     * Calls an external MCP tool by its full name (serverName/toolName).
     *
     * @param fullToolName the tool name in format serverName/toolName
     * @param arguments the tool arguments
     * @return the tool execution result
     */
    public String callTool(String fullToolName, Map<String, Object> arguments) {
        String[] parts = fullToolName.split("/", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Tool name must be in format 'serverName/toolName': " + fullToolName);
        }
        String serverName = parts[0];
        String toolName = parts[1];

        McpSyncClient client = clients.get(serverName);
        if (client == null) {
            throw new IllegalStateException("No MCP client connected for server: " + serverName);
        }

        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
        if (result.content() != null && !result.content().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (McpSchema.Content content : result.content()) {
                if (content instanceof McpSchema.TextContent textContent) {
                    sb.append(textContent.text());
                }
            }
            return sb.toString();
        }
        return "{}";
    }

    /**
     * Returns the list of connected server names.
     *
     * @return list of connected server names
     */
    public List<String> getConnectedServers() {
        return new ArrayList<>(clients.keySet());
    }

    private Map<String, Object> parseJsonSchema(McpSchema.JsonSchema schema) {
        if (schema == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new HashMap<>();
        if (schema.type() != null) {
            result.put("type", schema.type());
        }
        if (schema.properties() != null) {
            result.put("properties", schema.properties());
        }
        return result;
    }
}

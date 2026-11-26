package org.symphonykernel.mcp;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;
import org.symphonykernel.core.IknowledgeBase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
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
    private static final String QUERY_TYPE_EXTERNAL = "MCP_EXTERNAL";
    private static final String AUTH_TYPE_API_KEY_HEADER = "apiKeyHeader";
    private static final String AUTH_TYPE_BEARER = "bearerToken";
    private static final String AUTH_TYPE_API_KEY_QUERY = "apiKeyQuery";
    private static final String AUTH_TYPE_NONE = "none";
    private static final String DEFAULT_API_KEY_HEADER = "x-api-key";
    private static final String DEFAULT_BEARER_HEADER = "Authorization";
    private static final String DEFAULT_QUERY_PARAM_NAME = "api-key";

    @Autowired
    private MCPClientProperties clientProperties;

    @Autowired
    private IknowledgeBase knowledgeBase;

    @Autowired
    private MCPToolRegistry toolRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Value("${symphony.mcp.client.lazy-connect:true}")
    private boolean lazyConnect;

    @Value("${symphony.mcp.client.lazy-connect.max-servers-per-query:5}")
    private int maxServersPerQuery;

    @Value("${symphony.mcp.client.transport:}")
    private String transportMode;

    /** Map of connected MCP sync clients keyed by server name. */
    private final ConcurrentHashMap<String, McpSyncClient> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MCPClientProperties.ServerConfig> configuredServers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> serverLocks = new ConcurrentHashMap<>();

    /** Initializes configured MCP server registrations. */
    @PostConstruct
    public void init() {
        reloadRegistrations();
    }

    /**
     * Reloads MCP server registrations from properties + knowledge base and refreshes
     * external client connections/tool discovery.
     *
     * @return reload summary map
     */
    public synchronized Map<String, Object> reloadRegistrations() {
        closeClients();
        clients.clear();
        serverLocks.clear();

        toolRegistry.refreshRegistry();

        List<MCPClientProperties.ServerConfig> allServers = resolveAllServerRegistrations();
        configuredServers.clear();
        for (MCPClientProperties.ServerConfig server : allServers) {
            configuredServers.put(server.getName(), server);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("registeredServerCount", configuredServers.size());
        summary.put("lazyConnectEnabled", lazyConnect);

        if (allServers.isEmpty()) {
            logger.info("No external MCP servers configured");
            summary.put("connectedServerCount", 0);
            return summary;
        }

        if (lazyConnect) {
            logger.info("MCP client lazy-connect is enabled. Registered {} external MCP server(s)", configuredServers.size());
            summary.put("connectedServerCount", clients.size());
            return summary;
        }

        int connected = 0;
        for (MCPClientProperties.ServerConfig server : allServers) {
            if (connectToServer(server)) {
                connected++;
            }
        }

        summary.put("connectedServerCount", connected);
        return summary;
    }

    /** Closes all MCP client connections. */
    @PreDestroy
    public void cleanup() {
        closeClients();
        clients.clear();
        serverLocks.clear();
    }

    private void closeClients() {
        for (McpSyncClient client : clients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("Error closing MCP client: {}", e.getMessage());
            }
        }
    }

    private boolean connectToServer(MCPClientProperties.ServerConfig server) {
        try {
            if (server == null || !server.isEnabled()) {
                return false;
            }
            if (server.getName() == null || server.getName().isBlank() || server.getUrl() == null || server.getUrl().isBlank()) {
                logger.warn("Skipping MCP server registration with missing name/url");
                return false;
            }
            if (clients.containsKey(server.getName())) {
                return true;
            }

            String resolvedAuthKey = resolveAuthKey(server);
            String url = applyQueryAuthIfConfigured(server.getUrl(), server, resolvedAuthKey);

            McpSyncClient client = null;
            Exception sseError = null;

            String mode = resolveTransportMode(server);
            if ("sse".equals(mode)) {
                client = connectUsingSseTransport(server, url, resolvedAuthKey);
            } else if ("streamable".equals(mode)) {
                client = connectUsingStreamableTransport(server, url, resolvedAuthKey);
            } else if ("auto".equals(mode)) {
                try {
                    client = connectUsingSseTransport(server, url, resolvedAuthKey);
                } catch (Exception ex) {
                    sseError = ex;
                    logger.warn("SSE MCP transport failed for server '{}', trying streamable HTTP fallback: {}", server.getName(), ex.getMessage());
                    client = connectUsingStreamableTransport(server, url, resolvedAuthKey);
                }
            } else {
                throw new IllegalArgumentException("MCP transport must be streamable, sse or auto");
            }

            if (client == null && sseError != null) {
                throw sseError;
            }

            clients.put(server.getName(), client);
            return true;
        } catch (Exception e) {
            String actionable = buildActionableConnectErrorMessage(server, e);
            logger.error("Failed to connect to MCP server '{}' at {}: {}", server.getName(), server.getUrl(), actionable);
            return false;
        }
    }

    String resolveTransportMode(MCPClientProperties.ServerConfig server) {
        String serverMode = server != null ? server.getTransport() : null;
        if (serverMode != null && !serverMode.isBlank()) {
            return serverMode.trim().toLowerCase(Locale.ROOT);
        }
        if (transportMode != null && !transportMode.isBlank()) {
            return transportMode.trim().toLowerCase(Locale.ROOT);
        }
        return "streamable";
    }

    private McpSyncClient connectUsingSseTransport(MCPClientProperties.ServerConfig server, String url, String resolvedAuthKey) {
        URI uri = URI.create(url);
        var builder = HttpClientSseClientTransport.builder(serverOrigin(uri))
            .sseEndpoint(endpointPath(uri, "/sse"));
        applyHeaderAuthIfConfigured(builder, server, resolvedAuthKey);
        return initializeClient(server.getName(), withDeadlines(McpClient.sync(builder.build())).build());
    }

    private McpSyncClient connectUsingStreamableTransport(MCPClientProperties.ServerConfig server, String url, String resolvedAuthKey) {
        URI uri = URI.create(url);
        var builder = HttpClientStreamableHttpTransport.builder(serverOrigin(uri))
            .endpoint(endpointPath(uri, "/mcp"));
        applyHeaderAuthIfConfigured(builder, server, resolvedAuthKey);
        return initializeClient(server.getName(), withDeadlines(McpClient.sync(builder.build())).build());
    }

    private McpClient.SyncSpec withDeadlines(McpClient.SyncSpec spec) {
        return spec.requestTimeout(clientProperties.getRequestTimeout())
                .initializationTimeout(clientProperties.getInitializationTimeout());
    }

    private McpSyncClient initializeClient(String serverName, McpSyncClient client) {
        try {
            client.initialize();
            registerDiscoveredTools(serverName, client);
            return client;
        } catch (RuntimeException failure) {
            try {
                client.close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static String serverOrigin(URI uri) {
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("MCP URL must be an HTTP(S) URL without user info or a fragment");
        }
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    private static String endpointPath(URI uri, String defaultPath) {
        String path = uri.getRawPath();
        // The SDK resolves the endpoint as a URI reference; a // prefix would replace the host.
        if (path.startsWith("//")) {
            throw new IllegalArgumentException("MCP endpoint path must not start with //");
        }
        if (path.isEmpty()) {
            path = defaultPath;
        }
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    void registerDiscoveredTools(String serverName, McpSyncClient client) {
        List<MCPToolDescriptor> descriptors = new ArrayList<>();
        Set<String> seenCursors = new HashSet<>();
        String cursor = null;
        int pages = 0;
        do {
            if (++pages > clientProperties.getMaxToolDiscoveryPages()) {
                throw new IllegalStateException("MCP tool discovery exceeded its page limit");
            }
            McpSchema.ListToolsResult page = cursor == null ? client.listTools() : client.listTools(cursor);
            if (page == null) {
                break;
            }
            if (page.tools() != null) {
                for (McpSchema.Tool tool : page.tools()) {
                    MCPToolDescriptor descriptor = new MCPToolDescriptor(serverName + "/" + tool.name(),
                            tool.description(), parseJsonSchema(tool.inputSchema()), QUERY_TYPE_EXTERNAL);
                    if (tool.annotations() != null) {
                        descriptor.setReadOnlyHint(Boolean.TRUE.equals(tool.annotations().destructiveHint())
                                ? Boolean.FALSE : tool.annotations().readOnlyHint());
                    }
                    descriptors.add(descriptor);
                }
            }
            cursor = page.nextCursor();
            if (cursor != null && !cursor.isBlank() && !seenCursors.add(cursor)) {
                throw new IllegalStateException("MCP tool discovery returned a repeated cursor");
            }
        } while (cursor != null && !cursor.isBlank());
        descriptors.forEach(toolRegistry::registerExternalTool);
        logger.info("Discovered {} tools from MCP server '{}'", descriptors.size(), serverName);
    }

    private String buildActionableConnectErrorMessage(MCPClientProperties.ServerConfig server, Exception error) {
        String serverName = server != null && server.getName() != null ? server.getName() : "unknown";
        String serverUrl = server != null && server.getUrl() != null ? server.getUrl() : "<missing-url>";
        String original = error != null && error.getMessage() != null ? error.getMessage() : "Unknown error";
        String lower = original.toLowerCase();

        if (lower.contains("invalid sse response") || lower.contains("status code: 404") || lower.contains("<!doctype html")) {
            return "MCP endpoint failed with SSE (received HTML/404). "
                + "Server may require streamable HTTP transport instead of SSE. "
                + "Set symphony.mcp.client.transport=streamable or keep auto fallback enabled. "
                + "Verify endpoint/auth and that MCP initialize/listTools are reachable. "
                    + "server='" + serverName + "', url='" + serverUrl + "', original='" + original + "'";
        }

        if (lower.contains("failed to initialize") || lower.contains("initialize")) {
            return "MCP initialization failed. Check endpoint path, auth settings, and API key/token validity. "
                    + "If header auth is unsupported by transport in your runtime, prefer query auth (apiKeyQuery). "
                    + "server='" + serverName + "', url='" + serverUrl + "', original='" + original + "'";
        }

        return original;
    }

    private List<MCPClientProperties.ServerConfig> resolveAllServerRegistrations() {
        Map<String, MCPClientProperties.ServerConfig> merged = new LinkedHashMap<>();

        List<MCPClientProperties.ServerConfig> fromConfig = clientProperties.getServers() == null
                ? Collections.emptyList()
                : clientProperties.getServers();
        for (MCPClientProperties.ServerConfig server : fromConfig) {
            if (server == null || server.getName() == null || server.getName().isBlank()) {
                continue;
            }
            merged.put(server.getName(), server);
        }

        for (MCPClientProperties.ServerConfig server : loadFromKnowledgeBase()) {
            if (server.getName() == null || server.getName().isBlank()) {
                continue;
            }
            merged.putIfAbsent(server.getName(), server);
        }

        return merged.values().stream()
            .filter(Objects::nonNull)
            .filter(cfg -> cfg.isEnabled())
            .collect(Collectors.toList());
    }

    private List<MCPClientProperties.ServerConfig> loadFromKnowledgeBase() {
        Map<String, String> activeDescriptions;
        try {
            activeDescriptions = knowledgeBase.getActiveKnowledgeDescriptions();
        } catch (RuntimeException e) {
            logger.warn("Skipping Knowledge-backed MCP server registrations; configured servers are unaffected: {}",
                    e.getMessage());
            return Collections.emptyList();
        }
        if (activeDescriptions == null || activeDescriptions.isEmpty()) {
            return Collections.emptyList();
        }

        List<MCPClientProperties.ServerConfig> servers = new ArrayList<>();
        for (String name : activeDescriptions.keySet()) {
            try {
                Knowledge kb = knowledgeBase.GetByName(name);
                if (kb == null || kb.getType() != QueryType.MCP) {
                    continue;
                }
                JsonNode node = parseNode(kb.getData());
                if (!looksLikeMcpServerRegistration(node)) {
                    continue;
                }
                JsonNode nested = node.path("mcpServers");
                if (nested.isObject()) {
                    nested.fields().forEachRemaining(entry -> {
                        if (entry.getValue().hasNonNull("url")) {
                            servers.add(toServerConfig(resolveRegistration(entry.getKey(), entry.getValue())));
                        }
                    });
                } else {
                    servers.add(toServerConfig(resolveRegistration(name, node)));
                }
            } catch (Exception e) {
                logger.warn("Skipping MCP KB registration '{}' due to parse error: {}", name, e.getMessage());
            }
        }
        return servers;
    }

    private JsonNode parseNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean looksLikeMcpServerRegistration(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        if (node.has("mcpServers") && node.path("mcpServers").isObject()) {
            return true;
        }
        if (!node.hasNonNull("url")) {
            return false;
        }
        if (node.has("serverType") && "mcp".equalsIgnoreCase(node.path("serverType").asText())) {
            return true;
        }
        return node.path("mcp").asBoolean(false);
    }

    private record ServerRegistration(String name, JsonNode node) { }

    /** Resolves the same server name used when registering this knowledge. */
    public static String resolveServerName(String kbName, JsonNode node) {
        return resolveRegistration(kbName, node).name();
    }

    private static ServerRegistration resolveRegistration(String kbName, JsonNode node) {
        JsonNode mcpServers = node.path("mcpServers");
        if (mcpServers.isObject() && mcpServers.size() > 0) {
            if (kbName != null && !kbName.isBlank() && mcpServers.has(kbName)) {
                node = mcpServers.path(kbName);
            } else {
                Map.Entry<String, JsonNode> first = mcpServers.fields().next();
                kbName = first.getKey();
                node = first.getValue();
            }
        }
        String name = node.path("name").asText("");
        return new ServerRegistration(name.isBlank() ? kbName : name, node);
    }

    /** Resolves every named server in a nested MCP knowledge registration. */
    public static List<String> resolveServerNames(String kbName, JsonNode node) {
        JsonNode nested = node.path("mcpServers");
        if (nested.isObject() && !nested.isEmpty()) {
            List<String> names = new ArrayList<>();
            nested.fields().forEachRemaining(entry ->
                    names.add(resolveServerName(entry.getKey(), entry.getValue())));
            return names.stream().distinct().sorted().toList();
        }
        return List.of(resolveServerName(kbName, node));
    }

    private MCPClientProperties.ServerConfig toServerConfig(ServerRegistration registration) {
        JsonNode resolvedNode = registration.node();
        MCPClientProperties.ServerConfig config = new MCPClientProperties.ServerConfig();
        config.setName(registration.name());
        config.setUrl(resolvedNode.path("url").asText(null));
        config.setEnabled(resolvedNode.path("enabled").asBoolean(true));
        config.setTransport(resolvedNode.path("transport").asText(null));
        List<String> chatReadOnlyTools = new ArrayList<>();
        JsonNode chatTools = resolvedNode.path("chatReadOnlyTools");
        if (chatTools.isArray()) {
            chatTools.forEach(tool -> {
                if (tool.isTextual()) {
                    chatReadOnlyTools.add(tool.asText());
                }
            });
        }
        config.setChatReadOnlyTools(chatReadOnlyTools);
        config.setAuthType(resolvedNode.path("authType").asText(null));
        config.setAuthHeaderName(resolvedNode.path("authHeaderName").asText(null));
        config.setAuthKeyRef(resolvedNode.path("authKeyRef").asText(null));
        config.setAuthKey(resolvedNode.path("authKey").asText(null));
        config.setAuthQueryParamName(resolvedNode.path("authQueryParamName").asText(null));

        JsonNode headersNode = resolvedNode.path("headers");
        if (headersNode.isObject()) {
            String authorization = headersNode.path("Authorization").asText(null);
            if (authorization != null && !authorization.isBlank()) {
                config.setAuthType(AUTH_TYPE_BEARER);
                config.setAuthHeaderName(DEFAULT_BEARER_HEADER);
                String token = authorization;
                if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    token = authorization.substring(7).trim();
                }
                config.setAuthKey(token);
                if (token.startsWith("<") && token.endsWith(">")) {
                    logger.warn("MCP Authorization token for server '{}' appears to be a placeholder value: {}", config.getName(), token);
                }
            } else {
                String apiKey = headersNode.path("x-api-key").asText(null);
                if (apiKey != null && !apiKey.isBlank()) {
                    config.setAuthType(AUTH_TYPE_API_KEY_HEADER);
                    config.setAuthHeaderName("x-api-key");
                    config.setAuthKey(apiKey);
                }
            }
        }
        return config;
    }

    private String resolveAuthKey(MCPClientProperties.ServerConfig server) {
        if (server == null) {
            return null;
        }
        if (server.getAuthKey() != null && !server.getAuthKey().isBlank()) {
            return server.getAuthKey();
        }
        String ref = server.getAuthKeyRef();
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String value = environment.getProperty(ref);
        if (value == null || value.isBlank()) {
            value = System.getenv(ref);
        }
        return value;
    }

    private String applyQueryAuthIfConfigured(String baseUrl, MCPClientProperties.ServerConfig server, String key) {
        if (baseUrl == null) {
            return null;
        }
        String authType = normalizeAuthType(server);
        if (!AUTH_TYPE_API_KEY_QUERY.equals(authType) || key == null || key.isBlank()) {
            return baseUrl;
        }
        String paramName = server.getAuthQueryParamName() != null && !server.getAuthQueryParamName().isBlank()
                ? server.getAuthQueryParamName()
                : DEFAULT_QUERY_PARAM_NAME;
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + paramName + "=" + urlEncode(key);
    }

    private String normalizeAuthType(MCPClientProperties.ServerConfig server) {
        if (server == null || server.getAuthType() == null || server.getAuthType().isBlank()) {
            return AUTH_TYPE_NONE;
        }
        return server.getAuthType().trim();
    }

    private void applyHeaderAuthIfConfigured(HttpClientSseClientTransport.Builder builder,
                                             MCPClientProperties.ServerConfig server,
                                             String key) {
        String[] header = resolveAuthHeader(server, key);
        if (builder == null || header == null) {
            return;
        }
        builder.customizeRequest(request -> request.header(header[0], header[1]));
    }

    private void applyHeaderAuthIfConfigured(HttpClientStreamableHttpTransport.Builder builder,
                                             MCPClientProperties.ServerConfig server,
                                             String key) {
        String[] header = resolveAuthHeader(server, key);
        if (builder == null || header == null) {
            return;
        }
        builder.customizeRequest(request -> request.header(header[0], header[1]));
    }

    private String[] resolveAuthHeader(MCPClientProperties.ServerConfig server, String key) {
        if (server == null || key == null || key.isBlank()) {
            return null;
        }

        String authType = normalizeAuthType(server);
        String headerName = server.getAuthHeaderName();
        String headerValue;

        if (AUTH_TYPE_BEARER.equals(authType)) {
            headerName = (headerName == null || headerName.isBlank()) ? DEFAULT_BEARER_HEADER : headerName;
            headerValue = key.startsWith("Bearer ") ? key : "Bearer " + key;
            return new String[] { headerName, headerValue };
        }

        if (AUTH_TYPE_API_KEY_HEADER.equals(authType)) {
            headerName = (headerName == null || headerName.isBlank()) ? DEFAULT_API_KEY_HEADER : headerName;
            headerValue = key;
            return new String[] { headerName, headerValue };
        }

        return null;
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Lazily discovers likely relevant external MCP servers for the given query.
     * This avoids connecting to every configured server when many are registered.
     *
     * @param query user query text
     * @param maxServers maximum number of servers to connect for this warmup
     */
    public void warmupForQuery(String query, int maxServers) {
        if (configuredServers.isEmpty()) {
            return;
        }

        int limit = maxServers > 0 ? maxServers : maxServersPerQuery;
        if (limit <= 0) {
            return;
        }

        List<MCPClientProperties.ServerConfig> candidates = configuredServers.values().stream()
                .filter(Objects::nonNull)
            .filter(server -> server.isEnabled())
                .filter(server -> !clients.containsKey(server.getName()))
                .sorted(Comparator
                        .comparingInt((MCPClientProperties.ServerConfig server) -> scoreServerForQuery(server, query))
                        .reversed()
                .thenComparing((left, right) -> {
                    String leftName = left != null && left.getName() != null ? left.getName() : "";
                    String rightName = right != null && right.getName() != null ? right.getName() : "";
                    return leftName.compareToIgnoreCase(rightName);
                }))
                .limit(limit)
                .collect(Collectors.toList());

        for (MCPClientProperties.ServerConfig server : candidates) {
            ensureConnected(server.getName());
        }
    }

    private int scoreServerForQuery(MCPClientProperties.ServerConfig server, String query) {
        if (server == null) {
            return Integer.MIN_VALUE;
        }
        if (query == null || query.isBlank()) {
            return 0;
        }

        String haystack = ((server.getName() != null ? server.getName() : "") + " "
                + (server.getUrl() != null ? server.getUrl() : "")).toLowerCase();
        String[] terms = query.toLowerCase().split("[^a-z0-9]+");

        int score = 0;
        for (String term : terms) {
            if (term.length() < 3) {
                continue;
            }
            if (haystack.contains(term)) {
                score += Math.min(term.length(), 8);
            }
        }
        return score;
    }

    private McpSyncClient ensureConnected(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return null;
        }

        McpSyncClient existing = clients.get(serverName);
        if (existing != null) {
            return existing;
        }

        MCPClientProperties.ServerConfig server = configuredServers.get(serverName);
        if (server == null) {
            return null;
        }

        Object lock = serverLocks.computeIfAbsent(serverName, key -> new Object());
        synchronized (lock) {
            existing = clients.get(serverName);
            if (existing != null) {
                return existing;
            }
            boolean connected = connectToServer(server);
            if (!connected) {
                return null;
            }
            return clients.get(serverName);
        }
    }

    /** Lists only the selected server's tools, connecting it on demand. */
    public List<MCPToolDescriptor> listToolsForServer(String serverName) {
        if (ensureConnected(serverName) == null) {
            throw new IllegalStateException("MCP server is not registered or could not be reached");
        }
        String prefix = serverName + "/";
        return toolRegistry.listTools().stream()
                .filter(tool -> QUERY_TYPE_EXTERNAL.equals(tool.getQueryType()))
                .filter(tool -> tool.getName().startsWith(prefix) && tool.getName().indexOf('/', prefix.length()) < 0)
                .sorted(Comparator.comparing(MCPToolDescriptor::getName))
                .toList();
    }

    /** Returns whether a discovered tool is eligible for automatic read-only chat calls. */
    public boolean isChatToolAllowed(String serverName, MCPToolDescriptor tool) {
        MCPClientProperties.ServerConfig server = configuredServers.get(serverName);
        String prefix = serverName + "/";
        if (server == null || !tool.getName().startsWith(prefix) || Boolean.FALSE.equals(tool.getReadOnlyHint())) {
            return false;
        }
        return Boolean.TRUE.equals(tool.getReadOnlyHint())
                || server.getChatReadOnlyTools().contains(tool.getName().substring(prefix.length()));
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
            client = ensureConnected(serverName);
        }
        if (client == null) {
            MCPClientProperties.ServerConfig server = configuredServers.get(serverName);
            String url = server != null ? server.getUrl() : "<unknown-url>";
            throw new IllegalStateException(
                    "MCP server is not connected. "
                            + "Check that URL is the exact SSE endpoint and auth is valid. "
                            + "Expected HTTP 200 + text/event-stream. "
                            + "server='" + serverName + "', url='" + url + "'");
        }

        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
        if (Boolean.TRUE.equals(result.isError())) {
            throw new IllegalStateException("The MCP tool reported an execution error");
        }
        if (result.structuredContent() != null) {
            return objectMapper.valueToTree(result.structuredContent()).toString();
        }
        if (result.content() != null && !result.content().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (McpSchema.Content content : result.content()) {
                if (content instanceof McpSchema.TextContent textContent) {
                    sb.append(textContent.text());
                }
            }
            return sb.length() == 0 ? "{}" : sb.toString();
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

    /**
     * Returns the list of configured/registered external MCP server names.
     *
     * @return list of registered server names
     */
    public List<String> getRegisteredServers() {
        return new ArrayList<>(configuredServers.keySet());
    }

    /**
     * Returns connection status by registered server name.
     *
     * @return map of server name -> connection flag
     */
    public Map<String, Boolean> getServerConnectionStatus() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(configuredServers.keySet());
        names.sort((left, right) -> {
            String leftName = left == null ? "" : left;
            String rightName = right == null ? "" : right;
            return leftName.compareToIgnoreCase(rightName);
        });
        for (String name : names) {
            status.put(name, clients.containsKey(name));
        }
        return status;
    }

    /** @return whether lazy-connect mode is enabled */
    public boolean isLazyConnectEnabled() {
        return lazyConnect;
    }

    /** @return max servers to warm per query when lazy mode is enabled */
    public int getMaxServersPerQuery() {
        return maxServersPerQuery;
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
        if (schema.required() != null) {
            result.put("required", schema.required());
        }
        if (schema.additionalProperties() != null) {
            result.put("additionalProperties", schema.additionalProperties());
        }
        if (schema.defs() != null) {
            result.put("$defs", schema.defs());
        }
        if (schema.definitions() != null) {
            result.put("definitions", schema.definitions());
        }
        return result;
    }
}

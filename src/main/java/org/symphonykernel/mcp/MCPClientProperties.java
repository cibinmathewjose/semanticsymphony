package org.symphonykernel.mcp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for external MCP server connections.
 * <pre>
 * symphony.mcp.client.servers[0].name=weather-server
 * symphony.mcp.client.servers[0].url=http://localhost:3000
 * </pre>
 */
@Component
/** Configuration properties for MCP client connections. */
@ConfigurationProperties(prefix = "symphony.mcp.client")
@ConditionalOnProperty(value = "symphony.mcp.client.enabled", havingValue = "true", matchIfMissing = false)
public class MCPClientProperties {

    private List<ServerConfig> servers = new ArrayList<>();
    private Duration requestTimeout = Duration.ofSeconds(20);
    private Duration initializationTimeout = Duration.ofSeconds(20);
    private int maxToolDiscoveryPages = 10;

    /** @return deadline for each MCP request */
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /** @param timeout positive MCP request deadline */
    public void setRequestTimeout(Duration timeout) {
        requestTimeout = positiveTimeout(timeout);
    }

    /** @return deadline for MCP initialization */
    public Duration getInitializationTimeout() {
        return initializationTimeout;
    }

    /** @param timeout positive MCP initialization deadline */
    public void setInitializationTimeout(Duration timeout) {
        initializationTimeout = positiveTimeout(timeout);
    }

    /** @return maximum number of pages requested during tool discovery */
    public int getMaxToolDiscoveryPages() {
        return maxToolDiscoveryPages;
    }

    /** @param pages positive page limit for tool discovery */
    public void setMaxToolDiscoveryPages(int pages) {
        if (pages < 1) {
            throw new IllegalArgumentException("MCP tool discovery page limit must be positive");
        }
        maxToolDiscoveryPages = pages;
    }

    private static Duration positiveTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("MCP timeout must be positive");
        }
        return timeout;
    }

    /** @return the list of server configurations */
    public List<ServerConfig> getServers() {
        return servers;
    }

    /** @param servers the server configurations to set */
    public void setServers(List<ServerConfig> servers) {
        this.servers = servers;
    }

    /** Describes an external MCP server endpoint. */
    public static class ServerConfig {
        private String name;
        private String url;
        private boolean enabled = true;
        private String transport;
        private List<String> chatReadOnlyTools = List.of();
        private String authType;
        private String authHeaderName;
        private String authKeyRef;
        private String authKey;
        private String authQueryParamName;

        /** @return the server name */
        public String getName() {
            return name;
        }

        /** @param name the server name to set */
        public void setName(String name) {
            this.name = name;
        }

        /** @return the server URL */
        public String getUrl() {
            return url;
        }

        /** @param url the server URL to set */
        public void setUrl(String url) {
            this.url = url;
        }

        /** @return whether this server registration is enabled */
        public boolean isEnabled() {
            return enabled;
        }

        /** @param enabled whether this server registration is enabled */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return transport mode override (auto, sse, streamable) */
        public String getTransport() {
            return transport;
        }

        /** @param transport transport mode override (auto, sse, streamable) */
        public void setTransport(String transport) {
            this.transport = transport;
        }

        /** @return operator-verified read-only tools that omit MCP annotations */
        public List<String> getChatReadOnlyTools() {
            return chatReadOnlyTools;
        }

        /** @param tools unqualified names verified to be read-only */
        public void setChatReadOnlyTools(List<String> tools) {
            this.chatReadOnlyTools = tools == null ? List.of() : List.copyOf(tools);
        }

        /** @return auth type (apiKeyHeader, bearerToken, apiKeyQuery, none) */
        public String getAuthType() {
            return authType;
        }

        /** @param authType auth type (apiKeyHeader, bearerToken, apiKeyQuery, none) */
        public void setAuthType(String authType) {
            this.authType = authType;
        }

        /** @return auth header name (default: x-api-key or Authorization) */
        public String getAuthHeaderName() {
            return authHeaderName;
        }

        /** @param authHeaderName auth header name */
        public void setAuthHeaderName(String authHeaderName) {
            this.authHeaderName = authHeaderName;
        }

        /** @return key reference used to resolve secrets from env/properties */
        public String getAuthKeyRef() {
            return authKeyRef;
        }

        /** @param authKeyRef key reference used to resolve secrets from env/properties */
        public void setAuthKeyRef(String authKeyRef) {
            this.authKeyRef = authKeyRef;
        }

        /** @return inline auth key (for local/dev only) */
        public String getAuthKey() {
            return authKey;
        }

        /** @param authKey inline auth key (for local/dev only) */
        public void setAuthKey(String authKey) {
            this.authKey = authKey;
        }

        /** @return query parameter name for apiKeyQuery auth mode */
        public String getAuthQueryParamName() {
            return authQueryParamName;
        }

        /** @param authQueryParamName query parameter name for apiKeyQuery auth mode */
        public void setAuthQueryParamName(String authQueryParamName) {
            this.authQueryParamName = authQueryParamName;
        }
    }
}

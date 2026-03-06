package org.symphonykernel.mcp;

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
    }
}

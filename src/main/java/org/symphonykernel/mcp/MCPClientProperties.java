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
@ConfigurationProperties(prefix = "symphony.mcp.client")
@ConditionalOnProperty(value = "symphony.mcp.client.enabled", havingValue = "true", matchIfMissing = false)
public class MCPClientProperties {

    private List<ServerConfig> servers = new ArrayList<>();

    public List<ServerConfig> getServers() {
        return servers;
    }

    public void setServers(List<ServerConfig> servers) {
        this.servers = servers;
    }

    public static class ServerConfig {
        private String name;
        private String url;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}

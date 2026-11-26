package org.symphonykernel.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

class MCPClientServiceTest {
    private final MCPClientService service = new MCPClientService();

    @Test
    void discoveryRejectsPaginationBeyondTheConfiguredLimit() {
        var properties = new MCPClientProperties();
        properties.setMaxToolDiscoveryPages(1);
        var registry = discoveryRegistry(properties);
        var client = mock(McpSyncClient.class);
        var tool = new McpSchema.Tool("example", "An example tool", null, null, null, null, null);
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool), "next"));

        assertThatThrownBy(() -> service.registerDiscoveredTools("test", client))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("page limit");
        verify(client, never()).listTools("next");
        verifyNoInteractions(registry);
    }

    @Test
    void discoveryRejectsRepeatedCursorsAndInvalidDeadlines() {
        var properties = new MCPClientProperties();
        assertThatThrownBy(() -> properties.setRequestTimeout(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setInitializationTimeout(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        var registry = discoveryRegistry(properties);
        var client = mock(McpSyncClient.class);
        var page = new McpSchema.ListToolsResult(List.of(), "next");
        when(client.listTools()).thenReturn(page);
        when(client.listTools("next")).thenReturn(page);

        assertThatThrownBy(() -> service.registerDiscoveredTools("test", client))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("repeated cursor");
        verifyNoInteractions(registry);
    }

    private MCPToolRegistry discoveryRegistry(MCPClientProperties properties) {
        var registry = mock(MCPToolRegistry.class);
        ReflectionTestUtils.setField(service, "clientProperties", properties);
        ReflectionTestUtils.setField(service, "toolRegistry", registry);
        return registry;
    }

    @Test
    void streamableIsTheDefaultAndExplicitOverridesWin() {
        MCPClientProperties.ServerConfig server = new MCPClientProperties.ServerConfig();
        assertThat(service.resolveTransportMode(server)).isEqualTo("streamable");
        ReflectionTestUtils.setField(service, "transportMode", " SSE ");
        assertThat(service.resolveTransportMode(server)).isEqualTo("sse");
        server.setTransport("auto");
        assertThat(service.resolveTransportMode(server)).isEqualTo("auto");
    }

    @Test
    void nestedRegistrationUsesMatchingKeyOrFirstEntryAndRespectsNameOverride() throws Exception {
        var config = new ObjectMapper().readTree("""
                {"mcpServers":{"fallback":{},"inventory":{"name":"inventory-demo"}}}
                """);

        assertThat(MCPClientService.resolveServerName("inventory", config)).isEqualTo("inventory-demo");
        assertThat(MCPClientService.resolveServerName("other", config)).isEqualTo("fallback");
        assertThat(MCPClientService.resolveServerNames("other", config)).containsExactly("fallback", "inventory-demo");
    }
}
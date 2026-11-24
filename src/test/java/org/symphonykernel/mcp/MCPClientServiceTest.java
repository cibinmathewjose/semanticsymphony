package org.symphonykernel.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

class MCPClientServiceTest {
    private final MCPClientService service = new MCPClientService();

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
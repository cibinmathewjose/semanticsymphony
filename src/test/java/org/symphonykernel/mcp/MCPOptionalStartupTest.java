package org.symphonykernel.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;
import org.symphonykernel.core.IknowledgeBase;

import com.fasterxml.jackson.databind.ObjectMapper;

class MCPOptionalStartupTest {
    private final IknowledgeBase knowledgeBase = mock(IknowledgeBase.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertyBinding.class, MCPToolRegistry.class,
                    MCPClientProperties.class, MCPClientService.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(IknowledgeBase.class, () -> knowledgeBase);

    @Test
    void catalogFailureDoesNotBlockStartupWithoutMcpClient() {
        makeCatalogUnavailable();

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(MCPClientService.class);
            assertThat(context.getBean(MCPToolRegistry.class).listTools()).isEmpty();
        });
    }

    @Test
    void enabledClientKeepsConfiguredServersAndCanReloadAfterCatalogRecovery() {
        makeCatalogUnavailable();

        contextRunner.withPropertyValues(
                "symphony.mcp.client.enabled=true",
                "symphony.mcp.client.servers[0].name=configured",
                "symphony.mcp.client.servers[0].url=http://localhost:8080/mcp")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(MCPClientService.class);
                    MCPClientService client = context.getBean(MCPClientService.class);
                    MCPToolRegistry registry = context.getBean(MCPToolRegistry.class);
                    assertThat(registry.listTools()).isEmpty();
                    assertThat(client.getRegisteredServers()).containsExactly("configured");
                    assertThat(client.getConnectedServers()).isEmpty();

                    Knowledge registration = new Knowledge();
                    registration.setName("catalog-server");
                    registration.setType(QueryType.MCP);
                    registration.setData("""
                            {"mcp":true,"url":"http://localhost:8081/mcp"}
                            """);
                    doReturn(Map.of("catalog-server", "Catalog server"))
                            .when(knowledgeBase).getActiveKnowledgeDescriptions();
                    when(knowledgeBase.GetByName("catalog-server")).thenReturn(registration);

                    client.reloadRegistrations();

                    assertThat(registry.getTool("catalog-server")).isNotNull();
                    assertThat(client.getRegisteredServers()).containsExactlyInAnyOrder("configured", "catalog-server");
                });
    }

    private void makeCatalogUnavailable() {
        when(knowledgeBase.getActiveKnowledgeDescriptions())
                .thenThrow(new DataAccessResourceFailureException("Test catalog is offline"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties
    static class PropertyBinding {
    }
}
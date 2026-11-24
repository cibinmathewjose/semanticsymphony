package org.symphonykernel.steps;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.UserSession;
import org.symphonykernel.ai.KnowledgeExecuterFactory;
import org.symphonykernel.ai.KnowledgeGraphBuilder;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.mcp.MCPClientService;
import org.symphonykernel.mcp.MCPToolDescriptor;
import org.symphonykernel.providers.SessionProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

class MCPStepTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MCPClientService mcp = mock(MCPClientService.class);
    private final IAIClient model = mock(IAIClient.class);
    private final MCPStep step = new MCPStep() {
        @Override
        public void saveStepData(ExecutionContext context, String data) { }

        @Override
        public void saveStepData(ExecutionContext context, com.fasterxml.jackson.databind.JsonNode data) { }
    };
    private static final String INVENTORY = "inventory-demo";
    private final MCPToolDescriptor lookup = new MCPToolDescriptor(INVENTORY + "/get_product", "Look up product details",
            Map.of("type", "object", "required", List.of("id"), "properties", Map.of("id", Map.of("type", "string"))),
            "MCP_EXTERNAL");
    private final MCPToolDescriptor create = new MCPToolDescriptor(INVENTORY + "/create_product", "Create a product",
            Map.of(), "MCP_EXTERNAL");

    @BeforeEach
    void wireStep() {
        ReflectionTestUtils.setField(step, "objectMapper", mapper);
        ReflectionTestUtils.setField(step, "mcpClientService", mcp);
        ReflectionTestUtils.setField(step, "aiClient", model);
        when(mcp.listToolsForServer(INVENTORY)).thenReturn(List.of(lookup, create));
        when(mcp.isChatToolAllowed(INVENTORY, lookup)).thenReturn(true);
    }

    @Test
    void answersCapabilitiesFromSelectedCatalogWithoutExecutingTools() {
        when(model.execute(any())).thenAnswer(call -> {
            LLMRequest request = call.getArgument(0);
            assertThat(request.getUserPrompt()).contains("What can Inventory do?", "get_product", "Look up product details");
            return "{\"answer\":\"Inventory can look up product details and create products.\"}";
        });

        assertThat(step.getResponse(context("What can Inventory do?")).getMessage()).contains("look up product details");
        verify(mcp).listToolsForServer(INVENTORY);
        verify(mcp, never()).callTool(anyString(), any());
    }

    @Test
    void looksUpLiveDataAndGroundsTheAnswerInTheResult() {
        when(mcp.callTool(INVENTORY + "/get_product", Map.of("id", "P17"))).thenReturn("{\"name\":\"Night Cream\"}");
        when(model.execute(any())).thenAnswer(call -> {
            LLMRequest request = call.getArgument(0);
            assertThat(request.getUserPrompt()).contains("What is product P17 called?");
            if (request.getSystemMessage().contains("as evidence")) {
                assertThat(request.getUserPrompt()).contains("Night Cream");
                assertThat(request.getTools()).isNull();
                return "Product P17 is **Night Cream**.";
            }
            return "{\"tool\":\"get_product\",\"arguments\":{\"id\":\"P17\"}}";
        });

        assertThat(step.getResponse(context("What is product P17 called?")).getMessage()).contains("Night Cream");
        verify(mcp).callTool(INVENTORY + "/get_product", Map.of("id", "P17"));
    }

    @Test
    void rejectsWritesAndMissingRequiredArguments() {
        when(model.execute(any())).thenReturn("{\"tool\":\"create_product\",\"arguments\":{}}",
                "{\"tool\":\"get_product\",\"arguments\":{}}");

        assertThat(step.getResponse(context("Create a product")).getStatusCode()).isEqualTo("ERROR");
        assertThat(step.getResponse(context("Find a product")).getMessage()).contains("valid inputs");
        verify(mcp, never()).callTool(anyString(), any());
    }

    @Test
    void explicitToolPayloadStillDispatchesDirectly() throws Exception {
        ExecutionContext context = context("Create a product");
        context.setVariables(mapper.readTree("{\"tool\":\"create_product\",\"arguments\":{\"name\":\"Cream\"}}"));
        when(mcp.callTool(INVENTORY + "/create_product", Map.of("name", "Cream"))).thenReturn("{}");

        step.getResponse(context);

        verify(mcp).callTool(INVENTORY + "/create_product", Map.of("name", "Cream"));
        verify(mcp, never()).listToolsForServer(anyString());
        verify(model, never()).execute(any());
    }

    @Test
    void discoversAcrossServersAndRoutesQualifiedToolDespiteOneUnavailableServer() {
        ExecutionContext context = context("What is the catalog label for product P17?");
        context.getKnowledge().setData("{\"discover\":true}");
        MCPToolDescriptor catalogLookup = new MCPToolDescriptor("catalog/get_product", "Look up catalog label",
                lookup.getInputSchema(), "MCP_EXTERNAL");
        when(mcp.getRegisteredServers()).thenReturn(List.of(INVENTORY, "catalog", "unavailable"));
        when(mcp.listToolsForServer("catalog")).thenReturn(List.of(catalogLookup));
        when(mcp.listToolsForServer("unavailable")).thenThrow(new IllegalStateException());
        when(mcp.isChatToolAllowed("catalog", catalogLookup)).thenReturn(true);
        when(mcp.callTool("catalog/get_product", Map.of("id", "P17"))).thenReturn("{\"label\":\"Night Cream\"}");
        when(model.execute(any())).thenAnswer(call -> {
            LLMRequest request = call.getArgument(0);
            if (request.getSystemMessage().contains("as evidence")) {
                assertThat(request.getUserPrompt()).contains("Night Cream");
                return "The catalog label is Night Cream.";
            }
            assertThat(request.getUserPrompt()).contains("catalog/get_product", INVENTORY + "/get_product", "unavailable");
            return "{\"tool\":\"catalog/get_product\",\"arguments\":{\"id\":\"P17\"}}";
        });

        assertThat(step.getResponse(context).getMessage()).contains("Night Cream");
        verify(mcp).callTool("catalog/get_product", Map.of("id", "P17"));
        verify(mcp, never()).callTool(org.mockito.ArgumentMatchers.eq(INVENTORY + "/get_product"), any());
    }

    @Test
    void relatedFollowupRediscoversToolsWithoutNamingAServer() {
        var graph = new KnowledgeGraphBuilder();
        var sessions = mock(SessionProvider.class);
        var factory = mock(KnowledgeExecuterFactory.class);
        ReflectionTestUtils.setField(graph, "sessionManager", sessions);
        ReflectionTestUtils.setField(graph, "knowledgeExecuterFactory", factory);
        ReflectionTestUtils.setField(graph, "humanInLoopStateStore", mock(HumanInLoopStateStore.class));
        ReflectionTestUtils.setField(graph, "openAI", model);
        ReflectionTestUtils.setField(graph, "mcpClientService", mcp);
        ReflectionTestUtils.setField(graph, "mcpChatFallback", true);

        var previous = new UserSession();
        previous.setRequestId("previous");
        previous.setUserId("operator");
        previous.setSessionID("session-1");
        previous.setKnowldgeName("dynamic-mcp-discovery");
        previous.setBotResponse("Which product should I look up?");
        var current = new UserSession();
        current.setRequestId("current");
        var request = new ChatRequest();
        request.setUser("operator");
        request.setSession("session-1");
        request.setConversationId("previous");
        request.setQuery("P17, please");
        when(sessions.getRequest("previous")).thenReturn(previous);
        when(sessions.createUserSession(request)).thenReturn(current);
        when(sessions.getChatHistory(request)).thenReturn(List.of(new UserMessage("Find the product name")));
        when(factory.getExecuter(any())).thenReturn(step);
        when(model.evaluatePrompt(anyString())).thenReturn("FOLLOWUP");
        when(mcp.getRegisteredServers()).thenReturn(List.of(INVENTORY));
        when(mcp.callTool(INVENTORY + "/get_product", Map.of("id", "P17"))).thenReturn("Night Cream");
        when(model.execute(any())).thenAnswer(call -> {
            LLMRequest llmRequest = call.getArgument(0);
            assertThat(llmRequest.getUserPrompt()).contains("P17, please", "Find the product name");
            return llmRequest.getSystemMessage().contains("as evidence") ? "P17 is Night Cream."
                    : "{\"tool\":\"get_product\",\"arguments\":{\"id\":\"P17\"}}";
        });

        assertThat(graph.process(request).getMessage()).contains("Night Cream");
        verify(mcp).callTool(INVENTORY + "/get_product", Map.of("id", "P17"));
    }

    private static ExecutionContext context(String query) {
        Knowledge knowledge = new Knowledge();
        knowledge.setName("Inventory");
        knowledge.setData("{\"mcpServers\":{\"" + INVENTORY + "\":{\"url\":\"https://example.com/api/mcp\"}}}");
        ExecutionContext context = new ExecutionContext();
        context.setKnowledge(knowledge);
        context.setUsersQuery(query);
        return context;
    }
}
package org.symphonykernel.agentic;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.QueryType;
import org.symphonykernel.ai.KnowledgeExecuterFactory;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IUserSessionBase;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.mcp.MCPClientService;
import org.symphonykernel.mcp.MCPToolDescriptor;
import org.symphonykernel.mcp.MCPToolRegistry;
import org.symphonykernel.steps.AgenticStep;
import org.symphonykernel.steps.DatabaseStep;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class AgenticPlannerTest {
    private static final String LOOKUP = "{\"tool\":\"lookup\",\"arguments\":{\"id\":7}}";
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgenticPlanner planner = new AgenticPlanner();
    private final IAIClient ai = mock(IAIClient.class);
    private final MCPToolRegistry registry = mock(MCPToolRegistry.class);
    private final IknowledgeBase knowledge = mock(IknowledgeBase.class);
    private final KnowledgeExecuterFactory factory = mock(KnowledgeExecuterFactory.class);
    private final IStep tool = mock(IStep.class);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(planner, "aiClient", ai);
        ReflectionTestUtils.setField(planner, "toolRegistry", registry);
        ReflectionTestUtils.setField(planner, "knowledgeBase", knowledge);
        ReflectionTestUtils.setField(planner, "factory", factory);
        ReflectionTestUtils.setField(planner, "mapper", mapper);
        var descriptor = new MCPToolDescriptor("lookup", "Inventory lookup", Map.of("type", "object",
                "properties", Map.of("id", Map.of("type", "integer")), "required", List.of("id")), "TOOL");
        var nested = new MCPToolDescriptor("nested", "Another agent", Map.of(), "AGENTIC");
        nested.setReadOnlyHint(true);
        var writer = new MCPToolDescriptor("writer", "Locally configured write tool", Map.of(), "TOOL");
        writer.setReadOnlyHint(true);
        var declaredWriter = new MCPToolDescriptor("declared-writer", "Tool with a write hint", Map.of(), "TOOL");
        declaredWriter.setReadOnlyHint(false);
        when(registry.listTools()).thenReturn(List.of(descriptor, nested, writer, declaredWriter));
        when(registry.getTool("lookup")).thenReturn(descriptor);
    }

    @AfterEach
    void shutdown() {
        planner.shutdown();
    }

    @Test
    void restrictionsCannotEnableWriteToolsOrBlockedTypes() {
        when(ai.execute(any())).thenReturn(actions(LOOKUP));
        assertThat(planner.execute(context("{\"allowedTools\":[]}")).getMessage()).contains("not allowed");
        planner.setWriteTools(" writer ");
        ReflectionTestUtils.setField(planner, "readOnlyTools", "nested,writer,declared-writer");
        assertThat(planner.execute(context("{\"allowedTools\":[\"lookup\"]}")).getMessage()).contains("not allowed");
        for (String name : List.of("nested", "writer", "declared-writer")) {
            when(ai.execute(any())).thenReturn(actions("{\"tool\":\"" + name + "\"}"));
            assertThat(planner.execute(context("{\"allowedTools\":[\"" + name + "\"]}")).getMessage())
                    .contains("not allowed");
        }
        verifyNoInteractions(knowledge, factory, tool);
    }

    @Test
    void reportsRejectedToolInStatusAndWarning(CapturedOutput output) {
        ReflectionTestUtils.setField(planner, "readOnlyTools", "lookup");
        when(ai.execute(any())).thenReturn(actions(LOOKUP + ",{\"tool\":\"warehouse/get-stock\"}"));
        String message = "Agent requested a tool that is not allowed: 'warehouse/get-stock'.";

        ChatResponse response = planner.execute(context("{}"));
        assertThat(response.getStatusCode()).isEqualTo("ERROR");
        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(planner.executeStream(context("{}")).collectList().block(Duration.ofSeconds(5)))
                .containsExactly(message);
        assertThat(output.getAll()).contains(message);
        verifyNoInteractions(knowledge, factory, tool);
    }

    @Test
    void doesNotEchoMalformedToolNames(CapturedOutput output) {
        when(ai.execute(any())).thenReturn(actions("{\"tool\":\"lookup\\r\\nforged-log-line\"}"));
        String message = "Agent requested a tool that is not allowed: '<invalid tool name>'.";

        assertThat(planner.execute(context("{}")).getMessage()).isEqualTo(message);
        assertThat(output.getAll()).contains(message).doesNotContain("forged-log-line");
        verifyNoInteractions(knowledge, factory, tool);
    }

    @Test
    void discoversEligibleMcpToolsWithoutARestriction() {
        var mcp = mock(MCPClientService.class);
        ReflectionTestUtils.setField(planner, "mcpClientService", mcp);
        var descriptor = new MCPToolDescriptor("warehouse/stock", "Stock lookup", Map.of(), "MCP_EXTERNAL");
        descriptor.setReadOnlyHint(true);
        when(mcp.getRegisteredServers()).thenReturn(List.of("warehouse"));
        when(mcp.listToolsForServer("warehouse")).thenAnswer(invocation -> {
            when(registry.listTools()).thenReturn(List.of(descriptor));
            return List.of(descriptor);
        });
        when(registry.getTool("warehouse/stock")).thenReturn(descriptor);
        when(mcp.isChatToolAllowed("warehouse", descriptor)).thenReturn(true);
        when(mcp.callTool("warehouse/stock", Map.of())).thenReturn("Stock found");
        when(ai.execute(any())).thenReturn(actions("{\"tool\":\"warehouse/stock\"}"),
                "{\"isComplete\":true,\"finalAnswer\":\"Found\"}");
        assertThat(planner.execute(context("{}")).getMessage()).isEqualTo("Found");
        verify(mcp).listToolsForServer("warehouse");
        planner.setWriteTools("warehouse/stock");
        when(ai.execute(any())).thenReturn(actions("{\"tool\":\"warehouse/stock\"}"));
        assertThat(planner.execute(context("{}")).getMessage()).contains("not allowed");
        verify(mcp).callTool("warehouse/stock", Map.of());
    }

    @Test
    void rejectsInvalidBatchesBeforeDispatchingAnyTools() {
        ReflectionTestUtils.setField(planner, "readOnlyTools", "lookup");
        when(ai.execute(any())).thenReturn(actions(LOOKUP + ",{\"tool\":\"lookup\",\"arguments\":{\"id\":\"7\"}}"));
        assertThat(planner.execute(context("{}")).getMessage()).contains("schema");
        ReflectionTestUtils.setField(planner, "maxActions", 1);
        when(ai.execute(any())).thenReturn(actions(LOOKUP + "," + LOOKUP));
        assertThat(planner.execute(context("{}")).getMessage()).contains("action limit");
        when(ai.execute(any())).thenReturn("private upstream response");
        assertThat(planner.execute(context("{}")).getMessage()).isEqualTo("Agent returned an invalid plan.");
        verifyNoInteractions(knowledge, factory, tool);
    }

    @Test
    void isolatesInputsAndUsesOneResultContractForSyncAndStream() {
        ExecutionContext ctx = context("{\"systemPrompt\":\"Use inventory terminology\"}");
        var details = new Knowledge();
        details.setType(QueryType.TOOL);
        when(knowledge.GetByName("lookup")).thenReturn(details);
        when(factory.getExecuter(details)).thenReturn(tool);
        when(tool.getResponse(any())).thenAnswer(invocation -> {
            ExecutionContext child = invocation.getArgument(0);
            assertThat(child.getRequest()).isNotSameAs(ctx.getRequest());
            assertThat(child.getRequest().getPayload()).isEqualTo("{\"id\":7}");
            assertThat(child.getVariables().path("id").asInt()).isEqualTo(7);
            assertThat(child.getResolvedValues().get("previous").path("stock").asInt()).isEqualTo(9);
            child.getRequest().setPayload("changed");
            var result = new ChatResponse();
            result.setMessage("12 units");
            return result;
        });
        when(ai.execute(any())).thenAnswer(invocation -> {
            LLMRequest request = invocation.getArgument(0);
            assertThat(request.getSystemMessage()).contains("inventory terminology");
            return request.getUserPrompt().contains("12 units")
                    ? "{\"isComplete\":true,\"finalAnswer\":{\"stock\":12}}" : actions(LOOKUP);
        });
        ChatResponse sync = planner.execute(ctx);
        var events = planner.streamEvents(ctx).collectList().block(Duration.ofSeconds(5));
        assertThat(events).hasSize(3);
        assertThat(events.get(0).message()).startsWith("Executing:");
        assertThat(events.get(1).message()).startsWith("Completed:");
        assertThat(events.get(2).result().getData()).isEqualTo(sync.getData());
        assertThat(sync.getData().at("/0/stock").asInt()).isEqualTo(12);
        assertThat(ctx.getRequest().getPayload()).isEqualTo("parent");
    }

    @Test
    void runsConfiguredSourcesBeforeSynthesisAndKeepsPartialResults() {
        var ctx = context("{}");
        ctx.setKnowledge(AgenticPlanner.defaultKnowledge(List.of("db", "web", "docs", "writer")));
        planner.setWriteTools("writer");
        var db = defaultStep("db", QueryType.DATABASE);
        var web = defaultStep("web", QueryType.WEBSEARCH);
        var docs = defaultStep("docs", QueryType.AZURE_AI_SEARCH);
        when(registry.getTool("writer")).thenReturn(new MCPToolDescriptor("writer", "", Map.of(), "TOOL"));
        when(db.getResponse(any())).thenAnswer(call -> {
            ExecutionContext child = call.getArgument(0);
            assertThat(child.getUsersQuery()).isEqualTo("Inventory request");
            assertThat(child.getRequest()).isNotSameAs(ctx.getRequest());
            assertThat(child.getVariables().isEmpty()).isTrue();
            return new ChatResponse("Stock: 12 units");
        });
        when(web.getResponse(any())).thenThrow(new IllegalStateException("private error"));
        when(docs.getResponse(any())).thenReturn(new ChatResponse("Product handbook"));
        when(ai.execute(any())).thenAnswer(call -> {
            LLMRequest request = call.getArgument(0);
            assertThat(request.getSystemMessage()).contains("untrusted evidence");
            assertThat(request.getUserPrompt()).contains("12 units", "Product handbook", "unavailable")
                    .doesNotContain("private error", "previous");
            return "Consolidated answer";
        });
        assertThat(planner.execute(ctx).getMessage()).isEqualTo("Consolidated answer");
        var events = planner.streamEvents(ctx).collectList().block(Duration.ofSeconds(5));
        assertThat(events.get(events.size() - 1).result().getMessage()).isEqualTo("Consolidated answer");
        verify(db, times(2)).getResponse(any());
        verify(web, times(2)).getResponse(any());
        verify(docs, times(2)).getResponse(any());
        verify(ai, times(2)).execute(any());
        assertThat(ctx.getRequest().getPayload()).isEqualTo("parent");
    }

    @Test
    void streamsDatabaseMilestonesBeforeSourceFinishesAndKeepsThemOutOfAnswer() {
        var ctx = context("{}");
        ctx.setKnowledge(AgenticPlanner.defaultKnowledge(List.of("database-source")));
        var kb = new Knowledge();
        kb.setName("database-source");
        kb.setType(QueryType.DATABASE);
        var db = mock(DatabaseStep.class);
        when(registry.getTool(kb.getName())).thenReturn(new MCPToolDescriptor(kb.getName(), "", Map.of(), "DATABASE"));
        when(knowledge.GetByName(kb.getName())).thenReturn(kb);
        when(factory.getExecuter(kb)).thenReturn(db);
        var seen = new CountDownLatch(1);
        when(db.getResponse(any(), any())).thenAnswer(call -> {
            Consumer<String> progress = call.getArgument(1);
            progress.accept("Generating database query...");
            progress.accept("Executing database query...");
            assertThat(seen.await(2, TimeUnit.SECONDS)).isTrue();
            return new ChatResponse("Stock: 12 units");
        });
        when(ai.execute(any())).thenAnswer(call -> {
            LLMRequest request = call.getArgument(0);
            assertThat(request.getUserPrompt()).contains("Stock: 12 units").doesNotContain("Executing database query");
            return "12 units in stock";
        });
        var step = new AgenticStep();
        ReflectionTestUtils.setField(step, "agenticPlanner", planner);
        ReflectionTestUtils.setField(step, "objectMapper", mapper);
        ReflectionTestUtils.setField(step, "sessionBase", mock(IUserSessionBase.class));

        var events = step.getResponseStream(ctx).doOnNext(event -> {
            if (event.contains("Executing database query...")) seen.countDown();
        }).collectList().block(Duration.ofSeconds(5));

        assertThat(events).hasSize(5);
        assertThat(events.get(1)).contains("\"eventType\":\"status\"", "Generating database query...");
        assertThat(events.get(2)).contains("\"eventType\":\"status\"", "Executing database query...");
        assertThat(events.get(4)).contains("\"eventType\":\"content\"", "12 units in stock");
        verify(db).getResponse(any(), any());
    }

    @Test
    void logsWhenConfiguredDefaultIsNotRegistered(CapturedOutput output) {
        String sourceName = "missing-source";
        var ctx = context("{}");
        ctx.setKnowledge(AgenticPlanner.defaultKnowledge(List.of(sourceName)));
        ReflectionTestUtils.setField(planner, "readOnlyTools", sourceName);

        assertThat(planner.execute(ctx).getMessage())
                .isEqualTo("No configured default Knowledge source returned usable evidence.");
        assertThat(output.getAll()).contains("Default Knowledge '" + sourceName + "' unavailable: not registered");
        verifyNoInteractions(ai, knowledge, factory, tool);
    }

    @Test
    void neverSynthesizesWithoutEvidenceAndHonorsSourceLimits() {
        var ctx = context("{}");
        ctx.setKnowledge(AgenticPlanner.defaultKnowledge(List.of("blocked", "lookup")));
        when(registry.getTool("blocked")).thenReturn(new MCPToolDescriptor("blocked", "", Map.of(), "AGENTIC"));
        ReflectionTestUtils.setField(planner, "readOnlyTools", "blocked");
        assertThat(planner.execute(ctx).getMessage()).contains("usable evidence");
        ReflectionTestUtils.setField(planner, "maxActions", 1);
        assertThat(planner.execute(ctx).getMessage()).contains("action limit");
        verifyNoInteractions(ai, factory, tool);
    }

    @Test
    void synthesizesFastEvidenceWhenAnotherDefaultTimesOut() {
        var ctx = context("{}");
        ctx.setKnowledge(AgenticPlanner.defaultKnowledge(List.of("slow", "fast")));
        planner.setTimeout("PT1.5S");
        var release = new CountDownLatch(1);
        var slow = defaultStep("slow", QueryType.WEBSEARCH);
        var fast = defaultStep("fast", QueryType.AZURE_AI_SEARCH);
        when(slow.getResponse(any())).thenAnswer(call -> {
            release.await(4, TimeUnit.SECONDS);
            return new ChatResponse("late result");
        });
        when(fast.getResponse(any())).thenReturn(new ChatResponse("fast result"));
        when(ai.execute(any())).thenAnswer(call -> {
            LLMRequest request = call.getArgument(0);
            assertThat(request.getUserPrompt()).contains("unavailable", "fast result").doesNotContain("late result");
            return "Partial answer";
        });
        try {
            assertThat(planner.execute(ctx).getMessage()).isEqualTo("Partial answer");
        } finally {
            release.countDown();
        }
    }

    private IStep defaultStep(String name, QueryType type) {
        var kb = new Knowledge();
        kb.setName(name);
        kb.setType(type);
        var step = mock(IStep.class);
        when(registry.getTool(name)).thenReturn(new MCPToolDescriptor(name, "", Map.of(), type.name()));
        when(knowledge.GetByName(name)).thenReturn(kb);
        when(factory.getExecuter(kb)).thenReturn(step);
        return step;
    }

    @Test
    void deadlineReturnsEvenIfPlanningDoesNotFinish() {
        planner.setTimeout("PT0.15S");
        var release = new CountDownLatch(1);
        when(ai.execute(any())).thenAnswer(invocation -> {
            release.await(4, TimeUnit.SECONDS);
            return actions(LOOKUP);
        });
        try {
            assertThat(planner.execute(context("{}")).getMessage()).contains("deadline");
            assertThat(planner.executeStream(context("{}")).collectList().block(Duration.ofSeconds(2)))
                    .singleElement().asString().contains("deadline");
            verifyNoInteractions(knowledge, factory, tool);
        } finally {
            release.countDown();
        }
    }

    private ExecutionContext context(String data) {
        var ctx = new ExecutionContext();
        var kb = new Knowledge();
        kb.setData(data);
        ctx.setKnowledge(kb);
        ctx.setUsersQuery("Inventory request");
        var parent = new ChatRequest();
        parent.setPayload("parent");
        ctx.setRequest(parent);
        ctx.getResolvedValues().put("previous", mapper.createObjectNode().put("stock", 9));
        return ctx;
    }

    private static String actions(String actions) {
        return "{\"isComplete\":false,\"actions\":[" + actions + "]}";
    }
}
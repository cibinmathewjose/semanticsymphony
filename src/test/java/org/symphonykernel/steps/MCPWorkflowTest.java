package org.symphonykernel.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;
import org.symphonykernel.UserSession;
import org.symphonykernel.ai.KnowledgeExecuterFactory;
import org.symphonykernel.ai.KnowledgeGraphBuilder;
import org.symphonykernel.core.IUserSessionBase;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.mcp.MCPClientService;
import org.symphonykernel.providers.SessionProvider;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.databind.ObjectMapper;

class MCPWorkflowTest {
    @Test
    void streamedWorkflowUsesNativeToolDataAndResumesFromChatWithoutReplayingLookup() {
        var mapper = new ObjectMapper();
        var registry = mock(IknowledgeBase.class);
        var factory = mock(KnowledgeExecuterFactory.class);
        var sessions = mock(SessionProvider.class);
        var mcp = mock(MCPClientService.class);
        var humanState = mock(HumanInLoopStateStore.class);
        var checkpoints = mock(WorkflowCheckpointStore.class);
        var checkpoint = new AtomicReference<WorkflowCheckpoint>();
        doAnswer(call -> { checkpoint.set(call.getArgument(0)); return null; }).when(checkpoints).save(any());
        when(checkpoints.load("request-1")).thenAnswer(call -> checkpoint.get());

        var lookup = knowledge("lookup-stock", QueryType.MCP, "{\"name\":\"warehouse\",\"tool\":\"stock\"}");
        var approval = knowledge("ask-human", QueryType.HUMANLOOP, "{\"question\":\"Continue with {{$stock}}?\"}");
        var next = knowledge("post-approval", QueryType.MCP, "{\"name\":\"warehouse\",\"tool\":\"check\"}");
        var workflow = knowledge("inventory-flow", QueryType.SYMPHONY, """
                {"Flow":[
                   {"Name":"lookup-stock","Key":"stock","Payload":"input","Order":1},
                   {"Name":"ask-human","Key":"approval","Order":2},
                   {"Name":"post-approval","Key":"checked","Payload":"stock,approval","Order":3}
                ],"Result":"checked"}
                """);
        for (Knowledge knowledge : new Knowledge[] {lookup, approval, next, workflow}) {
            when(registry.GetByName(knowledge.getName())).thenReturn(knowledge);
        }
        when(mcp.callTool("warehouse/stock", Map.of("sku", "P17"))).thenReturn("{\"quantity\":7}");
        when(mcp.callTool("warehouse/check", Map.of("quantity", 7, "approval", "Continue")))
                .thenReturn("{\"checked\":true}");

        var step = new MCPStep();
        var human = new HumanInLoopStep();
        var symphony = new Symphony();
        for (BaseStep base : new BaseStep[] {step, human, symphony}) {
            ReflectionTestUtils.setField(base, "objectMapper", mapper);
            ReflectionTestUtils.setField(base, "knowledgeBase", registry);
            ReflectionTestUtils.setField(base, "sessionBase", mock(IUserSessionBase.class));
        }
        ReflectionTestUtils.setField(step, "mcpClientService", mcp);
        ReflectionTestUtils.setField(human, "stateStore", humanState);
        ReflectionTestUtils.setField(human, "templateResolver", new TemplateResolver());
        ReflectionTestUtils.setField(symphony, "knowledgeExecuterFactory", factory);
        ReflectionTestUtils.setField(symphony, "workflowCheckpointStore", checkpoints);
        when(factory.getExecuter(lookup)).thenReturn(step);
        when(factory.getExecuter(next)).thenReturn(step);
        when(factory.getExecuter(approval)).thenReturn(human);
        when(factory.getExecuter(workflow)).thenReturn(symphony);

        var graph = new KnowledgeGraphBuilder();
        ReflectionTestUtils.setField(graph, "objectMapper", mapper);
        ReflectionTestUtils.setField(graph, "knowledgeBaserepo", registry);
        ReflectionTestUtils.setField(graph, "knowledgeExecuterFactory", factory);
        ReflectionTestUtils.setField(graph, "workflowCheckpointStore", checkpoints);
        ReflectionTestUtils.setField(graph, "humanInLoopStateStore", humanState);
        ReflectionTestUtils.setField(graph, "sessionManager", sessions);

        var session = new UserSession();
        session.setRequestId("request-1");
        session.setSessionID("session-1");
        session.setUserId("operator");
        session.setUserInput("Check inventory");
        when(sessions.getRequest("request-1")).thenReturn(session);
        ExecutionContext context = new ExecutionContext().setRequest(request("Check inventory"))
                .setUserSession(session).setKnowledge(workflow).setUsersQuery("Check inventory")
                .setVariables(mapper.createObjectNode().put("sku", "P17"));

        var paused = graph.streamResponse(context).collectList().block(Duration.ofSeconds(5));
        assertThat(paused).anyMatch(event -> event.contains("AWAITING_INPUT") && event.contains("quantity"));
        assertThat(checkpoint.get().getResolvedValues().get("stock").path("quantity").asInt()).isEqualTo(7);
        when(humanState.getPendingRequestIdBySession("session-1")).thenReturn("request-1");
        when(humanState.submitInput("request-1", "Continue")).thenReturn(true);
        when(humanState.consumeSubmittedInput("request-1")).thenReturn("Continue");

        var completed = graph.streamProcess(request("Continue")).collectList().block(Duration.ofSeconds(5));
        assertThat(completed).anyMatch(event -> event.contains("final_response") && event.contains("checked"));
        verify(mcp).callTool("warehouse/stock", Map.of("sku", "P17"));
        verify(mcp).callTool("warehouse/check", Map.of("quantity", 7, "approval", "Continue"));
    }

    private static ChatRequest request(String text) {
        var request = new ChatRequest();
        request.setQuery(text);
        request.setSession("session-1");
        request.setUser("operator");
        return request;
    }

    private static Knowledge knowledge(String name, QueryType type, String data) {
        var knowledge = new Knowledge();
        knowledge.setName(name);
        knowledge.setType(type);
        knowledge.setData(data);
        return knowledge;
    }
}
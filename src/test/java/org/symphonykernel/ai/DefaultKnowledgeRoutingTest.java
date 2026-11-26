package org.symphonykernel.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;
import org.symphonykernel.UserSession;
import org.symphonykernel.config.SymphonyConfig;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.providers.FileContentProvider;
import org.symphonykernel.providers.SessionProvider;
import org.symphonykernel.steps.HumanInLoopStateStore;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;

class DefaultKnowledgeRoutingTest {
    private final KnowledgeGraphBuilder graph = spy(new KnowledgeGraphBuilder());
    private final IknowledgeBase repository = mock(IknowledgeBase.class);
    private final IAIClient ai = mock(IAIClient.class);
    private final QueryHandler sql = mock(QueryHandler.class);
    private final SessionProvider sessions = mock(SessionProvider.class);
    private final FileContentProvider prompts = mock(FileContentProvider.class);
    private SymphonyConfig config;

    @BeforeEach
    void prepare() {
        clearKnowledgeCache();
        config = new Binder(new MapConfigurationPropertySource(Map.of(
                "symphony.ai.autonomous", "true", "symphony.ai.default-knowledge", " db, web, docs, db ")))
                .bind("symphony.ai", Bindable.of(SymphonyConfig.class)).get();
        when(repository.getActiveKnowledgeDescriptions()).thenReturn(Map.of("known", "description"));
        when(prompts.prepareMatchKnowledgePrompt("{\"known\":\"description\"}", "question", "{}"))
                .thenReturn("intent prompt");
        ReflectionTestUtils.setField(graph, "symphonyConfig", config);
        ReflectionTestUtils.setField(graph, "fileContentProvider", prompts);
        ReflectionTestUtils.setField(graph, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(graph, "knowledgeBaserepo", repository);
        ReflectionTestUtils.setField(graph, "openAI", ai);
        ReflectionTestUtils.setField(graph, "queryHandler", sql);
        ReflectionTestUtils.setField(graph, "sessionManager", sessions);
        ReflectionTestUtils.setField(graph, "humanInLoopStateStore", mock(HumanInLoopStateStore.class));
    }

    @AfterEach
    void clearKnowledgeCache() {
        ReflectionTestUtils.setField(KnowledgeGraphBuilder.class, "knowledgeDescJsonCache", null);
    }

    @Test
    void registeredMatchesWinAndUnmatchedQuestionsUseConfiguredDefaults() {
        var known = new Knowledge();
        known.setName("known");
        when(repository.GetByName("known")).thenReturn(known);
        when(ai.evaluatePrompt("intent prompt")).thenReturn("known", "missing", "known", "missing");
        for (boolean autonomous : List.of(true, false)) {
            config.setAutonomous(autonomous);
            assertThat(route()).isSameAs(known);
            assertThat(route().getName()).isEqualTo("default-agentic");
        }
        assertThat(config.getDefaultKnowledge()).containsExactly("db", "web", "docs");
        verifyNoInteractions(sql);
    }

    @Test
    void emptyDefaultsKeepExistingSqlFallbackWithEitherAutonomySetting() {
        when(ai.evaluatePrompt("intent prompt")).thenReturn("NONE");
        when(sql.matchSelectQuery("question", null)).thenReturn("SELECT 1");
        config.setDefaultKnowledge(List.of());
        config.setAutonomous(false);
        assertThat(route().getType()).isEqualTo(QueryType.SQL);
        config.setAutonomous(true);
        assertThat(route().getType()).isEqualTo(QueryType.SQL);
    }

    @Test
    void explicitKnowledgeWinsOverDefaultsWhenAutonomyIsDisabled() {
        config.setAutonomous(false);
        var known = new Knowledge();
        known.setName("known");
        when(repository.GetByName("known")).thenReturn(known);
        var ctx = new ExecutionContext();
        ctx.setUsersQuery("Key: known");
        assertThat(graph.identifyIntent(ctx).getKnowledge()).isSameAs(known);
        verifyNoInteractions(ai, sql);
    }

    @Test
    void disabledAutonomyUsesDefaultsAfterAnIntentMissInBothModes() {
        config.setAutonomous(false);
        var request = secondTurn();
        when(prompts.prepareMatchKnowledgePrompt("{\"known\":\"description\"}", "question", request.getVariables().toString()))
                .thenReturn("intent prompt");
        when(ai.evaluatePrompt("intent prompt")).thenReturn("NONE");
        when(sessions.createUserSession(request)).thenReturn(new UserSession());
        doAnswer(call -> {
            ExecutionContext ctx = call.getArgument(0);
            assertThat(ctx.getKnowledge().getName()).isEqualTo("default-agentic");
            return new ChatResponse("default answer");
        }).when(graph).getResponse(any(ExecutionContext.class));
        doAnswer(call -> {
            ExecutionContext ctx = call.getArgument(0);
            assertThat(ctx.getKnowledge().getName()).isEqualTo("default-agentic");
            return Flux.just("default stream");
        }).when(graph).streamResponse(any(ExecutionContext.class));

        assertThat(graph.process(request).getMessage()).isEqualTo("default answer");
        assertThat(graph.streamProcess(request).blockFirst(Duration.ofSeconds(1))).isEqualTo("default stream");
        verify(ai, times(2)).evaluatePrompt("intent prompt");
        verify(ai, never()).evaluatePrompt(startsWith("Decide whether the user message"));
        verifyNoInteractions(sql);
    }

    private Knowledge route() {
        var ctx = new ExecutionContext();
        ctx.setUsersQuery("question");
        return graph.identifyIntent(ctx).getKnowledge();
    }

    @Test
    void secondTurnUsesRegisteredKnowledgeInBothResponseModes() {
        config.setAutonomous(false);
        var request = secondTurn();
        request.setPayload("model=selected-model");
        request.addContextInfo("material", "123");
        when(prompts.prepareMatchKnowledgePrompt("{\"known\":\"description\"}", "question", request.getVariables().toString()))
                .thenReturn("intent prompt");
        var configured = new Knowledge();
        configured.setName("known");
        when(repository.GetByName("known")).thenReturn(configured);
        when(ai.evaluatePrompt("intent prompt")).thenReturn("known");
        var current = new UserSession();
        when(sessions.createUserSession(request)).thenReturn(current);
        doAnswer(call -> {
            ExecutionContext ctx = call.getArgument(0);
            assertThat(ctx.getKnowledge()).isSameAs(configured);
            assertThat(ctx.getModelName()).isEqualTo("selected-model");
            assertThat(ctx.getVariables().path("material").asText()).isEqualTo("123");
            return new ChatResponse("configured answer");
        }).when(graph).getResponse(any(ExecutionContext.class));
        doAnswer(call -> {
            ExecutionContext ctx = call.getArgument(0);
            assertThat(ctx.getKnowledge()).isSameAs(configured);
            assertThat(ctx.getModelName()).isEqualTo("selected-model");
            return Flux.just("configured stream");
        }).when(graph).streamResponse(any(ExecutionContext.class));

        assertThat(graph.process(request).getMessage()).isEqualTo("configured answer");
        request.setPayload("model=selected-model");
        assertThat(graph.streamProcess(request).blockFirst(Duration.ofSeconds(1))).isEqualTo("configured stream");
        assertThat(current.getKnowldgeName()).isEqualTo("known");
        verify(ai, never()).evaluatePrompt(startsWith("Decide whether the user message"));
        verifyNoInteractions(sql);
    }

    @Test
    void registeredMissPreservesFollowupWithoutCreatingANewExecutionSession() {
        config.setDefaultKnowledge(List.of());
        var request = secondTurn();
        when(prompts.prepareMatchKnowledgePrompt("{\"known\":\"description\"}", "question", request.getVariables().toString()))
            .thenReturn("intent prompt");
        when(ai.evaluatePrompt("intent prompt")).thenReturn("NONE");
        doReturn(new ChatResponse("follow-up answer")).when(graph).getFollowupResponse(request);
        doReturn(Flux.just("follow-up stream")).when(graph).streamFollowupResponse(request);

        assertThat(graph.process(request).getMessage()).isEqualTo("follow-up answer");
        assertThat(graph.streamProcess(request).blockFirst(Duration.ofSeconds(1))).isEqualTo("follow-up stream");
        verify(ai, times(2)).evaluatePrompt("intent prompt");
        verify(sessions, never()).createUserSession(any(ChatRequest.class));
        verifyNoInteractions(sql);
    }

    private ChatRequest secondTurn() {
        var request = new ChatRequest();
        request.setQuery("question");
        request.setSession("session");
        request.setUser("user");
        request.setConversationId("previous");
        var previous = new UserSession();
        previous.setUserId("user");
        previous.setSessionID("session");
        previous.setStatus("SUCCESS");
        previous.setBotResponse("Which material?");
        when(sessions.getRequest("previous")).thenReturn(previous);
        when(ai.evaluatePrompt(startsWith("Decide whether the user message"))).thenReturn("FOLLOWUP");
        return request;
    }
}
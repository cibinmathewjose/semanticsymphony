package org.symphonykernel.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;
import org.symphonykernel.config.SymphonyConfig;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.providers.FileContentProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

class DefaultKnowledgeRoutingTest {
    private final KnowledgeGraphBuilder graph = new KnowledgeGraphBuilder();
    private final IknowledgeBase repository = mock(IknowledgeBase.class);
    private final IAIClient ai = mock(IAIClient.class);
    private final QueryHandler sql = mock(QueryHandler.class);
    private SymphonyConfig config;

    @BeforeEach
    void prepare() {
        config = new Binder(new MapConfigurationPropertySource(Map.of(
                "symphony.ai.autonomous", "true", "symphony.ai.default-knowledge", " db, web, docs, db ")))
                .bind("symphony.ai", Bindable.of(SymphonyConfig.class)).get();
        var prompts = mock(FileContentProvider.class);
        when(repository.getActiveKnowledgeDescriptions()).thenReturn(Map.of("known", "description"));
        when(prompts.prepareMatchKnowledgePrompt("{\"known\":\"description\"}", "question", "{}"))
                .thenReturn("intent prompt");
        ReflectionTestUtils.setField(graph, "symphonyConfig", config);
        ReflectionTestUtils.setField(graph, "fileContentProvider", prompts);
        ReflectionTestUtils.setField(graph, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(graph, "knowledgeBaserepo", repository);
        ReflectionTestUtils.setField(graph, "openAI", ai);
        ReflectionTestUtils.setField(graph, "queryHandler", sql);
    }

    @Test
    void registeredMatchesWinAndUnmatchedQuestionsUseConfiguredDefaults() {
        var known = new Knowledge();
        known.setName("known");
        when(repository.GetByName("known")).thenReturn(known);
        when(ai.evaluatePrompt("intent prompt")).thenReturn("known", "missing");
        assertThat(route()).isSameAs(known);
        assertThat(route().getName()).isEqualTo("default-agentic");
        assertThat(config.getDefaultKnowledge()).containsExactly("db", "web", "docs");
        verifyNoInteractions(sql);
    }

    @Test
    void disabledAutonomyOrEmptyDefaultsKeepsExistingSqlFallback() {
        when(ai.evaluatePrompt("intent prompt")).thenReturn("NONE");
        when(sql.matchSelectQuery("question", null)).thenReturn("SELECT 1");
        config.setAutonomous(false);
        assertThat(route().getType()).isEqualTo(QueryType.SQL);
        config.setAutonomous(true);
        config.setDefaultKnowledge(List.of());
        assertThat(route().getType()).isEqualTo(QueryType.SQL);
    }

    private Knowledge route() {
        var ctx = new ExecutionContext();
        ctx.setUsersQuery("question");
        return graph.identifyIntent(ctx).getKnowledge();
    }
}
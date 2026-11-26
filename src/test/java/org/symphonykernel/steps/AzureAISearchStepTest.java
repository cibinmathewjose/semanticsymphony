package org.symphonykernel.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpResponse;
import com.azure.search.documents.SearchDocument;
import com.azure.search.documents.models.QueryType;
import com.azure.search.documents.models.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;

class AzureAISearchStepTest {
    private static final String CONFIG = """
            {"index":"product-docs","select":["id","title"],"searchFields":["title"],"top":2}
            """;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void searchesConfiguredIndexAndReturnsRankedHitsInBothModes() throws Exception {
        var step = step((index, query, options) -> {
            assertEquals("product-docs", index);
            assertEquals("replacement parts drop", query);
            assertEquals(QueryType.SIMPLE, options.getQueryType());
            assertEquals(2, options.getTop());
            assertEquals(List.of("id", "title"), options.getSelect());
            assertEquals(List.of("title"), options.getSearchFields());
            return Flux.just(hit("a", 2.0), hit("b", 1.0), hit("c", 0.5));
        });
        var ctx = context(CONFIG);
        ctx.setVariables(mapper.createObjectNode().put("query", "replacement +parts | drop*")
                .put("index", "other-index").put("top", 20).put("endpoint", "https://example.com"));
        var data = step.getResponse(ctx).getData();
        var hits = data.get(0).path("hits");
        assertEquals(2, hits.size(), data::toString);
        assertEquals("a", hits.get(0).path("document").path("id").asText());
        assertEquals(1.0, hits.get(1).path("score").asDouble());
        assertEquals(data, mapper.readTree(step.getResponseStream(ctx).blockFirst(Duration.ofSeconds(5))));
    }

    @Test
    void reportsNoMatchesAndUsesChatQuestionByDefault() {
        var step = step((index, query, options) -> {
            assertEquals("Where are the parts", query);
            return Flux.empty();
        });
        var response = step.getResponse(context(CONFIG)).getData().get(0);
        assertTrue(response.path("hits").isArray());
        assertTrue(response.path("hits").isEmpty());
    }

    @Test
    void rejectsInvalidConfigAndMatchAllBeforeSearch() {
        var step = step((index, query, options) -> { throw new AssertionError("must not send request"); });
        var invalid = context(CONFIG.replace("\"id\",\"title\"", "\"*\""));
        assertTrue(step.getResponse(invalid).getData().get(0).path("errors").asText().contains("select"));
        var matchAll = context(CONFIG);
        matchAll.setUsersQuery("*");
        assertTrue(step.getResponse(matchAll).getData().get(0).path("errors").asText().contains("searchable terms"));
    }

    @Test
    void sanitizesServiceErrorsAndValidatesEndpoint() {
        var forbidden = mock(HttpResponse.class);
        when(forbidden.getStatusCode()).thenReturn(403);
        var step = step((index, query, options) -> Flux.error(new HttpResponseException("secret", forbidden)));
        String error = step.getResponse(context(CONFIG)).getData().get(0).path("errors").asText();
        assertTrue(error.contains("denied"));
        assertFalse(error.contains("secret"));
        assertEquals("https://my-service.search.windows.net",
                AzureAISearchStep.endpoint("https://my-service.search.windows.net/"));
        assertThrows(RuntimeException.class, () -> AzureAISearchStep.endpoint("https://my-service.search.windows.net.evil.test/"));
        assertThrows(RuntimeException.class, () -> AzureAISearchStep.endpoint("https://my-service.search.windows.net/indexes"));
    }

    AzureAISearchStep step(AzureAISearchStep.SearchGateway gateway) {
        var step = new AzureAISearchStep(gateway) {
            @Override
            public void saveStepData(ExecutionContext context, JsonNode data) {
                // Session storage is outside the search contract under test.
            }
        };
        step.objectMapper = mapper;
        return step;
    }

    private static SearchResult hit(String id, double score) {
        var result = mock(SearchResult.class);
        var document = new SearchDocument();
        document.put("id", id);
        when(result.getScore()).thenReturn(score);
        when(result.getDocument(SearchDocument.class)).thenReturn(document);
        return result;
    }

    private static ExecutionContext context(String config) {
        var context = new ExecutionContext();
        var knowledge = new Knowledge();
        knowledge.setData(config);
        context.setKnowledge(knowledge);
        context.setUsersQuery("Where are the parts?");
        return context;
    }
}
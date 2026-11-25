package org.symphonykernel.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.SharePointTokenProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class SharePointStepTest {
    private static final String SITE = "https://contoso.sharepoint.com/sites/HR";
    @SuppressWarnings("null") // String.formatted never returns null.
    @NonNull private static final String HITS = """
            {"value":[{"hitsContainers":[{"hits":[
              {"summary":"Discard sibling", "resource":{"webUrl":"%s-Archive/file.docx"}},
              {"summary":"Discard traversal", "resource":{"webUrl":"%s/%%2E%%2E/Public/file.docx"}},
              {"summary":"Discard foreign", "resource":{"webUrl":"https://other.sharepoint.com/sites/HR/file.docx"}},
              {"summary":"<c0>Policy</c0> allows 20 days.<ddd/>", "resource":{"name":"Policy", "webUrl":"%s/file.docx"}},
              {"summary":"Duplicate", "resource":{"webUrl":"%s/file.docx"}}
            ]}]}]}
            """.formatted(SITE, SITE, SITE, SITE);
    private final ObjectMapper mapper = new ObjectMapper();
    private final IAIClient ai = mock(IAIClient.class);

    @Test
    void sendsScopedKeywordQueryAndReturnsValidatedCitations() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        var step = step((scope, ctx, timeout) -> {
            assertEquals(SITE, scope.toString());
            return "delegated-token";
        }, request -> {
            requests.incrementAndGet();
            assertEquals("https://graph.microsoft.com/v1.0/search/query", request.url().toString());
            assertEquals("Bearer delegated-token", request.headers().getFirst("Authorization"));
            return body(request).map(body -> {
                assertTrue(body.contains("(\\\"policy\\\" OR \\\"leave\\\" OR \\\"path\\\" OR \\\"elsewhere\\\") AND path:\\\"" + SITE));
                assertFalse(body.contains("override.sharepoint.com"));
                return reply(HttpStatus.OK, HITS);
            });
        });
        when(ai.streamExecute(any())).thenAnswer(call -> {
            org.symphonykernel.LLMRequest request = call.getArgument(0);
            assertTrue(request.getUserPrompt().contains("Policy allows 20 days"));
            assertFalse(request.getUserPrompt().contains("Discard"));
            assertFalse(request.getUserPrompt().contains("Duplicate"));
            assertFalse(request.getUserPrompt().contains("delegated-token"));
            return Flux.just("{\"answer\":\"20 days [1]\",", "\"sourceIds\":[1]}");
        });
        ExecutionContext ctx = context(SITE);
        ctx.setVariables(mapper.createObjectNode().put("query", "What is the policy for leave? OR path:elsewhere")
                .put("url", "https://override.sharepoint.com"));
        var data = step.getResponse(ctx).getData();
        assertEquals("20 days [1]", data.get(0).path("summary").asText(), data::toString);
        assertEquals(SITE + "/file.docx", data.get(0).path("sources").get(0).path("url").asText());
        assertEquals(data, mapper.readTree(step.getResponseStream(ctx).blockFirst(Duration.ofSeconds(5))));
        assertEquals(2, requests.get());
    }

    @Test
    void emptyEvidenceSkipsModelAndUsesDefaultScope() {
        var step = step((scope, ctx, timeout) -> {
            assertEquals(SITE, scope.toString());
            return "token";
        }, request -> Mono.just(reply(HttpStatus.OK, "{\"value\":[]}")));
        assertTrue(step.getResponse(context(null)).getData().get(0).path("sources").isEmpty());
        verifyNoInteractions(ai);
    }

    @Test
    void rejectsUnknownOrMissingCitations() {
        var step = step((scope, ctx, timeout) -> "token", request -> Mono.just(reply(HttpStatus.OK, HITS)));
        when(ai.streamExecute(any())).thenReturn(Flux.just("{\"answer\":\"20 days [2]\",\"sourceIds\":[1]}"));
        var result = step.getResponse(context(SITE)).getData().get(0);
        assertTrue(result.path("sources").isEmpty());
        assertTrue(result.path("summary").asText().contains("couldn’t find enough information"));
    }

    @Test
    void failsClosedForMissingConnectionAndInvalidScope() {
        var step = step(null, request -> Mono.error(new AssertionError("Graph must not be called")));
        assertTrue(step.getResponse(context(SITE)).getData().get(0).path("errors").asText().contains("delegated"));
        var invalidContext = context(SITE + "?id=/sites/Other");
        assertTrue(step.getResponse(invalidContext).getData().get(0).path("errors").asText().contains("direct HTTPS"));
        verifyNoInteractions(ai);
    }

    @Test
    void graphPermissionFailureIsSanitized() {
        var step = step((scope, ctx, timeout) -> "token", request -> Mono.just(reply(HttpStatus.FORBIDDEN, "sensitive")));
        String error = step.getResponse(context(SITE)).getData().get(0).path("errors").asText();
        assertTrue(error.contains("denied"));
        assertFalse(error.contains("sensitive"));
        verifyNoInteractions(ai);
    }

    private SharePointStep step(SharePointTokenProvider token, Function<ClientRequest, Mono<ClientResponse>> exchange) {
        var beans = new StaticListableBeanFactory();
        if (token != null) {
            beans.addBean("sharePointTokenProvider", token);
        }
        var step = new SharePointStep(beans.getBeanProvider(SharePointTokenProvider.class), ai, SITE,
                WebClient.builder().exchangeFunction(exchange::apply).build()) {
            @Override
            public void saveStepData(ExecutionContext context, com.fasterxml.jackson.databind.JsonNode data) {
                // Session storage is not part of the Graph contract.
            }
        };
        step.objectMapper = mapper;
        return step;
    }

    private static Mono<String> body(ClientRequest request) {
        var output = new MockClientHttpRequest(request.method(), request.url());
        return request.writeTo(output, ExchangeStrategies.withDefaults()).then(Mono.defer(output::getBodyAsString));
    }

    private static ClientResponse reply(@NonNull HttpStatus status, @NonNull String body) {
        return ClientResponse.create(status).header("Content-Type", "application/json").body(body).build();
    }

    private static ExecutionContext context(String url) {
        var ctx = new ExecutionContext();
        var knowledge = new Knowledge();
        knowledge.setUrl(url);
        ctx.setKnowledge(knowledge);
        ctx.setUsersQuery("How much leave is available?");
        return ctx;
    }
}
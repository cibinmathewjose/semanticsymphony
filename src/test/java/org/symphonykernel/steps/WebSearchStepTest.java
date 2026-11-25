package org.symphonykernel.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFunctions;
import org.springframework.web.reactive.function.client.WebClient;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IUserSessionBase;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import reactor.core.publisher.Mono;

class WebSearchStepTest {

    private static final String ENDPOINT = "https://resource.openai.azure.com";
    private static final String RESPONSE = """
            {"status":"completed","output":[{"type":"reasoning"},
             {"type":"web_search_call","status":"completed"},
             {"type":"message","role":"assistant","content":[
              {"type":"output_text","text":"A grounded answer.","annotations":[
               {"type":"url_citation","title":"Example","url":"https://example.com/article"},
               {"type":"url_citation","url":"https://example.com/article"},
               {"type":"url_citation","url":"javascript:alert(1)"}]}]}]}
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final MockEnvironment environment = new MockEnvironment()
            .withProperty("symphony.websearch.foundry.endpoint", ENDPOINT)
            .withProperty("symphony.websearch.foundry.deployment", "test-deployment")
            .withProperty("symphony.websearch.foundry.api-key", "test-key");
        private HttpServer server;
    private WebClient client;
        private volatile String responseBody = RESPONSE;
        private volatile int status = 200;
        private volatile CapturedRequest received;

    @BeforeEach
        void setUp() throws IOException {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/", exchange -> {
                        try (exchange) {
                                received = new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                                                exchange.getRequestHeaders().getFirst("api-key"),
                                                exchange.getRequestHeaders().getFirst("Authorization"),
                                                mapper.readTree(exchange.getRequestBody()));
                                byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
                                exchange.getResponseHeaders().set("Content-Type", "application/json");
                                exchange.sendResponseHeaders(status, body.length);
                                exchange.getResponseBody().write(body);
                        }
                });
                server.start();
                var transport = ExchangeFunctions.create(new JdkClientHttpConnector());
                client = WebClient.builder().exchangeFunction(request -> transport.exchange(ClientRequest.from(request)
                                .url(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + request.url().getRawPath()))
                                .build())).build();
    }

        @AfterEach
        void tearDown() {
                server.stop(0);
        }

    private WebSearchStep step(TokenCredential credential) {
        WebSearchStep step = new WebSearchStep(environment, credential, client);
        step.objectMapper = mapper;
        step.sessionBase = mock(IUserSessionBase.class);
        return step;
    }

    @Test
    void validatesAzureOrigin() {
        assertThat(WebSearchStep.responsesUri(ENDPOINT + "/").toString())
                .isEqualTo(ENDPOINT + "/openai/v1/responses");
        assertThatThrownBy(() -> WebSearchStep.responsesUri(ENDPOINT + ".example.org"))
                .hasMessageContaining("valid Azure resource origin");
    }

        @Test
        void sendsHostedSearchRequestAndKeepsSafeCitations() {
                WebSearchStep step = step(null);
                ExecutionContext context = new ExecutionContext().setUsersQuery("fallback");
                context.setVariables(mapper.createObjectNode().put("query", "C++ & café?"));
                Knowledge knowledge = new Knowledge();
                knowledge.setData("{\"provider\":\"bing\",\"SystemPrompt\":\"Prefer official sources.\"}");
                context.setKnowledge(knowledge);

                JsonNode data = step.getResponse(context).getData();
                assertThat(received.method()).isEqualTo("POST");
                assertThat(received.path()).isEqualTo("/openai/v1/responses");
                assertThat(received.key()).isEqualTo("test-key");
                assertThat(received.body().path("model").asText()).isEqualTo("test-deployment");
                assertThat(received.body().path("input").asText()).isEqualTo("C++ & café?");
                assertThat(received.body().path("instructions").asText()).contains("Prefer official sources.");
                assertThat(received.body().path("tools").path(0).path("type").asText()).isEqualTo("web_search");
                assertThat(received.body().path("tool_choice").asText()).isEqualTo("required");
                assertThat(received.body().path("store").asBoolean(true)).isFalse();
                assertThat(data.path(0).path("summary").asText()).isEqualTo("A grounded answer.");
                assertThat(data.path(0).path("sources").size()).isEqualTo(1);
                assertThat(data.at("/0/sources/0/url").asText()).isEqualTo("https://example.com/article");
                verify(step.sessionBase).saveRequestDetails(context.getRequestId(), context.getName(), data.toString());
        }

        @Test
        void streamsCompleteCitedAnswerAndUsesEntraCredential() throws IOException {
                TokenCredential credential = context -> {
                        assertThat(context.getScopes()).containsExactly("https://ai.azure.com/.default");
                        return Mono.just(new AccessToken("test-token", OffsetDateTime.now().plusHours(1)));
                };
                var stream = step(credential).getResponseStream(new ExecutionContext().setUsersQuery("test"));
                assertThat(received).isNull();
                var chunks = stream.collectList().block(Duration.ofSeconds(10));
                assertThat(chunks).hasSize(1);
                assertThat(mapper.readTree(chunks.get(0)).at("/0/sources/0/title").asText()).isEqualTo("Example");
                assertThat(received.authorization()).isEqualTo("Bearer test-token");
                assertThat(received.key()).isNull();
        }

        @Test
        void doesNotExposeProviderErrorBody() {
                status = 429;
                responseBody = "{\"error\":\"sensitive upstream detail\"}";
                JsonNode data = step(null).getResponse(new ExecutionContext().setUsersQuery("test")).getData();
                assertThat(data.at("/0/errors").asText()).isEqualTo("Foundry Web Search returned HTTP 429.");
        }

        @Test
        void rejectsAnswerWithoutSuccessfulSearch() {
                responseBody = RESPONSE.replace("web_search_call", "other_call");
                JsonNode data = step(null).getResponse(new ExecutionContext().setUsersQuery("test")).getData();
                assertThat(data.at("/0/errors").asText()).contains("did not return a cited answer");
        }

        @Test
        void missingConfigurationDoesNotCallProvider() {
                environment.setProperty("symphony.websearch.foundry.api-key", "");
                JsonNode data = step(null).getResponse(new ExecutionContext().setUsersQuery("test")).getData();
                assertThat(data.at("/0/errors").asText()).contains("symphony.websearch.foundry.api-key");
                assertThat(received).isNull();
        }

        private record CapturedRequest(String method, String path, String key, String authorization, JsonNode body) {
        }
}
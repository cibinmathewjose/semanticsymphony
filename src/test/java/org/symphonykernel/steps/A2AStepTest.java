package org.symphonykernel.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.A2ATokenProvider;
import org.symphonykernel.core.IUserSessionBase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class A2AStepTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "0.3"})
    void sendsOneMessageThenPollsTheReturnedTask(String version) throws Exception {
        boolean modern = "1.0".equals(version);
        String sendMethod = modern ? "SendMessage" : "message/send";
        String pollMethod = modern ? "GetTask" : "tasks/get";
        var requests = new LinkedBlockingQueue<JsonNode>();
        var headers = new LinkedBlockingQueue<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/a2a", exchange -> {
            try (exchange) {
                JsonNode request = mapper.readTree(exchange.getRequestBody());
                requests.add(request);
                headers.add(exchange.getRequestHeaders().getFirst("A2A-Version") + ":"
                        + exchange.getRequestHeaders().getFirst("Authorization"));
                String body = sendMethod.equals(request.path("method").asText())
                    ? (modern ? "{\"task\":{\"id\":\"t1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}"
                        : "{\"kind\":\"task\",\"id\":\"t1\",\"status\":{\"state\":\"working\"}}")
                        : "{\"id\":\"t1\",\"contextId\":\"c1\",\"status\":{\"state\":\"TASK_STATE_COMPLETED\"},"
                                + "\"artifacts\":[{\"parts\":[{\"text\":\"Answer\"}]}]}";
                var envelope = mapper.createObjectNode().put("jsonrpc", "2.0").put("id", request.path("id").asText());
                envelope.set("result", mapper.readTree(body));
                byte[] bytes = mapper.writeValueAsBytes(envelope);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            var endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/a2a");
            WebClient http = WebClient.builder().filter((request, next) ->
                    next.exchange(ClientRequest.from(request).url(endpoint).build())).build();
            var beans = new StaticListableBeanFactory();
            beans.addBean("tokens", (A2ATokenProvider) (target, uri, context, timeout) -> "test-token");
            var env = environment().withProperty("symphony.a2a.targets.test.version", version);
            var step = new A2AStep(env, beans.getBeanProvider(A2ATokenProvider.class), mapper, http);
            step.sessionBase = mock(IUserSessionBase.class);
            var result = step.getResponse(context());
            assertThat(result.getMessage()).isEqualTo("Answer");
            assertThat(result.getData().at("/0/contextId").asText()).isEqualTo("c1");
            assertThat(requests).hasSize(2);
            var send = requests.remove();
            assertThat(send.path("method").asText()).isEqualTo(sendMethod);
            assertThat(send.at("/params/message/role").asText()).isEqualTo(modern ? "ROLE_USER" : "user");
            assertThat(send.at("/params/message/parts/0/text").asText()).isEqualTo("Question");
            var poll = requests.remove();
            assertThat(poll.path("method").asText()).isEqualTo(pollMethod);
            assertThat(poll.at("/params/id").asText()).isEqualTo("t1");
            assertThat(headers).containsExactly(version + ":Bearer test-token", version + ":Bearer test-token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refusesToSendWithoutHostCredentials() {
        var beans = new StaticListableBeanFactory();
        var step = new A2AStep(environment(), beans.getBeanProvider(A2ATokenProvider.class), mapper,
                WebClient.builder().exchangeFunction(request -> { throw new AssertionError("unexpected request"); }).build());
        assertThatThrownBy(() -> step.getResponse(context())).hasMessageContaining("A2ATokenProvider");
    }

    private static MockEnvironment environment() {
        return new MockEnvironment().withProperty("symphony.a2a.targets.test.url", "https://agent.example.com/a2a");
    }

    private static ExecutionContext context() {
        var context = new ExecutionContext();
        var knowledge = new Knowledge();
        knowledge.setData("{\"target\":\"test\"}");
        context.setKnowledge(knowledge);
        context.setUsersQuery("Question");
        return context;
    }
}
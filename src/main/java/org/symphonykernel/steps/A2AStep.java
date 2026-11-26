package org.symphonykernel.steps;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.core.A2ATokenProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

/** Executes one outbound text turn against a configured A2A JSON-RPC target. */
@Service
public class A2AStep extends BaseStep {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final long POLL_MILLIS = 500;

    private final Environment environment;
    private final ObjectProvider<A2ATokenProvider> tokens;
    private final ObjectMapper mapper;
    private final WebClient http;

    /** Creates the step with host-owned configuration and credentials. */
    @Autowired
    public A2AStep(Environment environment, ObjectProvider<A2ATokenProvider> tokens, ObjectMapper mapper) {
        this(environment, tokens, mapper, WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false).disableRetry(true)))
                .codecs(config -> config.defaultCodecs().maxInMemorySize(256 * 1024)).build());
    }

    A2AStep(Environment environment, ObjectProvider<A2ATokenProvider> tokens, ObjectMapper mapper, WebClient http) {
        this.environment = environment;
        this.tokens = tokens;
        this.mapper = mapper;
        this.http = http;
    }

    @Override
    public ChatResponse getResponse(ExecutionContext context) {
        if (context == null || context.getKnowledge() == null || context.getKnowledge().getData() == null) {
            throw new IllegalArgumentException("A2A knowledge configuration is required");
        }
        JsonNode config;
        try {
            config = mapper.readTree(context.getKnowledge().getData());
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("Invalid A2A knowledge configuration");
        }
        String target = config.path("target").asText();
        if (!target.matches("[a-z0-9][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("A2A requires a configured target alias");
        }
        String prefix = "symphony.a2a.targets." + target;
        URI endpoint = URI.create(environment.getRequiredProperty(prefix + ".url"));
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("A2A target requires an HTTPS URL without user info or a fragment");
        }
        String version = environment.getProperty(prefix + ".version", "1.0");
        if (!"1.0".equals(version) && !"0.3".equals(version)) {
            throw new IllegalArgumentException("A2A JSON-RPC supports versions 1.0 and 0.3");
        }
        JsonNode variables = context.getVariables();
        String text = variables != null && variables.path("message").isTextual()
                ? variables.path("message").textValue() : context.getUsersQuery();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A2A requires a text message");
        }
        var provider = tokens.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("A2A requires a host A2ATokenProvider");
        }
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        String token = provider.accessToken(target, endpoint, context, remaining(deadline));
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("No A2A access token for this caller and target");
        }
        JsonNode result = sendTurn(endpoint, version, token, text, deadline);
        ChatResponse response = new ChatResponse();
        response.setStatusCode("SUCCESS");
        response.setData(mapper.createArrayNode().add(result));
        response.setMessage(result.path("text").asText());
        saveStepData(context, response.getData());
        return response;
    }

    @Override
    public Flux<String> getResponseStream(ExecutionContext context) {
        return Mono.fromCallable(() -> getResponse(context)).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(response -> Flux.just(streamContent(context, "a2a", response.getMessage(), 1),
                        streamComplete(context, "a2a", "A2A request completed", 2)));
    }

    private JsonNode sendTurn(URI endpoint, String version, String token, String text, long deadline) {
        boolean modern = "1.0".equals(version);
        ObjectNode params = mapper.createObjectNode();
        ObjectNode message = params.putObject("message").put("messageId", UUID.randomUUID().toString())
                .put("role", modern ? "ROLE_USER" : "user");
        ObjectNode part = message.putArray("parts").addObject().put("text", text);
        if (!modern) {
            message.put("kind", "message");
            part.put("kind", "text");
        }
        JsonNode result = rpc(endpoint, version, token, modern ? "SendMessage" : "message/send", params, deadline);
        JsonNode reply = modern ? result.path("message") : result;
        if (reply.path("parts").isArray()) {
            return output(textParts(reply.path("parts")), reply, null);
        }
        JsonNode task = modern ? result.path("task") : result;
        String id = task.path("id").asText();
        if (id.isBlank()) {
            throw new IllegalStateException("A2A returned neither a message nor a task");
        }
        while (true) {
            String state = task.path("status").path("state").asText();
            switch (state) {
                case "completed", "TASK_STATE_COMPLETED" -> {
                    StringBuilder answer = new StringBuilder();
                    task.path("artifacts").forEach(artifact -> answer.append(textParts(artifact.path("parts"))));
                    if (answer.isEmpty()) {
                        answer.append(textParts(task.path("status").path("message").path("parts")));
                    }
                    return output(answer.toString(), task, id);
                }
                case "submitted", "working", "TASK_STATE_SUBMITTED", "TASK_STATE_WORKING" -> {
                    try {
                        Thread.sleep(Math.min(POLL_MILLIS, remaining(deadline).toMillis()));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("A2A polling interrupted");
                    }
                    task = rpc(endpoint, version, token, modern ? "GetTask" : "tasks/get",
                            mapper.createObjectNode().put("id", id), deadline);
                    if (!id.equals(task.path("id").asText())) {
                        throw new IllegalStateException("A2A returned a different task ID");
                    }
                }
                case "input-required", "TASK_STATE_INPUT_REQUIRED", "auth-required", "TASK_STATE_AUTH_REQUIRED" ->
                    throw new IllegalStateException("A2A task requires additional input or authorization");
                default -> throw new IllegalStateException("A2A task did not complete successfully");
            }
        }
    }

    private JsonNode rpc(URI endpoint, String version, String token, String method, ObjectNode params, long deadline) {
        String id = UUID.randomUUID().toString();
        ObjectNode request = mapper.createObjectNode().put("jsonrpc", "2.0").put("id", id).put("method", method);
        request.set("params", params);
        Duration budget = remaining(deadline);
        JsonNode envelope = http.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON).header("A2A-Version", version)
                .headers(headers -> headers.setBearerAuth(token)).bodyValue(request)
                .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
                        ? response.bodyToMono(JsonNode.class)
                        : Mono.error(new IllegalStateException("A2A HTTP status " + response.statusCode().value())))
                .timeout(budget).block();
        if (envelope == null || !id.equals(envelope.path("id").asText())
                || !"2.0".equals(envelope.path("jsonrpc").asText()) || envelope.has("error")
                || !envelope.path("result").isObject()) {
            throw new IllegalStateException("A2A JSON-RPC request failed or returned an invalid response");
        }
        return envelope.path("result");
    }

    private JsonNode output(String text, JsonNode source, String taskId) {
        if (text.isBlank()) {
            throw new IllegalStateException("A2A response contained no text");
        }
        ObjectNode result = mapper.createObjectNode().put("text", text);
        if (taskId != null) {
            result.put("taskId", taskId);
        }
        if (source.path("contextId").isTextual()) {
            result.put("contextId", source.path("contextId").asText());
        }
        return result;
    }

    private static String textParts(JsonNode parts) {
        StringBuilder result = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.path("text").isTextual()) {
                result.append(part.path("text").textValue());
            }
        }
        return result.toString();
    }

    private static Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) {
            throw new IllegalStateException("A2A turn exceeded its deadline");
        }
        return Duration.ofNanos(nanos);
    }
}
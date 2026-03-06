/*
 * The MIT License
 *
 * Copyright 2025 cjose.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.symphonykernel.steps;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Flux;

/**
 * HumanInLoopStep pauses workflow execution to collect user input before continuing.
 *
 * <p><strong>Knowledge entry configuration</strong></p>
 * <p>The {@code data} field of the Knowledge entry should contain a JSON object
 * with the following fields:</p>
 * <pre>{@code
 * {
 *   "question": "Do you approve the order for {{orderSummary}}?",
 *   "options": ["Approve", "Reject", "Modify"],
 *   "timeoutSeconds": 300,
 *   "defaultResponse": "Timeout - request auto-rejected"
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code question} (required) – The prompt displayed to the user. Supports
 *       {@code &#123;&#123;key&#125;&#125;} placeholders resolved from context.</li>
 *   <li>{@code options} (optional) – A JSON array of allowed response choices.</li>
 *   <li>{@code timeoutSeconds} (optional, default 300) – Maximum seconds to wait
 *       for user input before applying the default response.</li>
 *   <li>{@code defaultResponse} (optional) – The value returned when the user
 *       does not respond within the timeout period.</li>
 * </ul>
 *
 * <p><strong>Usage in a Symphony flow</strong></p>
 * <p>When this step executes inside a Symphony flow, it blocks the flow thread
 * (which runs asynchronously) until the user submits input via
 * {@link #submitInput(String, String)} or the timeout expires. The user's answer
 * is stored in the resolved values map under the flow item's key for use by
 * subsequent steps.</p>
 *
 * <p><strong>Submitting user input</strong></p>
 * <p>External code (e.g., a REST controller) calls
 * {@link #submitInput(String, String)} with the request ID and the user's answer
 * to unblock the waiting step.</p>
 */
@Service("HumanInLoopStep")
public class HumanInLoopStep extends BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(HumanInLoopStep.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
    private static final String AWAITING_INPUT = "AWAITING_INPUT";

    private static final ConcurrentHashMap<String, CompletableFuture<String>> pendingInputs = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> pendingQuestions = new ConcurrentHashMap<>();

    @Autowired
    private TemplateResolver templateResolver;

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {
        ArrayNode jsonArray = objectMapper.createArrayNode();
        Knowledge kb = ctx.getKnowledge();
        String requestId = ctx.getRequestId();

        try {
            JsonNode config = getConfig(kb);
            Map<String, JsonNode> resolvedValues = ctx.getResolvedValues();

            String question = resolveQuestion(config, resolvedValues);
            long timeoutSeconds = getTimeoutSeconds(config);
            String defaultResponse = getDefaultResponse(config);

            logger.info("HumanInLoopStep [{}]: presenting question to user", requestId);

            CompletableFuture<String> future = new CompletableFuture<>();
            pendingInputs.put(requestId, future);
            pendingQuestions.put(requestId, question);

            // Build and save the question details so the caller can retrieve them
            ObjectNode questionNode = buildQuestionNode(question, config, requestId);
            jsonArray.add(questionNode);

            String userInput;
            try {
                userInput = future.get(timeoutSeconds, TimeUnit.SECONDS);
                logger.info("HumanInLoopStep [{}]: received user input", requestId);
            } catch (TimeoutException e) {
                logger.warn("HumanInLoopStep [{}]: timed out after {} seconds", requestId, timeoutSeconds);
                userInput = defaultResponse;
            }

            // Build the result with the user's answer
            ObjectNode resultNode = objectMapper.createObjectNode();
            resultNode.put("question", question);
            resultNode.put("userInput", userInput != null ? userInput : "");
            resultNode.put("timedOut", userInput != null && userInput.equals(defaultResponse));
            jsonArray.removeAll();
            jsonArray.add(resultNode);

            saveStepData(ctx, jsonArray);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("HumanInLoopStep [{}]: interrupted", requestId);
            addError(jsonArray, "Step was interrupted");
        } catch (Exception e) {
            logger.error("HumanInLoopStep [{}]: error - {}", requestId, e.getMessage());
            addError(jsonArray, e.getMessage());
        } finally {
            pendingInputs.remove(requestId);
            pendingQuestions.remove(requestId);
        }

        ChatResponse response = new ChatResponse();
        response.setData(jsonArray);
        return response;
    }

    @Override
    public Flux<String> getResponseStream(ExecutionContext ctx) {
        Knowledge kb = ctx.getKnowledge();
        String requestId = ctx.getRequestId();

        try {
            JsonNode config = getConfig(kb);
            Map<String, JsonNode> resolvedValues = ctx.getResolvedValues();
            String question = resolveQuestion(config, resolvedValues);
            long timeoutSeconds = getTimeoutSeconds(config);
            String defaultResponse = getDefaultResponse(config);

            CompletableFuture<String> future = new CompletableFuture<>();
            pendingInputs.put(requestId, future);
            pendingQuestions.put(requestId, question);

            ObjectNode questionNode = buildQuestionNode(question, config, requestId);

            return Flux.just(questionNode.toString())
                .concatWith(Flux.defer(() -> {
                    try {
                        String userInput = future.get(timeoutSeconds, TimeUnit.SECONDS);
                        logger.info("HumanInLoopStep [{}]: received user input (stream)", requestId);
                        ObjectNode resultNode = objectMapper.createObjectNode();
                        resultNode.put("userInput", userInput != null ? userInput : "");
                        ArrayNode arr = objectMapper.createArrayNode();
                        arr.add(resultNode);
                        saveStepData(ctx, arr);
                        return Flux.just(userInput != null ? userInput : "");
                    } catch (TimeoutException e) {
                        logger.warn("HumanInLoopStep [{}]: timed out (stream)", requestId);
                        return Flux.just(defaultResponse != null ? defaultResponse : "No response received");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Flux.just("Step was interrupted");
                    } catch (Exception e) {
                        return Flux.just("Error: " + e.getMessage());
                    }
                }))
                .doFinally(signal -> {
                    pendingInputs.remove(requestId);
                    pendingQuestions.remove(requestId);
                });
        } catch (Exception e) {
            return Flux.just("Error: " + e.getMessage());
        }
    }

    /**
     * Submits user input for a pending human-in-the-loop request.
     *
     * @param requestId the request ID of the pending step
     * @param userInput the user's response
     * @return {@code true} if the input was accepted, {@code false} if no pending request was found
     */
    public static boolean submitInput(String requestId, String userInput) {
        CompletableFuture<String> future = pendingInputs.get(requestId);
        if (future != null) {
            return future.complete(userInput);
        }
        return false;
    }

    /**
     * Returns the pending question for a given request ID, or {@code null} if none.
     *
     * @param requestId the request ID
     * @return the question text, or {@code null}
     */
    public static String getPendingQuestion(String requestId) {
        return pendingQuestions.get(requestId);
    }

    /**
     * Checks whether a request is currently waiting for user input.
     *
     * @param requestId the request ID
     * @return {@code true} if the request is awaiting input
     */
    public static boolean isAwaitingInput(String requestId) {
        return pendingInputs.containsKey(requestId);
    }

    private String resolveQuestion(JsonNode config, Map<String, JsonNode> resolvedValues) {
        String question = config.has("question") ? config.get("question").asText() : "Please provide your input";
        if (TemplateResolver.hasPlaceholders(question)) {
            question = templateResolver.resolvePlaceholders(question, resolvedValues);
        }
        return question;
    }

    private long getTimeoutSeconds(JsonNode config) {
        return config.has("timeoutSeconds") ? config.get("timeoutSeconds").asLong(DEFAULT_TIMEOUT_SECONDS) : DEFAULT_TIMEOUT_SECONDS;
    }

    private String getDefaultResponse(JsonNode config) {
        return config.has("defaultResponse") ? config.get("defaultResponse").asText(null) : null;
    }

    private ObjectNode buildQuestionNode(String question, JsonNode config, String requestId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", AWAITING_INPUT);
        node.put("question", question);
        node.put("requestId", requestId);
        if (config.has("options") && config.get("options").isArray()) {
            node.set("options", config.get("options"));
        }
        return node;
    }

    private JsonNode getConfig(Knowledge kb) {
        if (kb != null && kb.getData() != null && !kb.getData().isEmpty()) {
            return getParamNode(kb.getData());
        }
        return objectMapper.createObjectNode();
    }

    private void addError(ArrayNode jsonArray, String message) {
        ObjectNode err = objectMapper.createObjectNode();
        err.put("error", message);
        jsonArray.add(err);
    }
}

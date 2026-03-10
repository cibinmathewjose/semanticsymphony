package org.symphonykernel.agentic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.ai.KnowledgeExecuterFactory;
import org.symphonykernel.ai.KnowledgeGraphBuilder;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.mcp.MCPClientService;
import org.symphonykernel.mcp.MCPToolDescriptor;
import org.symphonykernel.mcp.MCPToolRegistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The AgenticPlanner implements a ReAct-style (Reason + Act) loop where the LLM:
 * <ol>
 *   <li>Observes the user query and available tools</li>
 *   <li>Plans which tools to call (generates an AgentPlan)</li>
 *   <li>Executes the planned actions</li>
 *   <li>Observes results and decides whether to plan more actions or return a final answer</li>
 * </ol>
 * 
 * Available tools include all Symphony knowledge steps and any external MCP tools.
 */
@Component
public class AgenticPlanner {

    private static final Logger logger = LoggerFactory.getLogger(AgenticPlanner.class);
    private static final int DEFAULT_MAX_ITERATIONS = 10;
    private static final int MAX_CONCURRENT_LLM_CALLS = 5;

    private final Semaphore llmSemaphore = new Semaphore(MAX_CONCURRENT_LLM_CALLS);

    @Value("${symphony.agentic.max-iterations:10}")
    private int maxIterations;

    @Autowired
    private IAIClient aiClient;

    @Autowired
    private MCPToolRegistry toolRegistry;

    @Autowired(required = false)
    private MCPClientService mcpClientService;

    @Autowired
    private IknowledgeBase knowledgeBase;

   
    @Autowired
    private KnowledgeExecuterFactory knowledgeExecuterFactory;
    
    @Autowired
    private KnowledgeGraphBuilder knowledgeGraphBuilder;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Executes the agentic loop synchronously and returns the final result.
     *
     * @param ctx the execution context containing the user query and resolved values
     * @return the chat response with the final answer or partial results
     */
    public ChatResponse execute(ExecutionContext ctx) {
        String userQuery = ctx.getUsersQuery();
        ConcurrentHashMap<String, JsonNode> resolvedValues = new ConcurrentHashMap<>(ctx.getResolvedValues());
        List<Map<String, String>> conversationHistory = new ArrayList<>();

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            logger.info("Agentic iteration {}/{}", iteration + 1, maxIterations);

            String planPrompt = buildPlanningPrompt(userQuery, resolvedValues, conversationHistory);
            String planResponse;
            try {
                llmSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ChatResponse response = new ChatResponse();
                response.setMessage("Agent interrupted while waiting for LLM access");
                return response;
            }
            try {
                planResponse = aiClient.evaluatePrompt(planPrompt);
            } finally {
                llmSemaphore.release();
            }

            AgentPlan plan = parsePlan(planResponse);
            if (plan == null) {
                logger.warn("Failed to parse agent plan, returning raw response");
                ChatResponse response = new ChatResponse();
                response.setMessage(planResponse);
                return response;
            }

            if (plan.isComplete() && plan.getMessage() != null) {
                logger.info("Agent completed task after {} iterations", iteration + 1);
                ChatResponse response = new ChatResponse();
                if (plan.getData() != null) {
                    response.setData(plan.getData());
                }
                response.setMessage("Generating output:" + plan.getMessage());
                return response;
            }

            if (plan.getActions() == null || plan.getActions().isEmpty()) {
                logger.warn("Agent returned no actions and no final answer");
                ChatResponse response = new ChatResponse();
                response.setMessage(plan.getReasoning() != null ? plan.getReasoning() : "Unable to determine next action");
                return response;
            }

            for (AgentAction action : plan.getActions()) {
                String result = executeAction(action, ctx, resolvedValues);
                conversationHistory.add(Map.of(
                    "tool", action.getTool(),
                    "reasoning", action.getReasoning() != null ? action.getReasoning() : "",
                    "result", result
                ));
                try {
                    JsonNode resultNode = objectMapper.readTree(result);
                    resolvedValues.put(action.getTool().toLowerCase(), resultNode);
                } catch (Exception e) {
                    resolvedValues.put(action.getTool().toLowerCase(), objectMapper.valueToTree(result));
                }
            }
        }

        logger.warn("Agent reached max iterations ({})", maxIterations);
        ChatResponse response = new ChatResponse();
        response.setMessage("Agent reached maximum iterations. Partial results collected.");
        if (!resolvedValues.isEmpty()) {
            response.setData(objectMapper.createArrayNode().add(objectMapper.valueToTree(resolvedValues)));
        }
        return response;
    }

    /**
     * Executes the agentic loop as a reactive stream, emitting progress updates.
     *
     * @param ctx the execution context containing the user query and resolved values
     * @return a Flux emitting progress messages and the final answer
     */
    public Flux<String> executeStream(ExecutionContext ctx) {
        return Mono.fromCallable(() -> {
            String userQuery = ctx.getUsersQuery();
            ConcurrentHashMap<String, JsonNode> resolvedValues = new ConcurrentHashMap<>(ctx.getResolvedValues());
            List<Map<String, String>> conversationHistory = new ArrayList<>();
            return new AgenticLoopState(userQuery, resolvedValues, conversationHistory);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(state -> runAgenticLoop(state, ctx, 0));
    }

    /**
     * Internal state holder for the agentic loop.
     */
    private static class AgenticLoopState {
        final String userQuery;
        final ConcurrentHashMap<String, JsonNode> resolvedValues;
        final List<Map<String, String>> conversationHistory;

        AgenticLoopState(String userQuery, ConcurrentHashMap<String, JsonNode> resolvedValues,
                         List<Map<String, String>> conversationHistory) {
            this.userQuery = userQuery;
            this.resolvedValues = resolvedValues;
            this.conversationHistory = conversationHistory;
        }
    }

    /**
     * Runs one iteration of the agentic loop, emitting progress messages, then
     * either recurses for the next iteration or streams the final answer.
     */
    private Flux<String> runAgenticLoop(AgenticLoopState state, ExecutionContext ctx, int iteration) {
        if (iteration >= maxIterations) {
            return Flux.just("Agent reached maximum iterations.\n");
        }

        return Mono.fromCallable(() -> {
            logger.info("Agentic stream iteration {}/{}", iteration + 1, maxIterations);
            String planPrompt = buildPlanningPrompt(state.userQuery, state.resolvedValues, state.conversationHistory);
            llmSemaphore.acquire();
            try {
                return aiClient.evaluatePrompt(planPrompt);
            } finally {
                llmSemaphore.release();
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(planResponse -> {
            AgentPlan plan = parsePlan(planResponse);

            if (plan == null) {
                return Flux.just("Generating output:").concatWith(Flux.just(planResponse));
            }

            if (plan.isComplete() && plan.getMessage() != null) {
                return Flux.just("Generating output:").concatWith(Flux.just(plan.getMessage()));
            }

            if (plan.getActions() == null || plan.getActions().isEmpty()) {
                String msg = plan.getReasoning() != null ? plan.getReasoning() : "No further actions.";
                return Flux.just("Generating output:").concatWith(Flux.just(msg));
            }

            // Emit reasoning
            Flux<String> progress = Flux.empty();
            if (plan.getReasoning() != null) {
                progress = Flux.just("Thinking: " + plan.getReasoning() + "\n");
            }

            // Execute actions sequentially, emitting status for each
            Flux<String> actionExecution = Flux.fromIterable(plan.getActions())
                .concatMap(action -> Mono.fromCallable(() -> {
                    String status = "Executing: " + action.getTool()
                        + (action.getReasoning() != null ? " - " + action.getReasoning() : "") + "\n";
                    String result = executeAction(action, ctx, state.resolvedValues);
                    state.conversationHistory.add(Map.of(
                        "tool", action.getTool(),
                        "reasoning", action.getReasoning() != null ? action.getReasoning() : "",
                        "result", result
                    ));
                    try {
                        JsonNode resultNode = objectMapper.readTree(result);
                        state.resolvedValues.put(action.getTool().toLowerCase(), resultNode);
                    } catch (Exception e) {
                        state.resolvedValues.put(action.getTool().toLowerCase(), objectMapper.valueToTree(result));
                    }
                    return status + "Completed: " + action.getTool() + "\n";
                }).subscribeOn(Schedulers.boundedElastic()));

            // After all actions, recurse into next iteration
            return progress
                .concatWith(actionExecution)
                .concatWith(Flux.defer(() -> runAgenticLoop(state, ctx, iteration + 1)));
        })
        .onErrorResume(e -> {
            logger.error("Error in agentic stream iteration {}: {}", iteration + 1, e.getMessage(), e);
            return Flux.just("Error: " + e.getMessage() + "\n");
        });
    }

    private String buildPlanningPrompt(String userQuery, Map<String, JsonNode> resolvedValues, 
                                        List<Map<String, String>> conversationHistory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an intelligent agent that plans and executes tasks using available tools.\n\n");
        prompt.append("## Available Tools\n");

        for (MCPToolDescriptor tool : toolRegistry.listTools()) {
            prompt.append("- **").append(tool.getName()).append("**: ").append(tool.getDescription());
            if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
                try {
                    prompt.append("\n  Input schema: ").append(objectMapper.writeValueAsString(tool.getInputSchema()));
                } catch (Exception ignored) {
                }
            }
            prompt.append("\n");
        }

        prompt.append("\n## User Request\n").append(userQuery).append("\n");

        if (!conversationHistory.isEmpty()) {
            prompt.append("\n## Previous Actions & Results\n");
            for (Map<String, String> entry : conversationHistory) {
                prompt.append("- Tool: ").append(entry.get("tool"))
                    .append(", Reasoning: ").append(entry.get("reasoning"))
                    .append(", Result: ").append(truncateResult(entry.get("result")))
                    .append("\n");
            }
        }

        if (!resolvedValues.isEmpty()) {
            prompt.append("\n## Collected Data\n");
            for (Map.Entry<String, JsonNode> entry : resolvedValues.entrySet()) {
                String value = entry.getValue().toString();
                prompt.append("- ").append(entry.getKey()).append(": ")
                    .append(truncateResult(value)).append("\n");
            }
        }

        prompt.append("\n## Instructions\n");
        prompt.append("Analyze the user request and the available tools. Respond with a JSON object:\n");
        prompt.append("- If you need to call tools, provide:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"isComplete\": false,\n");
        prompt.append("  \"reasoning\": \"Why I'm taking these actions\",\n");
        prompt.append("  \"actions\": [\n");
        prompt.append("    {\"tool\": \"tool_name\", \"arguments\": {\"param\": \"value\"}, \"reasoning\": \"why this tool\"}\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("```\n");
        prompt.append("- If you have enough data to answer, provide:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"isComplete\": true,\n");
        prompt.append("  \"reasoning\": \"Summary of what I did\",\n");
        prompt.append("  \"finalAnswer\": \"The complete answer to the user's request\"\n");
        prompt.append("}\n");
        prompt.append("```\n");
        prompt.append("Respond with ONLY the JSON object, no other text.\n");

        return prompt.toString();
    }

    private String executeAction(AgentAction action, ExecutionContext ctx, 
                                  ConcurrentHashMap<String, JsonNode> resolvedValues) {
        String toolName = action.getTool();
        logger.info("Executing agent action: {}", toolName);

        try {
            // Check if it's an external MCP tool (contains '/')
            if (toolName.contains("/") && mcpClientService != null) {
                return mcpClientService.callTool(toolName, 
                    action.getArguments() != null ? action.getArguments() : new HashMap<>());
            }

            // Try as a Symphony knowledge step
            Knowledge kb = knowledgeBase.GetByName(toolName);
            if (kb != null) {
                IStep step = knowledgeExecuterFactory.getExecuter(kb);
                if (step != null) {
                    ExecutionContext stepCtx = new ExecutionContext(ctx);
                    stepCtx.setKnowledge(kb);
                    stepCtx.setName(toolName);
                    if (action.getArguments() != null && !action.getArguments().isEmpty()) {
                        stepCtx.setVariables(objectMapper.valueToTree(action.getArguments()));
                    } else {
                        stepCtx.setVariables(ctx.getVariables());
                    }
                    stepCtx.setConvert(true);

                    ChatResponse response = step.getResponse(stepCtx);
                    if (response.getData() != null) {
                        return objectMapper.writeValueAsString(response.getData());
                    } else if (response.getMessage() != null) {
                        return response.getMessage();
                    }
                    return "{}";
                }
            }

            logger.warn("Tool not found: {}", toolName);
            return "Error: Tool '" + toolName + "' not found in registry";
        } catch (Exception e) {
            logger.error("Error executing action '{}': {}", toolName, e.getMessage(), e);
            return "Error executing " + toolName + ": " + e.getMessage();
        }
    }

    private AgentPlan parsePlan(String response) {
        try {
            String json = response.trim();
            if (json.startsWith("```json")) {
                json = json.substring(7);
            }
            if (json.startsWith("```")) {
                json = json.substring(3);
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }
            json = json.trim();
            AgentPlan plan = objectMapper.readValue(json, AgentPlan.class);
            processFinalAnswer(plan);
            return plan;
        } catch (Exception e) {
            logger.warn("Failed to parse agent plan: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Processes the raw {@code finalAnswer} JsonNode into a text message and optional
     * structured data on the plan. If the finalAnswer is a JSON array or object it is
     * converted to a markdown table for display and also stored as ArrayNode data.
     * Plain strings are kept as-is.
     */
    private void processFinalAnswer(AgentPlan plan) {
        JsonNode answer = plan.getFinalAnswer();
        if (answer == null || answer.isNull()) {
            return;
        }
        if (answer.isArray()) {
            ArrayNode arrayNode = (ArrayNode) answer;
            plan.setData(arrayNode);
            plan.setMessage(convertToMarkdownTable(answer.toString()));
        } else if (answer.isObject()) {
            ArrayNode wrapped = objectMapper.createArrayNode();
            wrapped.add(answer);
            plan.setData(wrapped);
            plan.setMessage(convertToMarkdownTable(answer.toString()));
        } else {
            // Plain text value
            plan.setMessage(answer.asText());
        }
    }

    /**
     * Converts the final answer to a markdown table when the content is JSON.
     * If the answer is a JSON array of objects, it produces a multi-column table.
     * If it is a single JSON object, it produces a key-value table.
     * Otherwise, the original text is returned unchanged.
     */
    private String convertToMarkdownTable(String answer) {
        if (answer == null || answer.isBlank()) return answer;
        try {
            JsonNode node = objectMapper.readTree(answer.trim());
            if (node.isArray() && !node.isEmpty() && node.get(0).isObject()) {
                // Collect all unique headers across all elements
                List<String> headers = new ArrayList<>();
                for (JsonNode element : node) {
                    element.fieldNames().forEachRemaining(f -> {
                        if (!headers.contains(f)) headers.add(f);
                    });
                }
                StringBuilder table = new StringBuilder();
                table.append("| ").append(String.join(" | ", headers)).append(" |\n");
                table.append("| ").append(headers.stream().map(h -> "---").collect(Collectors.joining(" | "))).append(" |\n");
                for (JsonNode element : node) {
                    table.append("| ");
                    for (int i = 0; i < headers.size(); i++) {
                        JsonNode val = element.get(headers.get(i));
                        table.append(val != null && !val.isNull() ? val.asText() : "");
                        if (i < headers.size() - 1) table.append(" | ");
                    }
                    table.append(" |\n");
                }
                return table.toString();
            } else if (node.isObject() && !node.isEmpty()) {
                StringBuilder table = new StringBuilder();
                table.append("| Key | Value |\n");
                table.append("| --- | --- |\n");
                node.fields().forEachRemaining(entry ->
                    table.append("| ").append(entry.getKey()).append(" | ")
                         .append(entry.getValue().isNull() ? "" : entry.getValue().asText()).append(" |\n")
                );
                return table.toString();
            }
        } catch (Exception e) {
            // Not JSON — return the original answer as-is
        }
        return answer;
    }

    private String truncateResult(String result) {
        if (result == null) return "null";
        return result.length() > 500 ? result.substring(0, 500) + "..." : result;
    }
}

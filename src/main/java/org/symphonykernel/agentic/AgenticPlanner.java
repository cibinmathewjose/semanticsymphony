package org.symphonykernel.agentic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.mcp.MCPClientService;
import org.symphonykernel.mcp.MCPToolDescriptor;
import org.symphonykernel.mcp.MCPToolRegistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;

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
    private org.symphonykernel.ai.KnowledgeGraphBuilder knowledgeGraphBuilder;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Executes the agentic loop synchronously and returns the final result.
     */
    public ChatResponse execute(ExecutionContext ctx) {
        String userQuery = ctx.getUsersQuery();
        ConcurrentHashMap<String, JsonNode> resolvedValues = new ConcurrentHashMap<>(ctx.getResolvedValues());
        List<Map<String, String>> conversationHistory = new ArrayList<>();

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            logger.info("Agentic iteration {}/{}", iteration + 1, maxIterations);

            String planPrompt = buildPlanningPrompt(userQuery, resolvedValues, conversationHistory);
            String planResponse = aiClient.evaluatePrompt(planPrompt);

            AgentPlan plan = parsePlan(planResponse);
            if (plan == null) {
                logger.warn("Failed to parse agent plan, returning raw response");
                ChatResponse response = new ChatResponse();
                response.setMessage(planResponse);
                return response;
            }

            if (plan.isComplete() && plan.getFinalAnswer() != null) {
                logger.info("Agent completed task after {} iterations", iteration + 1);
                ChatResponse response = new ChatResponse();
                response.setMessage(plan.getFinalAnswer());
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
     */
    public Flux<String> executeStream(ExecutionContext ctx) {
        return Flux.create(sink -> {
            try {
                String userQuery = ctx.getUsersQuery();
                ConcurrentHashMap<String, JsonNode> resolvedValues = new ConcurrentHashMap<>(ctx.getResolvedValues());
                List<Map<String, String>> conversationHistory = new ArrayList<>();

                sink.next("Agent analyzing request...\n");

                for (int iteration = 0; iteration < maxIterations; iteration++) {
                    logger.info("Agentic stream iteration {}/{}", iteration + 1, maxIterations);

                    String planPrompt = buildPlanningPrompt(userQuery, resolvedValues, conversationHistory);
                    String planResponse = aiClient.evaluatePrompt(planPrompt);
                    AgentPlan plan = parsePlan(planResponse);

                    if (plan == null) {
                        sink.next(planResponse);
                        sink.complete();
                        return;
                    }

                    if (plan.isComplete() && plan.getFinalAnswer() != null) {
                        sink.next(plan.getFinalAnswer());
                        sink.complete();
                        return;
                    }

                    if (plan.getActions() == null || plan.getActions().isEmpty()) {
                        sink.next(plan.getReasoning() != null ? plan.getReasoning() : "No further actions.");
                        sink.complete();
                        return;
                    }

                    if (plan.getReasoning() != null) {
                        sink.next("Thinking: " + plan.getReasoning() + "\n");
                    }

                    for (AgentAction action : plan.getActions()) {
                        sink.next("Executing: " + action.getTool() + " - " + 
                            (action.getReasoning() != null ? action.getReasoning() : "") + "\n");
                        
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

                        sink.next("Completed: " + action.getTool() + "\n");
                    }
                }

                sink.next("Agent reached maximum iterations.");
                sink.complete();
            } catch (Exception e) {
                logger.error("Error in agentic stream: {}", e.getMessage(), e);
                sink.next("Error: " + e.getMessage());
                sink.complete();
            }
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
                IStep step = knowledgeGraphBuilder.getExecuter(kb);
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
            return objectMapper.readValue(json, AgentPlan.class);
        } catch (Exception e) {
            logger.warn("Failed to parse agent plan: {}", e.getMessage());
            return null;
        }
    }

    private String truncateResult(String result) {
        if (result == null) return "null";
        return result.length() > 500 ? result.substring(0, 500) + "..." : result;
    }
}

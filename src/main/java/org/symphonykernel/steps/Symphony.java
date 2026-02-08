package org.symphonykernel.steps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.FlowItem;
import org.symphonykernel.FlowJson;
import org.symphonykernel.Knowledge;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.QueryType;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IPluginLoader;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.transformer.JsonTransformer;
import org.symphonykernel.transformer.PlatformHelper;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Symphony is a step implementation for handling various execution contexts.
 * It integrates multiple helpers and provides methods for query execution and response generation.
 */
@Service
public class Symphony extends BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(Symphony.class);
    @Autowired
    IknowledgeBase knowledgeBase;

    
    @Autowired
    IPluginLoader pluginLoader;
    
    @Autowired
    @Qualifier("GraphQLStep")
    GraphQLStep graphQLHelper;
        
    @Autowired
    TemplateResolver templateResolver;
    
    @Autowired
    @Qualifier("RESTStep")
    RESTStep restHelper;
    
    @Autowired
    SqlStep sqlAssistant;

    @Autowired
    PlatformHelper platformHelper;

    @Autowired
    PluginStep pluginStep;
    @Autowired
    ToolStep toolStep;
    @Autowired
    VelocityStep velocityTemplateEngine;
    
    @Autowired
     Optional<RFCStep> rfcStep;

    @Autowired
    IAIClient azureOpenAIHelper;

    

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {
        JsonNode input = ctx.getVariables();
        Knowledge _symphony = ctx.getKnowledge();
        ArrayNode jsonArray = objectMapper.createArrayNode();
        logger.info("Executing Symphony " + _symphony.getName() + " with " + input);
        Map<String, JsonNode> resolvedValues = ctx.getResolvedValues();
        resolvedValues.put("input", input);
        try {
            FlowJson parsed = objectMapper.readValue(_symphony.getData(), FlowJson.class);
            processFlowItemsByOrder(parsed, ctx, resolvedValues)
            .doOnNext(item -> logger.info("Processing item: " + item))
            .then()
            .block();
            processFinalResponse(parsed, ctx, _symphony, resolvedValues, jsonArray);
           
        } catch (JsonProcessingException e) {
            handleJsonProcessingException(e, jsonArray);
        }
        logger.info("Data " + jsonArray);
        ChatResponse a = new ChatResponse();
        a.setData(jsonArray);
        saveStepData(ctx, jsonArray);
        return a;
    }
    @Override
    public Flux<String> getResponseStream(ExecutionContext ctx) {
        JsonNode input = ctx.getVariables();
        Knowledge _symphony = ctx.getKnowledge();
        logger.info("Executing Symphony {} with {}", _symphony.getName(), input);
        
        Map<String, JsonNode> resolvedValues = ctx.getResolvedValues();
        resolvedValues.put("input", input);

        try {
            FlowJson parsed = objectMapper.readValue(_symphony.getData(), FlowJson.class);
            
            // 1. This Flux handles the item processing and status strings
            Flux<String> progress = processFlowItemsByOrder(parsed, ctx, resolvedValues);
            
            // 2. This Flux handles the final LLM streaming
            StringBuilder responseAccumulator = new StringBuilder();
            Flux<String> finalResponse = processFinalResponseAsStream(parsed, ctx, _symphony, resolvedValues)
                .doOnNext(responseAccumulator::append)
                .doFinally(signalType -> {
                    saveStepData(ctx, responseAccumulator.toString());
                });

            // 3. CONCAT joins them: progress finishes, then finalResponse starts
            return Flux.concat(progress, finalResponse);

        } catch (Exception e) {
            saveStepData(ctx, e.getMessage());
            return Flux.just("Error processing Symphony: " + e.getMessage());
        }
    }

    /**
     * Helper method to handle JsonProcessingException and add error to response array.
     */
    private void handleJsonProcessingException(JsonProcessingException e, ArrayNode jsonArray) {
        ObjectNode err = objectMapper.createObjectNode();
        err.put("errors", e.getMessage());
        jsonArray.add(err);
    }

    /**
     * Helper method to process the final response logic (SystemPrompt, UserPrompt, result generation).
     */
    private void processFinalResponse(FlowJson parsed, ExecutionContext ctx, Knowledge _symphony, Map<String, JsonNode> resolvedValues, ArrayNode jsonArray) {
        if (parsed.SystemPrompt != null && !parsed.SystemPrompt.isEmpty()) {
            logger.info("Processing final response");
            String systemPrompt = templateResolver.resolvePlaceholders(parsed.SystemPrompt, resolvedValues);
            String userPrompt = parsed.UserPrompt;
            if (parsed.AdaptiveCardPrompt != null && !parsed.AdaptiveCardPrompt.trim().isEmpty()) {
                userPrompt = parsed.AdaptiveCardPrompt;
            } else if (userPrompt == null) {
                userPrompt = ctx.getUsersQuery();
            } else if (!userPrompt.trim().isEmpty() && TemplateResolver.hasPlaceholders(parsed.UserPrompt)) {
                userPrompt = templateResolver.resolvePlaceholders(parsed.UserPrompt, resolvedValues);
            }
            String result = null;
            if ((systemPrompt.indexOf(JsonTransformer.JSON) >= 0 || systemPrompt.indexOf(TemplateResolver.NO_DATA_FOUND) < 0) &&
                userPrompt.indexOf(JsonTransformer.JSON) >= 0 || userPrompt.indexOf(TemplateResolver.NO_DATA_FOUND) < 0) {
                if (_symphony.getTools() != null && _symphony.getTools().length() > 0) {
                    result = azureOpenAIHelper.execute(new LLMRequest(systemPrompt, userPrompt, loadTools(_symphony.getTools()), ctx.getModelName()));
                } else {
                    result = azureOpenAIHelper.execute(new LLMRequest(systemPrompt, userPrompt, null, ctx.getModelName()));
                }
            } else {
                result = "Unable to process request, data not availabe";
                logger.warn("Missing Data, Avoid LLM Call, systemPrompt {} userPrompt {}", systemPrompt, userPrompt);
            }
            JsonNode resultNode = parseJson(result);
            jsonArray.add(resultNode);
        } else if (parsed.Result != null && !parsed.Result.isEmpty()) {
            JsonNode resultNode = resolvedValues.get(parsed.Result.toLowerCase());
            jsonArray.add(resultNode);
        } else {
            ObjectNode objectNode = objectMapper.createObjectNode();
            for (Map.Entry<String, JsonNode> entry : resolvedValues.entrySet()) {
                objectNode.set(entry.getKey(), entry.getValue());
            }
            jsonArray.add(objectNode);
        }
    }
    private Flux<String> processFinalResponseAsStream(FlowJson parsed, ExecutionContext ctx, Knowledge _symphony, Map<String, JsonNode> resolvedValues) {
        if (parsed.SystemPrompt != null && !parsed.SystemPrompt.isEmpty()) {
            logger.info("Processing final response");
            String systemPrompt = templateResolver.resolvePlaceholders(parsed.SystemPrompt, resolvedValues);
            String userPrompt = parsed.UserPrompt;
            if (parsed.AdaptiveCardPrompt != null && !parsed.AdaptiveCardPrompt.trim().isEmpty()) {
                userPrompt = parsed.AdaptiveCardPrompt;
            } else if (userPrompt == null) {
                userPrompt = ctx.getUsersQuery();
            } else if (!userPrompt.trim().isEmpty() && TemplateResolver.hasPlaceholders(parsed.UserPrompt)) {
                userPrompt = templateResolver.resolvePlaceholders(parsed.UserPrompt, resolvedValues);
            }
           
            if ((systemPrompt.indexOf(JsonTransformer.JSON) >= 0 || systemPrompt.indexOf(TemplateResolver.NO_DATA_FOUND) < 0) &&
                userPrompt.indexOf(JsonTransformer.JSON) >= 0 || userPrompt.indexOf(TemplateResolver.NO_DATA_FOUND) < 0) {
                if (_symphony.getTools() != null && _symphony.getTools().length() > 0) {
                    return azureOpenAIHelper.streamExecute(new LLMRequest(systemPrompt, userPrompt, loadTools(_symphony.getTools()), ctx.getModelName()));
                } else {
                    return azureOpenAIHelper.streamExecute(new LLMRequest(systemPrompt, userPrompt, null, ctx.getModelName()));
                }
            } else {
                 String result = "Unable to process request, data not availabe";
                logger.warn("Missing Data, Avoid LLM Call, systemPrompt {} userPrompt {}", systemPrompt, userPrompt);
                return Flux.just(result);
            }
        } else if (parsed.Result != null && !parsed.Result.isEmpty()) {
            JsonNode resultNode = resolvedValues.get(parsed.Result.toLowerCase());
            return Flux.just(resultNode.toString());
        } else {
            ObjectNode objectNode = objectMapper.createObjectNode();
            for (Map.Entry<String, JsonNode> entry : resolvedValues.entrySet()) {
                objectNode.set(entry.getKey(), entry.getValue());
            }
            
            return Flux.just(objectNode.toString());
        }
    }
    
    /**
     * Loads Spring bean tools based on comma-separated tool names.
     * <p>
     * This method splits the input string by comma, trims each tool name,
     * and attempts to load the corresponding Spring bean from the application context.
     * </p>
     *
     * @param toolsNames comma-separated string of bean names to load
     * @return an array of loaded tool objects, or empty array if toolsNames is null/empty
     * @throws org.springframework.beans.BeansException if a bean cannot be found
     */
    private Object[] loadTools(String toolsNames) {
        if (toolsNames == null || toolsNames.isBlank()) {
            logger.warn("No tools specified to load");
            return new Object[0];
        }
        
        String[] toolNameArray = toolsNames.split(",");
        List<Object> tools = new ArrayList<>();
        
        for (String toolName : toolNameArray) {
            String trimmedName = toolName.trim();
            if (!trimmedName.isEmpty()) {
                try {
                    
                    Object tool =  pluginLoader.createObject(trimmedName);
                    tools.add(tool);
                    logger.info("Successfully loaded tool: {}", trimmedName);
                } catch (Exception e) {
                    logger.error("Failed to load tool: {}. Error: {}", trimmedName, e.getMessage());
                    throw new RuntimeException("Failed to load tool: " + trimmedName, e);
                }
            }
        }
        
        return tools.toArray();
    }
    
    private void processFlowItem(FlowItem item, ExecutionContext ctx, Map<String, JsonNode> resolvedValues) {
        if (shouldSkipItem(item, resolvedValues)) {
            return;
        }
        Knowledge kb = knowledgeBase.GetByName(item.getName());
        if (kb == null) {
            return;
        }
        logger.info("Executing Symphony: {} with Payload: {}", item.getName(), item.getPayload());
        ctx.setCurrentFlowItem(item);
        JsonNode resolverPayload = resolvePayload(item, resolvedValues);
        if (resolverPayload != null) {
            JsonNode result = processPayload(item, ctx, kb, resolverPayload);
            addResultMap(resolvedValues, item, result);
            processPrompt(item.getKey().toLowerCase(), resolvedValues, item.SystemPrompt);
        }
    }

    private boolean shouldSkipItem(FlowItem item, Map<String, JsonNode> resolvedValues) {
        if (item.getCondition() != null) {
            JsonNode conditionNode = resolvedValues.get(item.getCondition().toLowerCase());
            if (conditionNode != null && conditionNode.toString().toLowerCase().contains("false")) {
                logger.info("Skipping step {} as condition {} not met", item.getKey(), item.getCondition());
                return true;
            }
        }
        return false;
    }

    private JsonNode resolvePayload(FlowItem item, Map<String, JsonNode> resolvedValues) {
        if (item.getPayload() == null) {
            return null;
        }
        if (item.getPayload().contains(",")) {
            return resolveCommaPayload(item.getPayload(), resolvedValues);
        }
        return resolvedValues.get(item.getPayload().toLowerCase());
    }

    private JsonNode resolveCommaPayload(String payload, Map<String, JsonNode> resolvedValues) {
        String[] keys = payload.toLowerCase().split(",");
        ObjectNode combinedNode = objectMapper.createObjectNode();
        for (String key : keys) {
            key = key.trim();
            if (resolvedValues.containsKey(key)) {
                JsonNode value = resolvedValues.get(key);
                if (value.isObject()) {
                    combinedNode.setAll((ObjectNode) value);
                } else {
                    combinedNode.set(key, value);
                }
            }
        }
        return combinedNode;
    }

    private JsonNode processPayload(FlowItem item, ExecutionContext ctx, Knowledge kb, JsonNode resolverPayload) {
        String loopKey = item.getLoopKey();
        if (loopKey != null && !loopKey.isBlank()) {
            return processWithLoopKey(item, ctx, kb, resolverPayload, loopKey);
        }
        return processWithoutLoopKey(item, ctx, kb, resolverPayload);
    }

    private JsonNode processWithLoopKey(FlowItem item, ExecutionContext ctx, Knowledge kb, JsonNode resolverPayload, String loopKey) {
        if (resolverPayload.isArray()) {
            Map<String, JsonNode> resultPair = new HashMap<>();
            for (JsonNode idNode : resolverPayload) {
                JsonNode result = getResults(ctx, kb, idNode);
                if (result.isArray() && result.size() == 1) {
                    result = result.get(0);
                }
                String loopKeyValue = idNode.get(loopKey).asText();
                if (loopKeyValue != null && !loopKeyValue.isEmpty()) {
                    resultPair.put(loopKeyValue, result);
                } else {
                    logger.error("Error Unhandled: loopKeyValue is empty for result {} idnode {}", result, idNode);
                }
            }
            return objectMapper.valueToTree(resultPair);
        }
        return getResults(ctx, kb, resolverPayload);
    }

    private JsonNode processWithoutLoopKey(FlowItem item, ExecutionContext ctx, Knowledge kb, JsonNode resolverPayload) {
        if (resolverPayload.isArray() && !item.isArray()) {
            ArrayNode resultArray = objectMapper.createArrayNode();
            for (JsonNode idNode : resolverPayload) {
                JsonNode result = getResults(ctx, kb, idNode);
                if (result.isArray() && result.size() == 1) {
                    resultArray.add(result.get(0));
                } else {
                    resultArray.add(result);
                }
            }
            return resultArray;
        }
        return getResults(ctx, kb, resolverPayload);
    }

    private void addResultMap(Map<String, JsonNode> resolvedValues, FlowItem item, JsonNode resultNode) {
        if(TemplateResolver.isJsonNodeNullorEmpty(resultNode) )
        {           
            if(item.isRequired())
            {                    
                logger.error("No data found for step: " + item.getKey());
                throw new RuntimeException("No data found for step: " + item.getKey());
            }
            else
            {
                resultNode= objectMapper.createObjectNode();
                ((ObjectNode) resultNode).put(item.getKey(), "No data found" );
                logger.warn("No data found for step: " + item.getKey());
            }          
        }        
        else if(item.getJsonPath()!=null&&!item.getJsonPath().isEmpty())
        {
            logger.info("Evaluating expression {} on {} " , item.getJsonPath(),resultNode);
            try {
            resultNode = objectMapper.readTree(JsonPath.read(resultNode.toString(), item.getJsonPath()).toString());            
            logger.info("Expression result : {} " ,resultNode);
            } catch (Exception e) {
            	logger.error("Error evaluating {}",e.getMessage());
               }
        }
        resolvedValues.put(item.getKey().toLowerCase(), resultNode);        
    }

	private JsonNode getResults(ExecutionContext ctx, Knowledge kb, JsonNode idNode) {
        long startTime = System.currentTimeMillis(); // Start time logging
       
		JsonNode result;
		ExecutionContext newCtx = new ExecutionContext(ctx);	                                    
		newCtx.setName(kb.getName());	 
		newCtx.setVariables(idNode);		
		newCtx.setConvert(true);
		result = getResult(kb, newCtx);
        long endTime = System.currentTimeMillis(); // End time logging       
        logger.info("Processing time for "+kb.getName() + " = " + (endTime - startTime) + " ms");
		return result;
	}

	private void processPrompt(String key,Map<String, JsonNode> resolvedValues, String prompt) {
		if(prompt!=null&&!prompt.isEmpty())
		{   
			String systemPrompt = templateResolver.resolvePlaceholders(prompt, resolvedValues);
			String result = azureOpenAIHelper.evaluatePrompt(systemPrompt);    
			JsonNode resultNode = parseJson(result);    
			resolvedValues.replace(key, resultNode);
		    
		}
	}

	private JsonNode getResult(Knowledge kb, ExecutionContext newCtx) {
		JsonNode result = null;
		if (kb.getType() == QueryType.SQL) {
		    result = sqlAssistant.executeQueryByName(newCtx);
		} else if (kb.getType() == QueryType.GRAPHQL) {
		    result = graphQLHelper.executeQueryByName(newCtx);
		} else if (kb.getType() == QueryType.REST) {
		    result = restHelper.executeQueryByName(newCtx);
		} else if (kb.getType() == QueryType.SYMPHNOY) {
		    result = executeQueryByName(newCtx);
		}
		else if (kb.getType() == QueryType.PLUGIN) {
		    result = pluginStep.executeQueryByName(newCtx);
		}else if (kb.getType() == QueryType.TOOL) {
		    result = toolStep.executeQueryByName(newCtx);
		}
        else if (kb.getType() == QueryType.VELOCITY) {
		    result = velocityTemplateEngine.executeQueryByName(newCtx);
		}
         else if (kb.getType() == QueryType.RFC) {
                if (rfcStep.isPresent()) {
                        result = rfcStep.get().executeQueryByName(newCtx);
                } else {
                    logger.warn("RFC Step is not enabled");
                    return null;
                }
		  
		}
		return result;
	}

//    private Flux<String> processFlowItemsByOrder(FlowJson parsed, ExecutionContext ctx, Map<String, JsonNode> resolvedValues) {
//        // Group FlowItems by order
//        Map<Integer, List<FlowItem>> flowItemsByOrder = new TreeMap<>();
//        for (FlowItem item : parsed.Flow) {
//            Integer order = item.getOrder() != null ? item.getOrder() : 0;
//            flowItemsByOrder.computeIfAbsent(order, k -> new ArrayList<>()).add(item);
//        }
//        
//        // Process items in order
//        for (Map.Entry<Integer, List<FlowItem>> entry : flowItemsByOrder.entrySet()) {
//            Integer order = entry.getKey();
//            List<FlowItem> items = entry.getValue();
//            
//            if (order == 0 || items.size() == 1) {
//                // Process sequentially for order 0 or single items
//                for (FlowItem item : items) {
//                    processFlowItem(item, ctx, resolvedValues);
//                }
//            } else {
//
//                // Process in parallel for same order > 0            
//
//                    String traceId = MDC.get(Constants.LOGGER_TRACE_ID);
//                    logger.info("Processing order {} of {} items in parallel with traceId {}", order,items.size(), traceId);
//                    List<CompletableFuture<Void>> futures = items.stream()
//                        .map(item -> CompletableFuture.runAsync(() -> {
//                            try {
//                                MDC.put(Constants.LOGGER_TRACE_ID, traceId+"-"+item.getKey());
//                                processFlowItem(item, ctx, resolvedValues);
//                            } finally {
//                                MDC.clear();
//                            }
//                        })).collect(Collectors.toList());
//                
//                // Wait for all parallel executions to complete
//                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//            }
//        }
//    }
    private Flux<String> processFlowItemsByOrder(FlowJson parsed, ExecutionContext ctx, Map<String, JsonNode> resolvedValues) {
        // 1. Group items as before
        Map<Integer, List<FlowItem>> flowItemsByOrder = new TreeMap<>();
        for (FlowItem item : parsed.Flow) {
            Integer order = item.getOrder() != null ? item.getOrder() : 0;
            flowItemsByOrder.computeIfAbsent(order, k -> new ArrayList<>()).add(item);
        }

        // 2. Convert the Map entries into a Flux to process each "Order Group" sequentially
        return Flux.fromIterable(flowItemsByOrder.entrySet())
            .concatMap(entry -> {
                Integer order = entry.getKey();
                List<FlowItem> items = entry.getValue();

                if (order == 0 || items.size() == 1) {
                    // SEQUENTIAL PROCESSING
                    return Flux.fromIterable(items)
                        .concatMap(item -> executeWithStatus(item, ctx, resolvedValues));
                } else {
                    // PARALLEL PROCESSING (for items within the same order group)
                    return Flux.fromIterable(items)
                        .flatMap(item -> executeWithStatus(item, ctx, resolvedValues));
                }
            });
    }

    private Flux<String> executeWithStatus(FlowItem item, ExecutionContext ctx, Map<String, JsonNode> resolvedValues) {
        return Flux.defer(() -> {
            String itemName = item.getKey() != null ? item.getKey() : "Unknown Item";
            
            // Create the sequence: Message -> Actual Work -> Message
            return Flux.just("STARTING: " + itemName)
                .concatWith(Mono.fromRunnable(() -> {
                    // Note: If processFlowItem is blocking, wrap it in Schedulers.boundedElastic()
                    processFlowItem(item, ctx, resolvedValues);
                    logger.info("Completed processing item: {} data {}" , itemName,resolvedValues.get(itemName.toLowerCase()));
                }).then(Mono.empty())) // then(Mono.empty()) ensures no data is emitted here
                .concatWith(Flux.just("COMPLETED: " + itemName));
        }).subscribeOn(Schedulers.boundedElastic()); // Keeps parallel work off the main thread
    }

    @Override
    protected ArrayNode getData(ExecutionContext ctx) {
        // Reuse the logic from getResponse, but only return the ArrayNode data
        JsonNode input = ctx.getVariables();
        Knowledge _symphony = ctx.getKnowledge();
        ArrayNode jsonArray = objectMapper.createArrayNode();
        Map<String, JsonNode> resolvedValues = ctx.getResolvedValues();
        resolvedValues.put("input", input);
        try {
            FlowJson parsed = objectMapper.readValue(_symphony.getData(), FlowJson.class);
            processFlowItemsByOrder(parsed, ctx, resolvedValues);
            processFinalResponse(parsed, ctx, _symphony, resolvedValues, jsonArray);
        } catch (JsonProcessingException e) {
            handleJsonProcessingException(e, jsonArray);
        }
        return jsonArray;
    }
}

package org.symphonykernel.steps;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.FlowItem;
import org.symphonykernel.FlowJson;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;
import org.symphonykernel.ai.AzureOpenAIHelper;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.transformer.PlatformHelper;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Symphony is a step implementation for handling various execution contexts.
 * It integrates multiple helpers and provides methods for query execution and response generation.
 */
@Service
public class Symphony implements IStep {

    private static final Logger logger = LoggerFactory.getLogger(Symphony.class);

    @Autowired
    IknowledgeBase knowledgeBase;
    
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
    AzureOpenAIHelper azureOpenAIHelper;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {
    	
        JsonNode input = ctx.getVariables();
        Knowledge _symphony = ctx.getKnowledge();
        ArrayNode jsonArray = objectMapper.createArrayNode();
        logger.info("Executing Symphony " + _symphony.getName() + " with " + input);
        Map<String, JsonNode> resolvedValues = new HashMap<>();
        ctx.setResolvedValues(resolvedValues);
        resolvedValues.put("input", input);
        try {
            FlowJson parsed = objectMapper.readValue(_symphony.getData(), FlowJson.class);
            
            // Group FlowItems by order
            Map<Integer, List<FlowItem>> flowItemsByOrder = new TreeMap<>();
            for (FlowItem item : parsed.Flow) {
                Integer order = item.getOrder() != null ? item.getOrder() : 0;
                flowItemsByOrder.computeIfAbsent(order, k -> new ArrayList<>()).add(item);
            }
            
            // Process items in order
            for (Map.Entry<Integer, List<FlowItem>> entry : flowItemsByOrder.entrySet()) {
                Integer order = entry.getKey();
                List<FlowItem> items = entry.getValue();
                
                if (order == 0 || items.size() == 1) {
                    // Process sequentially for order 0 or single items
                    for (FlowItem item : items) {
                        processFlowItem(item, ctx, resolvedValues);
                    }
                } else {
                    // Process in parallel for same order > 0
                    List<CompletableFuture<Void>> futures = items.stream()
                        .map(item -> CompletableFuture.runAsync(() -> 
                            processFlowItem(item, ctx, resolvedValues)))
                        .collect(Collectors.toList());
                    
                    // Wait for all parallel executions to complete
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                }
            }
            
            // Process final response
            if (parsed.SystemPrompt != null && !parsed.SystemPrompt.isEmpty()) {

                String systemPrompt = templateResolver.resolvePlaceholders(parsed.SystemPrompt, resolvedValues);
                
                String userPrompt = parsed.UserPrompt;
                if (parsed.AdaptiveCardPrompt != null && !parsed.AdaptiveCardPrompt.trim().isEmpty()) {
                    userPrompt = parsed.AdaptiveCardPrompt;
                } else if (userPrompt == null) {
                    userPrompt = ctx.getUsersQuery();
                } else if (!userPrompt.trim().isEmpty()&&TemplateResolver.hasPlaceholders(parsed.UserPrompt)) {
                    userPrompt = templateResolver.resolvePlaceholders(parsed.UserPrompt, resolvedValues);
                }               
                String result =null;
                if((systemPrompt.indexOf(TemplateResolver.JSON)>=0||systemPrompt.indexOf(TemplateResolver.NO_DATA_FOUND)<0)&&
                	userPrompt.indexOf(TemplateResolver.JSON)>=0||userPrompt.indexOf(TemplateResolver.NO_DATA_FOUND)<0)
                {                	
                	result = azureOpenAIHelper.execute(systemPrompt, userPrompt);    
	               
                }
                else
                {
                	result="Unable to process request, data not availabe";
                	logger.warn("Missing Data, Avoid LLM Call, systemPrompt {} userPrompt {}",systemPrompt,userPrompt);
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

        } catch (JsonProcessingException e) {
            ObjectNode err = objectMapper.createObjectNode();
            err.put("errors", e.getMessage());
            jsonArray.add(err);
        }
        logger.info("Data " + jsonArray);
        ChatResponse a = new ChatResponse();
        a.setData(jsonArray);
        return a;
    }
    
    /**
     * Process a single flow item
     */
    private synchronized void processFlowItem(FlowItem item, ExecutionContext ctx, Map<String, JsonNode> resolvedValues) {
        Knowledge kb = knowledgeBase.GetByName(item.getName());
        if (kb != null) {
            logger.info("Executing Symphony: " + item.getName() + " with Payload: " + item.getPaylod());
            JsonNode result = null;
            JsonNode resolverPayload = null;
            ctx.setCurrentFlowItem(item);
            if (item.getPaylod() != null) {
                resolverPayload = resolvedValues.get(item.getPaylod().toLowerCase());
            }                    
            if (resolverPayload != null) {
                String loopKey = item.getLoopKey();
                if (loopKey != null && !loopKey.isBlank()) {
                    if (resolverPayload.isArray()) {
                        Map<String, JsonNode> resultPair = new HashMap<>();      
                        for (JsonNode idNode : resolverPayload) {      
                            result = getResults(ctx, kb, idNode);	  
                            if (result.isArray() && result.size() == 1) {
                                result = result.get(0);                       
                            }
                            // create a key value pair with key as the value of loopKey in idNode and value as the result and add to resolvedValues                                        
                            String loopKeyValue = idNode.get(loopKey).asText(); 
                            if (loopKeyValue != null && !loopKeyValue.isEmpty()) {
                                resultPair.put(loopKeyValue, result);
                            } else {
                                logger.error("Error Unhandled: senario loopKeyValue is empty for result {} idnode {}", result, idNode);
                            }	                                   
                        }
                        JsonNode resultPairNode = objectMapper.valueToTree(resultPair);
                        addResultMap(resolvedValues, item, resultPairNode);
                    } else {
                        result = getResults(ctx, kb, resolverPayload);	                                
                        addResultMap(resolvedValues, item, result);
                    }                            	
                } else {
                    if (resolverPayload.isArray() && !item.isArray()) {
                        ArrayNode resultArray = objectMapper.createArrayNode();
                        for (JsonNode idNode : resolverPayload) {
                            result = getResults(ctx, kb, idNode);	                                    
                            if (result.isArray()) {
                                if (result.size() == 1)
                                    resultArray.add(result.get(0));
                                else
                                    resultArray.add(result);	 
                                    logger.warn("Review senario" + result);
                            } else {
                                resultArray.add(result);
                                logger.warn("Review senario" + result);
                            }
                        }	                               
                        addResultMap(resolvedValues, item, resultArray);
                    } else {
                        result = getResults(ctx, kb, resolverPayload);	                                
                        addResultMap(resolvedValues, item, result);
                    }
                }
                processPrompt(item.getKey().toLowerCase(), resolvedValues, item.SystemPrompt);
            }
        }
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
        resolvedValues.put(item.getKey().toLowerCase(), resultNode);        
    }

	private JsonNode getResults(ExecutionContext ctx, Knowledge kb, JsonNode idNode) {
		JsonNode result;
		ExecutionContext newCtx = new ExecutionContext();	                                    
		newCtx.setName(kb.getName());	 
		newCtx.setVariables(idNode);
		newCtx.setHttpHeaderProvider(ctx.getHttpHeaderProvider());
		newCtx.setUsersQuery(ctx.getUsersQuery());
        newCtx.setChatHistory(ctx.getChatHistory());
        newCtx.setResolvedValues(ctx.getResolvedValues());
        newCtx.setCurrentFlowItem(ctx.getCurrentFlowItem());
		newCtx.setConvert(true);
		result = getResult(kb, newCtx);
		return result;
	}

	private void processPrompt(String key,Map<String, JsonNode> resolvedValues, String prompt) {
		
		if(prompt!=null&&!prompt.isEmpty())
		{   
			String systemPrompt = templateResolver.resolvePlaceholders(prompt, resolvedValues);
			String result = azureOpenAIHelper.ask(systemPrompt);    
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
		}
		return result;
	}

    /**
     * Parses a JSON string into a JsonNode.
     *
     * @param jsonString the JSON string to parse
     * @return the parsed JsonNode
     */
    public JsonNode parseJson(String jsonString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            if (jsonString != null) {
                if (jsonString.startsWith("```json")) {
                    jsonString = jsonString.replace("```json", "").replace("```", "");
                }

            } else {
                jsonString = "";
            }
            if (jsonString.startsWith("[") || jsonString.startsWith("{")) {
                return objectMapper.readTree(jsonString);
            } else {
                return createTextNode(jsonString, objectMapper);
            }

        } catch (Exception e) {

            return createTextNode(jsonString, objectMapper);
        }
    }

    private JsonNode createTextNode(String jsonString, ObjectMapper objectMapper) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("TextOutput", jsonString);
        return objectNode;
    }

    @Override
    public JsonNode executeQueryByName(ExecutionContext context) {
        // TODO Auto-generated method stub
        return null;
    }

}

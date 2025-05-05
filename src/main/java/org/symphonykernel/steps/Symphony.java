package org.symphonykernel.steps;

import java.util.HashMap;
import java.util.Map;

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

        Map<String, JsonNode> resolvedValues = new HashMap<>();
        resolvedValues.put("input", input);
        try {
            FlowJson parsed = objectMapper.readValue(_symphony.getData(), FlowJson.class);
            for (FlowItem item : parsed.Flow) {
                Knowledge kb = knowledgeBase.GetByName(item.getName());
                if (kb != null) {

                	logger.info("Executing Symphony: " + item.getName() + " with Payload: " + item.getPaylod());
                    JsonNode result = null;
                    JsonNode resolverPayload = null;
                    if (item.getPaylod() != null) {
                        resolverPayload = resolvedValues.get(item.getPaylod().toLowerCase());
                        //TO DO get nested object mapping
                    }
                    if (item.isArray()) {
                        if (resolverPayload != null) {
                            if (resolverPayload != null && resolverPayload.isArray()) {
                                ArrayNode resultArray = objectMapper.createArrayNode();
                                for (JsonNode idNode : resolverPayload) {
                                    ExecutionContext newCtx = new ExecutionContext();
                                    newCtx.setName(kb.getName());
                                    newCtx.setUsersQuery(ctx.getUsersQuery());
                                    newCtx.setVariables(idNode);
                                    newCtx.setHttpHeaderProvider(ctx.getHttpHeaderProvider());
                                    newCtx.setConvert(true);
                                    result = getResult(kb, newCtx);
                                    if (result.isArray() && result.size() == 1) {
                                        resultArray.add(result.get(0));
                                    } else {
                                    	logger.error("Error Unhandled: senario" + result);
                                    }
                                }
                                resolvedValues.put(kb.getName().toLowerCase(), resultArray);
                            }
                        }
                    } else {
                        ExecutionContext newCtx = new ExecutionContext();
                        newCtx.setName(kb.getName());
                        newCtx.setVariables(resolverPayload);
                        newCtx.setHttpHeaderProvider(ctx.getHttpHeaderProvider());
                        newCtx.setUsersQuery(ctx.getUsersQuery());
                        newCtx.setConvert(true);
                        result = getResult(kb, newCtx);                        
                        resolvedValues.put(kb.getName().toLowerCase(), result);
                    }
                }
            }
            if (parsed.SystemPrompt != null && !parsed.SystemPrompt.isEmpty()) {

                String systemPrompt = TemplateResolver.resolvePlaceholders(parsed.SystemPrompt, resolvedValues);
                String userPrompt = parsed.UserPrompt;
                if (parsed.AdaptiveCardPrompt != null && !parsed.AdaptiveCardPrompt.trim().isEmpty()) {
                    userPrompt = parsed.AdaptiveCardPrompt;
                } else if (userPrompt == null || userPrompt.trim().isEmpty()) {
                    userPrompt = ctx.getUsersQuery();
                } else if (TemplateResolver.hasPlaceholders(parsed.UserPrompt)) {
                    userPrompt = TemplateResolver.resolvePlaceholders(parsed.UserPrompt, resolvedValues);
                }
                String result = azureOpenAIHelper.execute(systemPrompt, userPrompt);
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

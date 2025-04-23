package org.symphonykernel.core;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.FlowItem;
import org.symphonykernel.FlowJson;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class Symphony implements IStep {

    @Autowired
    IknowledgeBase knowledgeBase;
    @Autowired
    GraphQLStep graphQLHelper;
    @Autowired
    SqlStep sqlAssistant;

    @Autowired
    PlatformHelper platformHelper;

    @Autowired
    AzureOpenAIHelper azureOpenAIHelper;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ArrayNode getResponse(ExecutionContext ctx) {
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

                    System.out.println("Executing Symphony: " + item.getName() + " with Payload: " + item.getPaylod());
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
                                    if (kb.getType() == QueryType.SQL) {
                                        result = sqlAssistant.executeQueryByName(newCtx);
                                    } else if (kb.getType() == QueryType.GRAPHQL) {
                                        result = graphQLHelper.executeQueryByName(newCtx);
                                    } else if (kb.getType() == QueryType.SYMPHNOY) {
                                        result = executeQueryByName(newCtx);
                                    }
                                    if (result.isArray() && result.size() == 1) {
                                        resultArray.add(result.get(0));
                                    } else {
                                        System.out.println("Error Unhandled: senario" + result);
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

                        if (kb.getType() == QueryType.SQL) {
                            result = sqlAssistant.executeQueryByName(newCtx);
                        } else if (kb.getType() == QueryType.GRAPHQL) {
                            result = graphQLHelper.executeQueryByName(newCtx);
                        } else if (kb.getType() == QueryType.SYMPHNOY) {
                            result = executeQueryByName(newCtx);
                        }
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

        return jsonArray;
    }

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

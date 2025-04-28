package org.symphonykernel.ai;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;
import org.symphonykernel.UserSession;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.providers.SessionProvider;
import org.symphonykernel.steps.GraphQLStep;
import org.symphonykernel.steps.PluginStep;
import org.symphonykernel.steps.SqlStep;
import org.symphonykernel.steps.Symphony;
import org.symphonykernel.transformer.PlatformHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;

@Component
public class KnowledgeGraphBuilder {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeGraphBuilder.class);

    private static final String NONE = "NONE";
    private static final String STATUS_SUCCESS = "SUCCESS";

    @Autowired
    IknowledgeBase knowledgeBaserepo;

    @Autowired
    PlatformHelper platformHelper;

    @Autowired
    AzureOpenAIHelper openAI;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    QueryHandler queryHandler;

    @Autowired
    Symphony symphony;

    @Autowired
    GraphQLStep graphQLHelper;

    @Autowired
    SqlStep sqlAssistant;

    @Autowired
    PluginStep pluginStep;

    @Autowired
    SessionProvider sessionManager;

    @Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/matchKnowledgePrompt.text') }")
    private String matchKnowledgePrompt;

    @Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/paramParserPrompt.text') }")
    private String paramParserPrompt;

    private static final ThreadLocal<ExecutionContext> threadLocalContext = ThreadLocal.withInitial(ExecutionContext::new);

    public ExecutionContext getLocalExecutionContext() {
        return threadLocalContext.get();
    }

    public ExecutionContext createContext(ChatRequest request) {
        ExecutionContext ctx = new ExecutionContext();
        ctx.setRequest(request);
        //todo: parse the request and set the context
        ctx.setUsersQuery(request.getQuery());
        ctx.setHttpHeaderProvider(request.getHeaderProvider());
        ChatHistory chatHistory = sessionManager.getChatHistory(request);
        ctx.setChatHistory(chatHistory);
        UserSession info = sessionManager.createUserSession(request);
        ctx.setUserSession(info);
        return ctx;
    }

    public ExecutionContext identifyIntent(ExecutionContext ctx ) {
        Knowledge knowledge = matchKnowledge(ctx.getUsersQuery());
        ctx.setKnowledge(knowledge);
        return ctx;
    }


    public ExecutionContext setParameters(ExecutionContext ctx) {
        ChatRequest request=ctx.getRequest();
        Knowledge knowledge=ctx.getKnowledge();
        if (knowledge != null && request != null && knowledge.getParams() != null &&
            (request.getPayload() == null || NONE.equals(request.getPayload()))) {
            String params = openAI.evaluatePrompt(paramParserPrompt, knowledge.getParams(), request.getQuery());
            request.setPayload(params);
            ctx.setRequest(request);
        }
        JsonNode node = getVariables(request);
        ctx.setVariables(node);
        logger.info("Variables : " + node);
        return ctx;
    }

    private JsonNode getVariables(ChatRequest request) {
        if (request != null && request.getPayload() != null && !request.getPayload().isEmpty()) {
            try {
                return objectMapper.readTree(request.getPayload());
            } catch (Exception e) {
                logger.error("Error decoding payload: {}", e.getMessage());
            }
        }
        return JsonNodeFactory.instance.objectNode();
    }

    public ChatResponse getResponse(ExecutionContext ctx) {       
        Knowledge knowledge= ctx.getKnowledge();        
        IStep step = getExecuter(knowledge);
        ChatResponse response = (step != null) ? step.getResponse(ctx) : new ChatResponse();
        if (step == null) {
            response.setMessage("No knowledge found for the query");
        }
        if (knowledge != null && knowledge.getCard() != null && response.getData() != null && !response.getData().isEmpty()) {
            response.setMessage(platformHelper.generateAdaptiveCardJson(response.getData().get(0), knowledge.getCard()));
        }
        response.setRequestId(ctx.getRequestId());
        response.setStatusCode(STATUS_SUCCESS);
        sessionManager.updateUserSession(ctx.getUserSession(), response);
        return response;
    }

    private Knowledge matchKnowledge(String question) {
        try {
            // Fetch knowledge descriptions and convert to JSON string
            //Todo: Optimize the query to fetch only the required knowledge descriptions based on the input query
            Map<String, String> knowledgeDesc = knowledgeBaserepo.getActiveKnowledgeDescriptions();
            String jsonString = objectMapper.writeValueAsString(knowledgeDesc);

            // Get response from OpenAI
            String response = openAI.evaluatePrompt(matchKnowledgePrompt, jsonString, question);

            // Return knowledge if response is valid
            if (response != null) {
                return knowledgeBaserepo.GetByName(response.trim());
            } else {
                String query = queryHandler.matchSelectQuery(question);
                if (query != null) {
                    Knowledge k = new Knowledge();
                    k.setType(QueryType.SQL);
                    k.setData(query);
                    return k;
                }
            }

        } catch (JsonProcessingException e) {
            // Log the exception for debugging
            e.printStackTrace();
        }
        return null;
    }

    public IStep getExecuter(Knowledge knowledge) {

        if (knowledge == null || knowledge.getType() == null) {
            logger.warn("Knowledge or its type is null");
            return null;
        }
        switch (knowledge.getType()) {
            case SQL -> {
                return sqlAssistant;
            }
            case GRAPHQL -> {
                return graphQLHelper;
            }
            case SYMPHNOY -> {
                return symphony;
            }
            case PLUGIN -> {
                return pluginStep;
            }
            case REST -> {
                return null;
            }
            case TEXT -> {
                return null;
            }
            default -> {
                logger.warn("Unhandled QueryType: " + knowledge.getType());
                return null;
            }
        }
    }
}

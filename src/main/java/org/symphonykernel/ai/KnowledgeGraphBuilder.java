package org.symphonykernel.ai;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    ExecutionContext ctx;
    ChatRequest request;
    IStep step;
    Knowledge knowledge;

    public KnowledgeGraphBuilder() {
        ctx = new ExecutionContext();
    }

    public KnowledgeGraphBuilder createContext(ChatRequest req) {
        this.request = req;
        ctx.setUsersQuery(request.getQuery());

        ctx.setHttpHeaderProvider(request.getHeaderProvider());

        ChatHistory chatHistory = sessionManager.getChatHistory(request);
        ctx.setChatHistory(chatHistory);

        UserSession info = sessionManager.createUserSession(request);
        ctx.setUserSession(info);
        return this;
    }
    
    public KnowledgeGraphBuilder identifyIntent() {
        knowledge = matchKnowledge(request.getQuery());
        if(knowledge!=null)
        	logger.info("knowledge : "+knowledge.getName());
        else
        	 logger.error("knowledge : null");
        ctx.setKnowledge(knowledge);
        return this;
    }

    public KnowledgeGraphBuilder setParameters() {
        if (knowledge != null && request != null && knowledge.getParams() != null && (request.getPayload() == null || NONE.equals(request.getPayload()))) {
            String params = openAI.evaluatePrompt(paramParserPrompt, knowledge.getParams(), request.getQuery());
            request.setPayload(params);
        }
        JsonNode node=getVariables();
        ctx.setVariables(node);
        logger.info("Variables : "+node);
        return this;
    }
    
    public JsonNode getVariables() {
        if (this.request != null && this.request.getPayload() != null && !this.request.getPayload().isEmpty()) {
            try {
                // Decode the Base64 string
                byte[] decodedBytes = Base64.getDecoder().decode(this.request.getPayload());
                String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);

                // Parse the JSON string
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readTree(decodedString);

            } catch (Exception e) {
                // Handle invalid Base64 input
                logger.error("Error decoding Base64 payload: {}", e.getMessage());

            }
        }
        return JsonNodeFactory.instance.objectNode();
    }

    public KnowledgeGraphBuilder initExecuter() {      
        step = getExecuter(ctx.getKnowledge());
        return this;
    }

    public ChatResponse getResponse() {
        ChatResponse a ;
        if (step != null ) {
            a= step.getResponse(ctx);       
        }
        else
        {
            a = new ChatResponse();
            a.setMessage("No knowledge found for the query");
            a.setStatusCode(STATUS_SUCCESS);
        }     
        if (knowledge.getCard() != null) {
            a.setMessage(platformHelper.generateAdaptiveCardJson(a.getData().get(0), knowledge.getCard()));

        }
        a.setRequestId(ctx.getRequestId());  
        a.setStatusCode(STATUS_SUCCESS);
        sessionManager.updateUserSession(ctx.getUserSession(), a);
        return a;
    }

    private Knowledge matchKnowledge(String question) {
        try {
            // Fetch knowledge descriptions and convert to JSON string
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

package org.symphonykernel.ai;

import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;
import org.symphonykernel.UserSession;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.providers.FileContentProvider;
import org.symphonykernel.providers.SessionProvider;
import org.symphonykernel.steps.GraphQLStep;
import org.symphonykernel.steps.PluginStep;
import org.symphonykernel.steps.RESTStep;
import org.symphonykernel.steps.SharePointSearchStep;
import org.symphonykernel.steps.SqlStep;
import org.symphonykernel.steps.Symphony;
import org.symphonykernel.transformer.JsonTransformer;
import org.symphonykernel.transformer.PlatformHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;

/**
 * The KnowledgeGraphBuilder class is responsible for building and managing the
 * execution context for processing user queries, identifying intents, setting
 * parameters, and generating responses. It integrates with various components
 * such as knowledge bases, OpenAI, and execution steps (e.g., SQL, GraphQL,
 * REST, etc.) to provide a seamless query processing pipeline.
 *
 * <p>
 * This class is annotated with Spring's {@code @Component} to enable dependency
 * injection and is designed to work within a Spring application context.</p>
 *
 * <h2>Key Responsibilities:</h2>
 * <ul>
 * <li>Create and manage {@link ExecutionContext} for user queries.</li>
 * <li>Identify user intent by matching queries with knowledge
 * descriptions.</li>
 * <li>Set parameters for queries using OpenAI prompt evaluation.</li>
 * <li>Generate responses by executing the appropriate step based on the
 * identified knowledge type.</li>
 * </ul>
 *
 * <h2>Dependencies:</h2>
 * <ul>
 * <li>{@link IknowledgeBase} - Repository for managing knowledge
 * descriptions.</li>
 * <li>{@link PlatformHelper} - Helper for platform-specific operations.</li>
 * <li>{@link AzureOpenAIHelper} - Integration with OpenAI for prompt
 * evaluation.</li>
 * <li>{@link ObjectMapper} - JSON processing utility.</li>
 * <li>{@link QueryHandler} - Handles query matching and parsing.</li>
 * <li>{@link Symphony}, {@link GraphQLStep}, {@link RESTStep}, {@link SqlStep}, {@link PluginStep}
 * - Execution steps for various query types.</li>
 * <li>{@link SessionProvider} - Manages user sessions and chat history.</li>
 * <li>{@link VectorSearchHelper} - Provides vector-based search
 * capabilities.</li>
 * <li>{@link FileContentProvider} - Loads file-based content for prompts.</li>
 * </ul>
 *
 * <h2>Thread Safety:</h2>
 * <p>
 * This class uses a {@link ThreadLocal} to manage {@link ExecutionContext}
 * instances for each thread, ensuring thread safety for context-specific
 * operations.</p>
 *
 * <h2>Methods:</h2>
 * <ul>
 * <li>{@link #getLocalExecutionContext()} - Retrieves the thread-local
 * execution context.</li>
 * <li>{@link #createContext(ChatRequest)} - Creates a new execution context for
 * a given chat request.</li>
 * <li>{@link #identifyIntent(ExecutionContext)} - Identifies the intent of the
 * user's query.</li>
 * <li>{@link #setParameters(ExecutionContext)} - Sets parameters for the query
 * using OpenAI prompt evaluation.</li>
 * <li>{@link #getResponse(ExecutionContext)} - Generates a response based on
 * the execution context.</li>
 * <li>{@link #matchKnowledge(String)} - Matches a query to a knowledge
 * description or SQL query.</li>
 * <li>{@link #getExecuter(Knowledge)} - Retrieves the appropriate execution
 * step based on the knowledge type.</li>
 * </ul>
 *
 * <h2>Logging:</h2>
 * <p>
 * Uses SLF4J for logging important events and errors during query
 * processing.</p>
 *
 * <h2>Usage:</h2>
 * <p>
 * This class is intended to be used as a Spring-managed bean and should not be
 * instantiated manually.</p>
 */
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
    @Qualifier("GraphQLStep")
    GraphQLStep graphQLHelper;

    @Autowired
    @Qualifier("RESTStep")
    RESTStep restHelper;

    @Autowired
    SqlStep sqlAssistant;

    @Autowired
    SharePointSearchStep sharePointAssistant;

    @Autowired
    PluginStep pluginStep;

    @Autowired
    SessionProvider sessionManager;
    @Autowired
    VectorSearchHelper vector;
    @Autowired
    private FileContentProvider fileContentProvider;

    //@Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/matchKnowledgePrompt.text') }")
    //@Value("${fileContentProvider.matchKnowledgePrompt}")
    //private String matchKnowledgePrompt;
    //@Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/paramParserPrompt.text') }")
    //@Value("${fileContentProvider.paramParserPrompt}")
    //private String paramParserPrompt;
    private static final ThreadLocal<ExecutionContext> threadLocalContext = ThreadLocal.withInitial(ExecutionContext::new);

    /**
     * Retrieves the thread-local execution context for the current thread.
     *
     * @return the {@link ExecutionContext} associated with the current thread.
     */
    public ExecutionContext getLocalExecutionContext() {
        return threadLocalContext.get();
    }

    /**
     * Creates a new execution context for a given chat request.
     *
     * @param request the {@link ChatRequest} containing user query and
     * metadata.
     * @return a new {@link ExecutionContext} initialized with the request data.
     */
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

    /**
     * Identifies the intent of the user's query by matching it with knowledge
     * descriptions.
     *
     * @param ctx the {@link ExecutionContext} containing the user's query.
     * @return the updated {@link ExecutionContext} with identified knowledge.
     */
    public ExecutionContext identifyIntent(ExecutionContext ctx) {
        var request = ctx.getRequest();
        JsonNode params = null;
        if (request != null) {
            params = request.getVariables();
        }
        Knowledge knowledge = matchKnowledge(ctx.getUsersQuery(), params);
        if(knowledge==null)
        {
        	throw new RuntimeException("No matching knowldge found");       
        }
        logger.info("Knowldge idetified as {}",knowledge.getName());
        ctx.setKnowledge(knowledge);
        return ctx;
        
    }

    /**
     * Sets parameters for the query using OpenAI prompt evaluation.
     *
     * @param ctx the {@link ExecutionContext} containing the query and
     * knowledge.
     * @return the updated {@link ExecutionContext} with parameters set.
     */
    public ExecutionContext setParameters(ExecutionContext ctx) {
        ChatRequest request = ctx.getRequest();
        if (request == null) {
            throw new RuntimeException("Request object must be set in context");
        }
        Knowledge knowledge = ctx.getKnowledge();
        if (knowledge != null && knowledge.getParams() != null) {
            if (request.getPayload() != null && !NONE.equals(request.getPayload())) {
                logger.warn("Ignore paylod : " + request.getPayload());
            } else {
                String params = openAI.evaluatePrompt(fileContentProvider.paramParserPrompt, knowledge.getParams(), request.getQuery());
                request.setPayload(params);
                logger.info("payload identified as : " + params);
            }
        }
        ctx.setRequest(request);
        JsonNode node = request.getVariables();
        if (knowledge != null) {
            node = mapMissingVariables(node, knowledge.getParams());
        }
        ctx.setVariables(node);
        logger.info("Variables set as : " + node);
        return ctx;
    }

    JsonNode mapMissingVariables(JsonNode availableVariables, String params) {
        if (params != null && !params.isEmpty()) {
            try {
                JsonNode paramNode = objectMapper.readTree(params);
                if(paramNode.isArray())
                	paramNode=paramNode.get(0);
                if (availableVariables.isArray()) {
                    ArrayNode updatedArray = objectMapper.createArrayNode();
                    for (JsonNode item : availableVariables) {
                        Map<String, Object> variables = objectMapper.convertValue(item, Map.class);
                        Iterator<String> fieldNames = paramNode.fieldNames();
                        boolean changed = false;
                        while (fieldNames.hasNext()) {
                            String fieldName = fieldNames.next();
                            boolean hasKey = variables.keySet().stream()
                                    .anyMatch(existingKey -> existingKey.equalsIgnoreCase(fieldName));
                            Object mappedValue = getValue(paramNode, variables, fieldName);
                            if (hasKey) {                            	
                            	variables.keySet().removeIf(existingKey -> existingKey.equalsIgnoreCase(fieldName));
                            }
                            variables.put(fieldName, mappedValue);
                            changed = true;                            
                        }
                        if (changed) {
                            updatedArray.add(objectMapper.valueToTree(variables));
                        } else {
                            updatedArray.add(item);
                        }
                    }
                    return updatedArray;
                } else {
                    Map<String, Object> variables = objectMapper.convertValue(availableVariables, Map.class);
                    Iterator<String> fieldNames = paramNode.fieldNames();
                    boolean changed = false;
                    while (fieldNames.hasNext()) {
                        String fieldName = fieldNames.next();
                        boolean hasKey = variables.keySet().stream()
                                .anyMatch(existingKey -> existingKey.equalsIgnoreCase(fieldName));
                        Object mappedValue = getValue(paramNode, variables, fieldName);
                        if (hasKey) {                        	
                        	variables.keySet().removeIf(existingKey -> existingKey.equalsIgnoreCase(fieldName));                                                    
                        }
                        variables.put(fieldName, mappedValue);
                        changed = true;                        
                    }
                    if (changed) {
                        return objectMapper.valueToTree(variables);
                    }
                }
            } catch (JsonProcessingException e) {
                logger.error("Error processing JSON: " + e.getMessage(), e);
            }
        }
        return availableVariables;
    }

	private Object getValue(JsonNode paramNode, Map<String, Object> variables, String fieldName) {
		Object mappedValue = findMapping(fieldName, paramNode, variables);
		if (mappedValue == null) {
		    logger.error("Unable to find mapping for {}", fieldName);
		    throw new RuntimeException("Unable to find field mapping");
		}
		return mappedValue;
	}

    private Object findMapping(String fieldName, JsonNode mapType, Map<String, Object> mapFrom) {
        for (String key : mapFrom.keySet()) {
            if (!key.equalsIgnoreCase(fieldName)) {
                String mapperKey = fieldName.toLowerCase() + "_from_" + key.toLowerCase();
                JsonNode value = sqlAssistant.executeQueryByNameWithDynamicMapping(mapperKey.trim(), mapFrom.get(key));
                if (value != null) {
                    JsonTransformer transformer = new JsonTransformer();
                    Object val = transformer.getMatchingFieldValue(fieldName, mapType, value);
                    return val;
                }
            }else
            { 
            	JsonTransformer transformer = new JsonTransformer();
            	JsonNode value = objectMapper.valueToTree(mapFrom);
            	 Object val = transformer.getMatchingFieldValue(fieldName, mapType, value);
            	 if(val!=null)
                 return val;
            }
        }
        return null;
    }

    /**
     * Generates a response based on the execution context.
     *
     * @param ctx the {@link ExecutionContext} containing the query and
     * execution details.
     * @return a {@link ChatResponse} generated from the execution context.
     */
    public ChatResponse getResponse(ExecutionContext ctx) {
        Knowledge knowledge = ctx.getKnowledge();
        IStep step = getExecuter(knowledge);
        ChatResponse response ;
        if (step == null) {
        	response= new ChatResponse();
            response.setMessage("No knowledge found for the query");
        }
        else
        	 response =  step.getResponse(ctx);
        if (knowledge != null && knowledge.getCard() != null && response.getData() != null && !response.getData().isEmpty()) {
            response.setMessage(platformHelper.generateAdaptiveCardJson(response.getData().get(0), knowledge.getCard()));
        }
        response.setRequestId(ctx.getRequestId());
        response.setStatusCode(STATUS_SUCCESS);
        sessionManager.updateUserSession(ctx.getUserSession(), response);
        return response;
    }

    private Knowledge matchKnowledge(String question, JsonNode params) {
        try {
            // Fetch knowledge descriptions and convert to JSON string

            //ArrayNode knowledgeDesc = vector.Search(DefaultIndexTrakingProvider.csKnowledgeIndex, question,null);// 
            Map<String, String> knowledgeDesc = knowledgeBaserepo.getActiveKnowledgeDescriptions();
            String jsonString = objectMapper.writeValueAsString(knowledgeDesc);

            // Get response from OpenAI
            String response = openAI.evaluatePrompt(fileContentProvider.matchKnowledgePrompt, jsonString, question);

            // Return knowledge if response is valid
            if (response != null) {
                return knowledgeBaserepo.GetByName(response.trim());
            } else {
                String query = queryHandler.matchSelectQuery(question, params);
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

    /**
     * Retrieves the appropriate execution step based on the knowledge type.
     *
     * @param knowledge the {@link Knowledge} object containing the query type
     * and data.
     * @return the {@link IStep} implementation for executing the query.
     */
    public IStep getExecuter(Knowledge knowledge) {

        if (knowledge == null || knowledge.getType() == null) {
            logger.warn("Knowledge or its type is null");
            return null;
        }
        logger.info("getting executter for "+knowledge.getType());
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
                return restHelper;
            }
            case SHAREPOINT -> {
                return sharePointAssistant;
            }
            default -> {
                logger.warn("Unhandled QueryType: " + knowledge.getType());
                return null;
            }
        }
    }
}

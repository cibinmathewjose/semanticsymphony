package org.symphonykernel.ai;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.QueryType;
import org.symphonykernel.UserSession;
import org.symphonykernel.UserSessionStepDetails;
import org.symphonykernel.config.Constants;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.providers.FileContentProvider;
import org.symphonykernel.providers.SessionProvider;
import org.symphonykernel.steps.FileStep;
import org.symphonykernel.steps.GraphQLStep;
import org.symphonykernel.steps.PluginStep;
import org.symphonykernel.steps.RESTStep;
import org.symphonykernel.steps.RFCStep;
import org.symphonykernel.steps.SqlStep;
import org.symphonykernel.steps.Symphony;
import org.symphonykernel.steps.ToolStep;
import org.symphonykernel.steps.VelocityStep;
import org.symphonykernel.transformer.JsonTransformer;
import org.symphonykernel.transformer.PlatformHelper;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;

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
 * <li>{@link IAIClient} - Integration with OpenAI for prompt
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
 * <li>{@link #matchKnowledge(String, JsonNode)} - Matches a query to a
 * knowledge description or SQL query.</li>
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

    private static final class Status {
        static final String NONE = "NONE";
        static final String MODEL = "model";
        static final String SUCCESS = "SUCCESS";
        static final String FAILED = "FAILED";
        static final String FOLLOWUP = "FOLLOWUP";
        static final String PROCESSING = "PROCESSING";
    }

    @Value("${symphony.threadpool.size:25}")
    private int threadPoolSize;
    @Value("${symphony.knowledge.cache.ttl.ms:60000}")
    private long cacheTtlMs;

    private ExecutorService executorService;

    // Caching for knowledge descriptions (thread-safe)
    private static final Object cacheLock = new Object();    
    private static String knowledgeDescJsonCache = null;
    private static long knowledgeDescCacheTimestamp = 0;

    /**
     * Initializes the thread pool with the configured size. This method is
     * called after the bean is constructed.
     */
    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(threadPoolSize);
        logger.info("Initialized thread pool with size: {}", threadPoolSize);
    }

    /**
     * Cleans up resources by shutting down the thread pool. This method is
     * called before the bean is destroyed.
     */
    @PreDestroy
    public void cleanup() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    logger.warn("Thread pool forced shutdown after timeout");
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
                logger.warn("Thread pool shutdown interrupted", e);
            }
            logger.info("Thread pool shutdown completed");
        }
    }
    @Autowired
    TemplateResolver templateResolver;

    @Autowired
    IknowledgeBase knowledgeBaserepo;

    @Autowired
    PlatformHelper platformHelper;

    @Autowired
    IAIClient openAI;

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
    FileStep fileUrlHelper;

    @Autowired
    Optional<RFCStep> rfcStep;

    @Autowired
    PluginStep pluginStep;

    @Autowired
    ToolStep toolStep;
    
    @Autowired
    VelocityStep velocityTemplateEngine;

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
        //TODO: set model name from request if any
        ExecutionContext ctx = new ExecutionContext();
        ctx.setRequest(request);
         String payload=request.getPayload();
        if(payload!=null && !payload.isEmpty()&&!payload.equals(Status.NONE))
        {
            if(payload.contains("="))
            {
                payload=request.getPayloadParam(Status.MODEL);
            }
            ctx.setModelName(payload);
        }
        ctx.setUsersQuery(request.getQuery());
        ctx.setHttpHeaderProvider(request.getHeaderProvider());
        ChatHistory chatHistory = sessionManager.getChatHistory(request);
        ctx.setChatHistory(chatHistory);
        UserSession info = null;
        info = sessionManager.createUserSession(request);
        ctx.setUserSession(info);
        ctx.put("input", ctx.getVariables());
        ctx.put("userprompt", ctx.getUsersQuery()); 
        return ctx;
    }

    public static void registerParameterTranslation(String priorityParamName, String translateFromParamName) {
        parameterTranslationMap.put(priorityParamName, translateFromParamName);
    }

    public ExecutionContext loadContext(String requestId, String key) {
        ExecutionContext ctx = new ExecutionContext();
        ChatRequest req = new ChatRequest();
        req.setConversationId(requestId);
        req.setKey(key);
        ctx.setRequest(req);
        UserSession info = sessionManager.getRequest(requestId);
        ctx.setUserSession(info);
        return ctx;
    }

    /**
     * Identifies the intent of the user's query by matching it with knowledge
     * descriptions. Throws a RuntimeException if no matching knowledge is
     * found.
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
        if (knowledge == null) {
            throw new RuntimeException("No matching knowldge found");
        }
        logger.debug("Knowldge idetified as {}", knowledge.getName());
        ctx.setKnowledge(knowledge);
        UserSession s = ctx.getUserSession();
        if (s != null) {
            s.setStatus(Status.PROCESSING);
            s.setKnowldgeName(knowledge.getName());
            sessionManager.updateUserSession(s);
        }
        return ctx;

    }

    /**
     * Sets parameters for the query using OpenAI prompt evaluation. Updates the
     * request payload and variables in the execution context.
     *
     * @param ctx the {@link ExecutionContext} containing the query and
     * knowledge.
     * @return the updated {@link ExecutionContext} with parameters set.
     * @throws RuntimeException if the request object is not set in the context.
     */
    public ExecutionContext setParameters(ExecutionContext ctx) {
        ChatRequest request = ctx.getRequest();
        if (request == null) {
            throw new RuntimeException("Request object must be set in context");
        }
        Knowledge knowledge = ctx.getKnowledge();
        if (knowledge != null && knowledge.getParams() != null) {
            if (request.getPayload() != null && !Status.NONE.equals(request.getPayload())) {
                logger.warn("Ignore paylod : " + request.getPayload());
            } else {
                String prompt = fileContentProvider.prepareParamParserPrompt(knowledge.getParams(), request.getQuery());
                String params = openAI.evaluatePrompt(prompt);
                request.setPayload(params);
                logger.debug("payload identified as : " + params);
            }
        }
        ctx.setRequest(request);
        JsonNode node = request.getVariables();
        if (knowledge != null) {
            node = mapMissingVariables(node, knowledge.getParams());
        }        
        ctx.setVariables(node);
        logger.debug("Variables set as : {}", node);
        return ctx;
    }

    /**
     * Maps missing variables from the available variables using the provided
     * parameters . If the parameters are invalid JSON, the method skips
     * parameter parsing.
     *
     * @param availableVariables the existing variables to be updated.
     * @param params the JSON string containing the parameters to map.
     * @return a {@link JsonNode} with the updated variables.
     */
    JsonNode mapMissingVariables(JsonNode availableVariables, String params) {
        if (params != null && !params.isEmpty()) {
            try {
                JsonNode paramNode = objectMapper.readTree(params);
                if (paramNode.isArray()) {
                    paramNode = paramNode.get(0);
                }
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
                    return resolveValueConflictsByPriority(updatedArray,paramNode);
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
                        return resolveValueConflictsByPriority(objectMapper.valueToTree(variables),paramNode);
                    }
                }
            } catch (JsonProcessingException e) {
                logger.warn("Invalid JSON, skiping parameter parsing");
            }
        }
        return resolveValueConflictsByPriority(availableVariables,null);
    }

    private JsonNode resolveValueConflictsByPriority(JsonNode availableVariables, JsonNode paramNode) {
        if (parameterTranslationMap.isEmpty() || availableVariables == null || availableVariables.isEmpty()) {
            return availableVariables;
        }
        if (availableVariables.isArray() && availableVariables.size() > 1) {
            ArrayNode resultArray = objectMapper.createArrayNode();
            for (JsonNode item : availableVariables) {
                resultArray.add(resolveValueConflictsByPriorityForItem(item, paramNode));
            }
            return resultArray;
        } else {
            return resolveValueConflictsByPriorityForItem(availableVariables, paramNode);
        }
    }

    private JsonNode resolveValueConflictsByPriorityForItem(JsonNode availableVariables, JsonNode paramNode) throws IllegalArgumentException {
        Map<String, Object> variables = objectMapper.convertValue(availableVariables, Map.class);
        boolean changed = false;
        for (String fieldName : parameterTranslationMap.keySet()) {
            String translateFromParamName = parameterTranslationMap.get(fieldName);
            boolean hasKey = variables.keySet().stream()
                    .anyMatch(existingKey -> existingKey.equalsIgnoreCase(fieldName));           
            if (hasKey) {
                String mapperKey = fieldName.toLowerCase().trim() + "_from_" + translateFromParamName.toLowerCase().trim();
                JsonNode value = sqlAssistant.executeQueryByNameWithDynamicMapping(mapperKey, variables.get(translateFromParamName));
                if (value != null) {
                    JsonTransformer transformer = new JsonTransformer();
                    Object val = transformer.getMatchingFieldValue(fieldName, paramNode, value);
                    if(val!=null)
                     {
                        logger.info("Updated value for {} from {} to {} based on translation {}", fieldName, variables.get(fieldName), val, translateFromParamName);
                        variables.put(fieldName, val);
                        //variables.keySet().removeIf(existingKey -> existingKey.equalsIgnoreCase(mapperKey));
                        changed = true;
                     }
                     else
                        logger.info("Keeping value for {} as {} since translation is null for {}", fieldName, variables.get(fieldName),  translateFromParamName);
                }
            }           
        }
        if (changed) {
            return objectMapper.valueToTree(variables);
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
            } else {
                JsonTransformer transformer = new JsonTransformer();
                JsonNode value = objectMapper.valueToTree(mapFrom);
                Object val = transformer.getMatchingFieldValue(fieldName, mapType, value);
                if (val != null) {
                    return val;
                }
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
        ChatResponse response = null;
        if (ctx.isIsAsync()) {
            response = getAsyncResponse(ctx);
        } else {
            response = process(ctx);
        }
        return response;
    }

    private ChatResponse invalidRequestHandler(ExecutionContext ctx) {
        ChatResponse response;
        response = new ChatResponse();
        response.setMessage("No knowledge found for the query");
        response.setRequestId(ctx.getRequestId());
        response.setStatusCode(Status.FAILED);
        sessionManager.updateUserSession(ctx.getUserSession(), response);
        return response;
    }

    private ChatResponse getAsyncResponse(ExecutionContext ctx) {
        ChatResponse response;
        response = new ChatResponse();
        response.setMessage("Processing request");
        response.setRequestId(ctx.getRequestId());
        response.setStatusCode(Status.PROCESSING);
        executorService.submit(() -> {
            process(ctx);
        });
        return response;
    }

    private ChatResponse process(ExecutionContext ctx) {

        Knowledge knowledge = ctx.getKnowledge();
        IStep step = getExecuter(knowledge);
        ChatResponse response = null;
        if (step == null) {
            response = invalidRequestHandler(ctx);
        } else {
            try {
                MDC.put(Constants.LOGGER_TRACE_ID, ctx.getRequestId());
                logger.info("Processing request for requestId: {}", ctx.getRequestId());
                response = processRequest(ctx, knowledge, step);
                logger.info("Response {}", response.getMessage());
            } catch (Exception e) {
                logger.error("Error processing request for requestId: {}", ctx.getRequestId(), e);
                UserSession session = ctx.getUserSession();
                if (session != null) {
                    session.setBotResponse("An error occurred while processing your request: " + e.getMessage());
                    session.setStatus(Status.FAILED);
                    sessionManager.updateUserSession(session, null);
                }
            } finally {
                logger.info("request processing completed for requestId: {}", ctx.getRequestId());
                MDC.clear();
            }

        }
        return response;
    }
    public Flux<String> streamResponse(ExecutionContext ctx) {

		Knowledge knowledge = ctx.getKnowledge();
		IStep step = getExecuter(knowledge);
		if (step == null) {
			return Flux.just("No knowledge found for the query");
		} else {
			try {
				logger.info("Processing request for requestId: {}", ctx.getRequestId());
				return processRequestStream(ctx,  step);
			} catch (Exception e) {
				logger.error("Error processing request for requestId: {}", ctx.getRequestId(), e);
				UserSession session = ctx.getUserSession();
				if (session != null) {
					session.setBotResponse("An error occurred while processing your request: " + e.getMessage());
					session.setStatus(Status.FAILED);
					sessionManager.updateUserSession(session, null);
				}
				return Flux.just("An error occurred while processing your request: " + e.getMessage());
			} 

		}
	}

    public ChatResponse getFollowupResponse(ChatRequest request) {
        ChatResponse response = new ChatResponse();
        if (request == null || request.getQuery() == null || request.getSession() == null) {
            response.setMessage("Sorry, I am unable to process the question, please check the FAQ for more information about my capabilities.");
            return response;
        }
        String rId = sessionManager.getLastRequestId(request.getSession());
        if (rId == null || rId.isEmpty()) {
            response.setMessage("No previous session found for the given conversation id");
            return response;
        }

        return getFollowupResponse(rId, request.getQuery());

    }
    public Flux<String> streamFollowupResponse(ChatRequest request) {
		if (request == null || request.getQuery() == null || request.getSession() == null) {
			return Flux.just("Sorry, I am unable to process the question, please check the FAQ for more information about my capabilities.");
		}
		String rId = sessionManager.getLastRequestId(request.getSession());
		if (rId == null || rId.isEmpty()) {
			return Flux.just("No previous session found for the given conversation id");
		}

		return streamFollowupResponse(rId, request.getQuery());

	}

	 public Flux<String> streamFollowupResponse(String requestId, String query) {
		
		 if (requestId == null || requestId.isEmpty() || query == null || query.isEmpty()) {
			 	return Flux.just("Conversation id and followup question required");
	        }
	        ExecutionContext ctx = loadContext(requestId, Status.FOLLOWUP);

	        if (ctx == null || ctx.getUserSession() == null) {
	        	return Flux.just("No previous session found for the given conversation id");
	        }
	        UserSession s = ctx.getUserSession();
	        List<UserSessionStepDetails> steps = sessionManager.getRequestDetails(requestId);
	        if (!s.getStatus().equals(Status.SUCCESS) || steps == null || steps.isEmpty() || s.getKnowldgeName() == null) {
	        	return Flux.just("Sorry unable to process the followup question, please check the FAQ for more information about my capabilities.");
	        } else {
	        	sessionManager.saveRequestDetails(ctx.getRequestId(), Status.FOLLOWUP + "-" + query.hashCode(), "");
	        	return Flux.concat(
	        			Flux.just("Processing request"),
	        	        Flux.just("Status: Searching database..."),
	        	        processFollowup(query, ctx, s, steps) // This returns the actual Flux<String>
	        	    );
	        }	      
	       
	}

    /**
     * Retrieves the follow-up response for a given request ID and query.
     *
     * @param requestId the ID of the previous request.
     * @param query the follow-up question to process.
     * @return a {@link ChatResponse} containing the follow-up response or an
     * error message.
     */
    public ChatResponse getFollowupResponse(String requestId, String query) {
        ChatResponse response = new ChatResponse();
        if (requestId == null || requestId.isEmpty() || query == null || query.isEmpty()) {
            response.setMessage("Conversation id and followup question required");
            return response;
        }
        ExecutionContext ctx = loadContext(requestId, Status.FOLLOWUP);

        if (ctx == null || ctx.getUserSession() == null) {
            response.setMessage("No previous session found for the given conversation id");
            return response;
        }
        UserSession s = ctx.getUserSession();
        List<UserSessionStepDetails> steps = sessionManager.getRequestDetails(requestId);
        if (!s.getStatus().equals(Status.SUCCESS) || steps == null || steps.isEmpty() || s.getKnowldgeName() == null) {
            response.setMessage("Sorry unable to process the followup question, please check the FAQ for more information about my capabilities.");
        } else {

            response.setMessage("Processing request");
            response.setRequestId(ctx.getRequestId());
            response.setStatusCode(Status.PROCESSING);
            sessionManager.saveRequestDetails(ctx.getRequestId(), Status.FOLLOWUP + "-" + query.hashCode(), "");
            executorService.submit(() -> {
                processFollowup(query, response, ctx, s, steps);
            });

            return response;

        }
        response.setRequestId(ctx.getRequestId());
        response.setStatusCode(Status.SUCCESS);
        return response;
    }

    private void processFollowup(String query, ChatResponse response, ExecutionContext ctx, UserSession s,
            List<UserSessionStepDetails> steps) {
        MDC.put(Constants.LOGGER_TRACE_ID, ctx.getRequestId());
        try {
            logger.info("Processing follow-up request for requestId: {}", ctx.getRequestId());
            StringBuilder stepDetails = new StringBuilder();
            StringBuilder finalout = new StringBuilder();
            String finalStep = s.getKnowldgeName();

            build(steps, stepDetails, finalout, finalStep);
            String prompt = fileContentProvider.prepareFollowupPrompt(stepDetails.toString(), finalout.toString());
            String systemPrompt = templateResolver.resolvePlaceholders(prompt);
            String out = openAI.execute(new LLMRequest(systemPrompt, query, null, ctx.getModelName()));
            sessionManager.saveRequestDetails(ctx.getRequestId(), Status.FOLLOWUP + "-" + query.hashCode(), " Question : " + query + " Ans: " + out);
            response.setMessage(out);

        } finally {
            MDC.clear();
        }
    }

	private void build(List<UserSessionStepDetails> steps, StringBuilder stepDetails, StringBuilder finalout,
			String finalStep) {
		for (UserSessionStepDetails step : steps) {
		    String stepName = step.getStepName();
		    String stepData = step.getData();
		    String stepDescription = knowledgeBaserepo.getKnowledgeDescriptions(stepName);
		    if (stepData == null || stepData.isEmpty()) {
		        stepDescription = "";
		    }
		    if (stepName.equalsIgnoreCase(finalStep)) {

		        finalout.append("key: ").append(stepName).append("\n");
		        finalout.append("Description: ").append(stepDescription).append("\n");
		        finalout.append("Data: ").append(stepData).append("\n\n");
		    } else {
		        stepDetails.append("key: ").append(stepName).append("\n");
		        stepDetails.append("Description: ").append(stepDescription).append("\n");
		        stepDetails.append("Data: ").append(stepData).append("\n\n");
		    }
		}
	}
    private Flux<String> processFollowup(String query, ExecutionContext ctx, UserSession s,
			List<UserSessionStepDetails> steps) {
    	try {
            logger.info("Processing follow-up request for requestId: {}", ctx.getRequestId());
            StringBuilder stepDetails = new StringBuilder();
            StringBuilder finalout = new StringBuilder();
            String finalStep = s.getKnowldgeName();

            build(steps, stepDetails, finalout, finalStep);
            String prompt = fileContentProvider.prepareFollowupPrompt(stepDetails.toString(), finalout.toString());
            String systemPrompt = templateResolver.resolvePlaceholders(prompt);
         // 2. Use a local StringBuilder to capture the stream chunks
            StringBuilder responseAccumulator = new StringBuilder();
            return openAI.streamExecute(new LLMRequest(systemPrompt, query, null, ctx.getModelName())).doOnNext(responseAccumulator::append) // Capture each chunk as it flies by
                    .doFinally(signalType -> {
                        // 3. This runs only when the stream completes or errors out
                        String fullResponse = responseAccumulator.toString();
                        sessionManager.saveRequestDetails(
                            ctx.getRequestId(), 
                            Status.FOLLOWUP + "-" + query.hashCode(), 
                            " Question : " + query + " Ans: " + fullResponse
                        );
                        logger.info("Session saved for requestId: {}", ctx.getRequestId());
                    });
            } catch (Exception e) {
				logger.error("Error processing follow-up request for requestId: {}", ctx.getRequestId(), e);
				String out = "An error occurred while processing your follow-up question: " + e.getMessage();
				return Flux.just(out);
            }    			
    }

    /**
     * Retrieves the details of a request based on the request ID.
     *
     * @param requestId the ID of the request to retrieve details for.
     * @return a {@link ChatResponse} containing the request details or status.
     */
    public ChatResponse getAsyncResponse(String requestId) {
        ExecutionContext ctx = loadContext(requestId, "ASYNC_RESULT");
        ChatResponse response = new ChatResponse();

        // Maximum wait time in milliseconds (20 seconds)
        final long maxWaitTime = 20000;
        // Polling interval in milliseconds (2 seconds)
        final long pollingInterval = 2000;
        // Start time
        long startTime = System.currentTimeMillis();
        UserSession followupreqDetails = null;
        while (System.currentTimeMillis() - startTime < maxWaitTime) {

            UserSession reqDetails;
            if (followupreqDetails == null) {
                reqDetails = sessionManager.getRequest(ctx.getRequestId());

                if (reqDetails == null) {
                    response.setMessage("Invalid request id");
                    response.setRequestId(ctx.getRequestId());
                    response.setStatusCode(Status.FAILED);
                    return response;
                }
            } else {
                reqDetails = followupreqDetails;
            }

            if (Status.SUCCESS.equals(reqDetails.getStatus()) || Status.FAILED.equals(reqDetails.getStatus())) {

                UserSessionStepDetails followUps = sessionManager.getFollowUpDetails(ctx.getRequestId());
                if (followUps != null) {
                    if (followUps.getData() != null && !followUps.getData().isEmpty()) {
                        response.setMessage(followUps.getData());
                        response.setRequestId(ctx.getRequestId());
                        response.setStatusCode(Status.SUCCESS);
                        return response;
                    } else {
                        followupreqDetails = reqDetails;
                    }
                } else {
                    response.setMessage(reqDetails.getBotResponse());
                    if (reqDetails.getData() != null) {
                        try {
                            response.setData(objectMapper.readValue(reqDetails.getData(), ArrayNode.class));
                        } catch (Exception e) {
                            logger.error("Failed to parse data into ArrayNode", e);
                            response.setData(null);
                        }
                    }
                    response.setRequestId(ctx.getRequestId());
                    response.setStatusCode(reqDetails.getStatus());
                    return response;
                }
            }

            // Wait for polling interval before checking again
            try {
                Thread.sleep(pollingInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Thread interrupted while waiting for request completion", e);
                break;
            }
        }

        // If we get here, we've timed out
        UserSession reqDetails = sessionManager.getRequest(ctx.getRequestId());
        String createTime = reqDetails != null ? reqDetails.getCreateDt().toString() : "unknown time";
        response.setMessage("Request is still processing. Started at " + createTime);
        response.setRequestId(ctx.getRequestId());
        response.setStatusCode(Status.PROCESSING);
        return response;
    }

    private ChatResponse processRequest(ExecutionContext ctx, Knowledge knowledge, IStep step) {
        ChatResponse response;
        response = step.getResponse(ctx);
        if (knowledge != null && knowledge.getCard() != null && response.getData() != null && !response.getData().isEmpty()) {
            response.setMessage(platformHelper.generateAdaptiveCardJson(response.getData().get(0), knowledge.getCard()));
        }
        response.setRequestId(ctx.getRequestId());
        response.setStatusCode(Status.SUCCESS);
        sessionManager.updateUserSession(ctx.getUserSession(), response);
        return response;
    }
    private Flux<String> processRequestStream(ExecutionContext ctx, IStep step) {
    	
    	StringBuilder responseAccumulator = new StringBuilder();
        return step.getResponseStream(ctx).doOnNext(responseAccumulator::append) // Capture each chunk as it flies by
                .doFinally(signalType -> {
                    // 3. This runs only when the stream completes or errors out
                    String fullResponse = responseAccumulator.toString();
                    sessionManager.updateUserSession(ctx.getUserSession(), fullResponse, Status.SUCCESS);
                    logger.info("Session saved for requestId: {}", ctx.getRequestId());
                });
        
    }

    /**
     * Matches a query to a knowledge description or SQL query.
     *
     * @param question the user query to match
     * @param params additional parameters for matching
     * @return the matched {@link Knowledge} object, or {@code null} if no match
     * is found
     */
    private Knowledge matchKnowledge(String question, JsonNode params) {
        Map<String, String> knowledgeDescCache = new HashMap<>();
        try {
            if(question==null || question.isEmpty())
                return null;
            if(question.startsWith("Key:"))
            {
                String key = question.substring(4).trim();
                Knowledge knowledge = knowledgeBaserepo.GetByName(key);
                if(knowledge!=null)
                {
                    logger.debug("Direct knowledge match found for key {}", key);
                    return knowledge;
                }   
            }
            long now = System.currentTimeMillis();
            String jsonString;
            synchronized (cacheLock) {
                if (knowledgeDescJsonCache == null || now - knowledgeDescCacheTimestamp > cacheTtlMs) {
                    knowledgeDescCache = knowledgeBaserepo.getActiveKnowledgeDescriptions();
                    knowledgeDescJsonCache = objectMapper.writeValueAsString(knowledgeDescCache);
                    knowledgeDescCacheTimestamp = now;
                }
                jsonString = knowledgeDescJsonCache;
            }
            String prompt = fileContentProvider.prepareMatchKnowledgePrompt(jsonString, question, params.toString());
            String response = openAI.evaluatePrompt(prompt);
            int matchCount = -1;
            if(response != null && !response.isEmpty() && !Status.NONE.equalsIgnoreCase(response.trim()))
            {
                matchCount  = response.indexOf(',')+1;
            }
            if (matchCount>=0 && matchCount < 5) {
                Knowledge knowledge = null;
                if (matchCount > 0) {
                    String[] matches = response.trim().split(",");
                    for (String match : matches) {
                        knowledge = knowledgeBaserepo.GetByName(match.trim());
                        if (knowledge != null && knowledge.getParams() != null) {
                            prompt = fileContentProvider.prepareMatchParamsPrompt(knowledge.getParams(), params.toString(), question);
                            String isMatch = openAI.evaluatePrompt(prompt);
                            if ("YES".equalsIgnoreCase(isMatch)) {
                                logger.debug("Matching knowldge mapped {} from multiple matches", match);
                                return knowledge;
                            }
                            logger.debug("Knowldge not matching with context {}", match);
                        }
                    }
                    logger.debug("No matching knowldge found for the query based on parameters returning one match");
                    return knowledge;
                } else {
                    logger.debug("question matched with knowledge {}", response);
                    return knowledgeBaserepo.GetByName(response.trim());
                }
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
            logger.warn("Exception in matchKnowledge", e);
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
        logger.info("getting executter for " + knowledge.getType());
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
            case TOOL -> {
                return toolStep;
            }  
            case VELOCITY -> {
                return velocityTemplateEngine;
            }
            case REST -> {
                return restHelper;
            }
            case FILE -> {
                return fileUrlHelper;
            }
            case SHAREPOINT -> {
                throw new UnsupportedOperationException("SHAREPOINT QueryType is not implemented");
            }
             case RFC -> {
                if (rfcStep.isPresent()) {
                    return rfcStep.get();
                } else {
                    logger.warn("RFC Step is not enabled");
                    return null;
                }
            }
            default -> {
                logger.warn("Unhandled QueryType: " + knowledge.getType());
                return null;
            }
        }
    }

    private static Map<String, String> parameterTranslationMap = new HashMap<>();
}

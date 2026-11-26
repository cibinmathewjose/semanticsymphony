package org.symphonykernel.ai;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

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
import org.symphonykernel.config.SymphonyConfig;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.providers.FileContentProvider;
import org.symphonykernel.providers.SessionProvider;
import org.symphonykernel.agentic.AgenticPlanner;
import org.symphonykernel.steps.AuthenticationStep;
import org.symphonykernel.steps.DatabaseStep;
import org.symphonykernel.steps.DocumentStep;
import org.symphonykernel.steps.GraphQLStep;
import org.symphonykernel.steps.PluginStep;
import org.symphonykernel.steps.RESTStep;
import org.symphonykernel.steps.SqlStep;
import org.symphonykernel.steps.Symphony;
import org.symphonykernel.steps.ToolStep;
import org.symphonykernel.steps.VelocityStep;
import org.symphonykernel.steps.WebSearchStep;
import org.symphonykernel.steps.EmailStep;
import org.symphonykernel.steps.HumanInLoopStateStore;
import org.symphonykernel.steps.WorkflowCheckpoint;
import org.symphonykernel.steps.WorkflowCheckpointStore;
import org.symphonykernel.transformer.JsonTransformer;
import org.symphonykernel.transformer.PlatformHelper;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.ai.chat.messages.Message;

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
 * <!-- Removed invalid reference to getExecuter(Knowledge) as it does not exist in this class. -->
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
    private static final String STREAM_SCHEMA_VERSION = "1.0";
    private static final String FOLLOWUP_CAPABILITY_MESSAGE = "Sorry unable to process the followup question, please check the FAQ for more information about my capabilities.";
    private static final String FOLLOWUP_INPUT_REQUIRED_MESSAGE = "Conversation id and followup question required";

    private enum FollowupRoute {
        RESUME_PENDING_INPUT,
        NORMAL_FOLLOWUP,
        NONE
    }

    private static final class Status {
        static final String NONE = "NONE";
        static final String MODEL = "model";
        static final String SUCCESS = "SUCCESS";
        static final String FAILED = "FAILED";
        static final String FOLLOWUP = "FOLLOWUP";
        static final String PROCESSING = "PROCESSING";
		static final String ERROR = "ERROR";
    }

    @Autowired
    private SymphonyConfig symphonyConfig;

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
        executorService = Executors.newFixedThreadPool(symphonyConfig.getThreadPoolSize());
        logger.info("Initialized thread pool with size: {}", symphonyConfig.getThreadPoolSize());
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

    @Autowired(required = false)
    private org.symphonykernel.mcp.MCPClientService mcpClientService;

    @org.springframework.beans.factory.annotation.Value("${symphony.mcp.client.chat-fallback:true}")
    private boolean mcpChatFallback;

    private static final String MCP_DISCOVERY_KNOWLEDGE = "dynamic-mcp-discovery";

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    QueryHandler queryHandler;


    @Autowired
    SqlStep sqlAssistant;


    
    @Autowired
    private KnowledgeExecuterFactory knowledgeExecuterFactory;

    @Autowired
    SessionProvider sessionManager;
   
    @Autowired
    private FileContentProvider fileContentProvider;

    @Autowired(required = false)
    private InMemoryVectorIntentMatcher vectorMatcher;

    @Autowired
    private HumanInLoopStateStore humanInLoopStateStore;

    @Autowired
    private WorkflowCheckpointStore workflowCheckpointStore;

    /**
     * Creates a new execution context for a given chat request.
     *
     * @param request the {@link ChatRequest} containing user query and metadata
     * @return a new {@link ExecutionContext} initialized with the request data
     */
    public ExecutionContext createContext(ChatRequest request) {
        //TODO: set model name from request if any
        ExecutionContext ctx = new ExecutionContext();
        ctx.setRequest(request);
         String payload=request.getPayload();
        if(payload!=null && !payload.isBlank()&&!payload.equals(Status.NONE)
            && !payload.stripLeading().startsWith("{") && !payload.stripLeading().startsWith("["))
        {
            if(payload.contains("="))
            {
                payload=request.getPayloadParam(Status.MODEL);
            }
            ctx.setModelName(payload);			
            request.setPayload(null);
        }
        ctx.setUsersQuery(request.getQuery());
        ctx.setHttpHeaderProvider(request.getHeaderProvider());
        java.util.List<Message> chatHistory = sessionManager.getChatHistory(request);
        ctx.setChatHistory(chatHistory);
        UserSession info = null;
        info = sessionManager.createUserSession(request);
        ctx.setUserSession(info);
        ctx.put("userprompt", ctx.getUsersQuery()); 
        return ctx;
    }

    /**
     * Registers a parameter translation mapping.
     *
     * @param priorityParamName the priority parameter name
     * @param translateFromParamName the parameter name to translate from
     */
    public static void registerParameterTranslation(String priorityParamName, String translateFromParamName) {
        parameterTranslationMap.put(priorityParamName, translateFromParamName);
    }

    /**
     * Loads an execution context from a previous request.
     *
     * @param requestId the request ID
     * @param key the key for context loading
     * @return the loaded execution context
     */
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
     * Identifies the intent of the user's query by matching it with knowledge descriptions.
     * Throws a RuntimeException if no matching knowledge is found.
     *
     * @param ctx the {@link ExecutionContext} containing the user's query
     * @return the updated {@link ExecutionContext} with identified knowledge
     */
    public ExecutionContext identifyIntent(ExecutionContext ctx) {
        var request = ctx.getRequest();
        JsonNode params = null;
        if (request != null) {
            params = request.getVariables();
        }
        Knowledge knowledge = matchKnowledge(ctx.getUsersQuery(), params);
        if (knowledge == null) {
            knowledge = mcpDiscoveryKnowledge();
            if (knowledge == null) {
                throw new RuntimeException("No matching knowledge found");
            }
        }
        return assignKnowledge(ctx, knowledge);
    }

    private ExecutionContext assignKnowledge(ExecutionContext ctx, Knowledge knowledge) {
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
     * Processes a chat request end-to-end and returns the response.
     *
     * @param request the chat request
     * @return the chat response
     */
    public ChatResponse process(ChatRequest request) {      
        long start = System.nanoTime();
        logger.debug("Processing request: {}", request != null ? request.getQuery() : "Received null request");       
        if (request == null) {
            return new ChatResponse("Request is null");
        }
        String resumeRequestId = tryResumePendingInput(request);
        if (resumeRequestId != null) {
            return resumePendingRequest(request, resumeRequestId);
        }
        try {
            Knowledge configuredKnowledge = matchRegisteredOrDefaultKnowledge(request.getQuery(), request.getVariables());
            if (configuredKnowledge == null && resolveFollowupRoute(request) == FollowupRoute.NORMAL_FOLLOWUP) {
                ExecutionContext mcpContext = prepareMcpFollowup(request);
                return mcpContext == null ? getFollowupResponse(request) : getResponse(mcpContext);
            }
            ExecutionContext ctx = prepareMatchedContext(request, configuredKnowledge);
            ChatResponse response = getResponse(ctx);
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            logger.info("Processed request in {} ms", durationMs);
            return response;
        } catch (Exception ex) {
            throw new RuntimeException("Error processing request", ex);
        }
    }
    /**
     * Processes a chat request and returns a streaming response.
     *
     * @param request the chat request
     * @return a Flux stream of response strings
     */
    public Flux<String> streamProcess(ChatRequest request) {      
        if (request == null) {
            AtomicLong sequence = new AtomicLong(0);
            return Flux.just(streamEnvelope(null, request, "error", "request", "Request is null", null, sequence.incrementAndGet(), null));
        }
        String resumeRequestId = tryResumePendingInput(request);
        if (resumeRequestId != null) {
            return streamResumePendingRequest(request, resumeRequestId);
        }
        try {
            Knowledge configuredKnowledge = matchRegisteredOrDefaultKnowledge(request.getQuery(), request.getVariables());
            if (configuredKnowledge == null && resolveFollowupRoute(request) == FollowupRoute.NORMAL_FOLLOWUP) {
                ExecutionContext mcpContext = prepareMcpFollowup(request);
                return mcpContext == null ? streamFollowupResponse(request) : streamResponse(mcpContext);
            }
            ExecutionContext ctx = prepareMatchedContext(request, configuredKnowledge);
            return streamResponse(ctx);
        } catch (Exception ex) {
            throw new RuntimeException("Error processing request", ex);
        } 
    }

    private ExecutionContext prepareMcpFollowup(ChatRequest request) {
        UserSession previous = findExistingSessionQuietly(resolveFollowupRequestId(request));
        if (!belongsToRequest(previous, request) || previous.getKnowldgeName() == null) {
            return null;
        }
        Knowledge knowledge = MCP_DISCOVERY_KNOWLEDGE.equals(previous.getKnowldgeName())
            ? mcpDiscoveryKnowledge() : knowledgeBaserepo.GetByName(previous.getKnowldgeName());
        if (knowledge == null || knowledge.getType() != QueryType.MCP) {
            return null;
        }
        return setParameters(assignKnowledge(createContext(request), knowledge));
    }

    private boolean belongsToRequest(UserSession session, ChatRequest request) {
        return session != null && java.util.Objects.equals(request.getUser(), session.getUserId())
                && java.util.Objects.equals(request.getSession(), session.getSessionID());
    }

    private FollowupRoute resolveFollowupRoute(ChatRequest request) {
        if (hasContextInfo(request)) {
            String previousRequestId = resolveFollowupRequestId(request);
            UserSession previousSession = findExistingSessionQuietly(previousRequestId);
            if (!belongsToRequest(previousSession, request)) {
                return FollowupRoute.NONE;
            }
            if (shouldTreatAsAnswerToPreviousAiQuestion(previousSession.getBotResponse(), request.getQuery())) {
                return FollowupRoute.NORMAL_FOLLOWUP;
            }
            if (shouldHandleAsFollowupBalanced(request)) {
                return FollowupRoute.NORMAL_FOLLOWUP;
            }
        }
        return FollowupRoute.NONE;
    }

    private boolean shouldTreatAsAnswerToPreviousAiQuestion(String previousAnswer, String userQuery) {
        if (userQuery == null || userQuery.isBlank() || previousAnswer == null || previousAnswer.isBlank()) {
            return false;
        }
        String prompt = "Decide whether the user message is answering the assistant's previous question and should continue the same flow. "
                + "Return exactly FOLLOWUP or NEW."
                + "\\n\\nAssistant previous response:\\n" + previousAnswer
                + "\\n\\nUser message:\\n" + userQuery;
        try {
            String result = openAI.evaluatePrompt(prompt);
            return result != null && "FOLLOWUP".equalsIgnoreCase(result.trim());
        } catch (Exception ex) {
            logger.warn("Answer-to-previous-question AI check failed", ex);
            return false;
        }
    }
    /**
     * Sets parameters for the query using OpenAI prompt evaluation. Updates the request payload and variables in the execution context.
     *
     * @param ctx the {@link ExecutionContext} containing the query and knowledge
     * @return the updated {@link ExecutionContext} with parameters set
     * @throws RuntimeException if the request object is not set in the context
     */
    public ExecutionContext setParameters(ExecutionContext ctx) {
        ChatRequest request = ctx.getRequest();
        if (request == null) {
            throw new RuntimeException("Request object must be set in context");
        }
        Knowledge knowledge = ctx.getKnowledge();
        if (knowledge != null && knowledge.getType() == QueryType.MCP) {
            // Only explicit request variables can select a tool directly. Natural-language selection
            // must go through MCPStep's discovered catalog and read-only checks.
            ctx.setVariables(request.getVariables());
            return ctx;
        }
        if (knowledge != null && knowledge.getParams() != null) {
            if (request.getPayload() != null && !Status.NONE.equals(request.getPayload())) {
                logger.warn("Ignore paylod : " + request.getPayload());
            } else {
                String prompt = fileContentProvider.prepareParamParserPrompt(knowledge.getParams(), request.getQuery());
                String params = openAI.evaluatePrompt(prompt);
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
        logger.debug("Variables set as : {}", node);
        return ctx;
    }

    /**
     * Maps missing variables from the available variables using the provided parameters.
     * If the parameters are invalid JSON, the method skips parameter parsing.
     *
     * @param availableVariables the existing variables to be updated
     * @param params the JSON string containing the parameters to map
     * @return a {@link JsonNode} with the updated variables
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
                        Map<String, Object> variables = objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {});
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
                    Map<String, Object> variables = objectMapper.convertValue(availableVariables, new TypeReference<Map<String, Object>>() {});
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
        Map<String, Object> variables = objectMapper.convertValue(availableVariables, new TypeReference<Map<String, Object>>() {});
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
        if (variables != null && !variables.isEmpty()) {
                for (Map.Entry<String, Object> entry : variables.entrySet()) {
                    Object value = entry.getValue();
                    if (entry.getKey() != null
                            && entry.getKey().equalsIgnoreCase(fieldName)
                            && value != null
                            && (!(value instanceof String) || !((String) value).trim().isEmpty())) {
                        return value;
                    }
                }
            }
        Object mappedValue = findMapping(fieldName, paramNode, variables);
        if (mappedValue == null) {
            // Try to get default value from parameter configuration
            Object defaultValue = getDefaultValue(paramNode, fieldName);
            if (defaultValue != null) {
                logger.error("unable to do field mapping for '{}', assigning default value from parameter config: '{}'.", fieldName, defaultValue);
                mappedValue = defaultValue;
            } else {
                logger.error("unable to do field mapping for '{}', assigning empty string as default value.", fieldName);
                mappedValue = ""; // Assign empty string as fallback default
            }
        }
        return mappedValue;
    }

      /**
     * Gets the default value for a parameter field from the parameter definition.
     * The parameter definition should have a "default" property to specify the default value.
     * Example: {"fieldName": {"type": "string", "default": "defaultValue"}}
     *
     * @param paramNode the parameter definition JSON node
     * @param fieldName the name of the field to get the default value for
     * @return the default value, or null if not defined
     */
    private Object getDefaultValue(JsonNode paramNode, String fieldName) {
        if (paramNode == null || !paramNode.has(fieldName)) {
            return null;
        }
        JsonNode fieldDef = paramNode.get(fieldName);
        if (fieldDef == null || !fieldDef.has("default")) {
            return null;
        }
        JsonNode defaultNode = fieldDef.get("default");
        if (defaultNode == null || defaultNode.isNull()) {
            return null;
        }
        // Return the appropriate Java type based on the JSON node type
        if (defaultNode.isTextual()) {
            return defaultNode.asText();
        } else if (defaultNode.isNumber()) {
            if (defaultNode.isInt()) {
                return defaultNode.asInt();
            } else if (defaultNode.isLong()) {
                return defaultNode.asLong();
            } else {
                return defaultNode.asDouble();
            }
        } else if (defaultNode.isBoolean()) {
            return defaultNode.asBoolean();
        } else if (defaultNode.isArray() || defaultNode.isObject()) {
            return defaultNode;
        }
        return defaultNode.asText();
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
        IStep step =  knowledgeExecuterFactory.getExecuter(knowledge);
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
                response = new ChatResponse("Error processing request: " + e.getMessage());
                response.setRequestId(ctx.getRequestId());
                response.setStatusCode(Status.FAILED);
                UserSession session = ctx.getUserSession();
                if(session==null)
				{
                	session= this.sessionManager.getRequest(ctx.getRequestId());
				}
                if (session != null) {
                    session.setBotResponse("An error occurred while processing your request: " + e.getMessage());
                    session.setStatus(Status.FAILED);
                    sessionManager.updateUserSession(session, response);
                }
            } finally {
                logger.info("request processing completed for requestId: {}", ctx.getRequestId());
                MDC.clear();
            }

        }
        return response;
    }
    /**
     * Streams the response for a prepared execution context.
     *
     * @param ctx the execution context
     * @return a Flux emitting response chunks
     */
    public Flux<String> streamResponse(ExecutionContext ctx) {

		Knowledge knowledge = ctx.getKnowledge();
		IStep step =  knowledgeExecuterFactory.getExecuter(knowledge);
        AtomicLong sequence = new AtomicLong(0);
		if (step == null) {
            return Flux.just(streamEnvelope(ctx, ctx.getRequest(), "error", "intent", "No knowledge found for the query", null, sequence.incrementAndGet(), null));
		} else {
			try {
                logger.info("stream.start requestId={} conversationId={} stage=request model={}",
                        ctx.getRequestId(),
                        ctx.getRequest() != null ? ctx.getRequest().getConversationId() : "",
                        ctx.getModelName());
				return processRequestStream(ctx,  step);
			} catch (Exception e) {
				logger.error("Error processing request for requestId: {}", ctx.getRequestId(), e);
				UserSession session = ctx.getUserSession();
				if (session != null) {
					session.setBotResponse("An error occurred while processing your request: " + e.getMessage());
					session.setStatus(Status.FAILED);
					sessionManager.updateUserSession(session, null);
				}
                return Flux.just(streamEnvelope(ctx, ctx.getRequest(), "error", "request", "An error occurred while processing your request: " + e.getMessage(), null, sequence.incrementAndGet(), null));
			} 

		}
	}

    /**
     * Checks whether the request has context info from a previous conversation.
     *
     * @param request the chat request
     * @return true if context information exists
     */
    public boolean hasContextInfo(ChatRequest request) {
	    if (request == null || request.getQuery() == null) {
            return false;
        }
        String rId = resolveFollowupRequestId(request);
        if (rId == null || rId.isEmpty()) {
           return false;
        }
	    return findExistingSessionQuietly(rId) != null;
	}
    
    /**
     * Retrieves a follow-up response for the given request.
     *
     * @param request the chat request containing the follow-up query
     * @return a ChatResponse with the follow-up answer
     */
    public ChatResponse getFollowupResponse(ChatRequest request) {
    	String rId = resolveFollowupRequestId(request);
        return getFollowupResponse(rId, request != null ? request.getQuery() : null);

    }
    /**
     * Streams a follow-up response for the given request.
     *
     * @param request the chat request
     * @return a Flux emitting follow-up response chunks
     */
    public Flux<String> streamFollowupResponse(ChatRequest request) {
        if (request == null || request.getQuery() == null) {
			throw new RuntimeException("Sorry, I am unable to process the question, please check the FAQ for more information about my capabilities.");
		}
        String rId = resolveFollowupRequestId(request);
		if (rId == null || rId.isEmpty()) {
			throw new RuntimeException("No previous session found for the given conversation id");
		}

		return streamFollowupResponse(rId, request.getQuery());

	}

    /**
     * Streams a follow-up response for a given request ID and query.
     *
     * @param requestId the request ID
     * @param query the follow-up query
     * @return a Flux emitting follow-up response chunks
     */
	 public Flux<String> streamFollowupResponse(String requestId, String query) {
         String normalizedQuery = normalizeFollowupQuery(query);
         if (requestId == null || requestId.isEmpty() || normalizedQuery == null || normalizedQuery.isEmpty()) {
             	throw new RuntimeException(FOLLOWUP_INPUT_REQUIRED_MESSAGE);
	        }
	        ExecutionContext ctx = loadContext(requestId, Status.FOLLOWUP);

	        if (ctx == null || ctx.getUserSession() == null) {
	        	throw new RuntimeException("No previous session found for the given conversation id");
	        }
	        UserSession s = ctx.getUserSession();
	        List<UserSessionStepDetails> steps = sessionManager.getRequestDetails(requestId);
	        if (!s.getStatus().equals(Status.SUCCESS) || steps == null || steps.isEmpty() || s.getKnowldgeName() == null) {
            	throw new RuntimeException(FOLLOWUP_CAPABILITY_MESSAGE);
	        } else {
            	FollowupContextData followupContext = buildFollowupContext(s, steps);
            	String clarification = getClarificationIfNeeded(normalizedQuery, followupContext, ctx);
            	sessionManager.saveRequestDetails(ctx.getRequestId(), Status.FOLLOWUP + "-" + normalizedQuery.hashCode(), "");
            	AtomicLong sequence = new AtomicLong(0);
            	logger.info("stream.start requestId={} conversationId={} stage=followup model={}",
            			ctx.getRequestId(), requestId, ctx.getModelName());
            	if (clarification != null) {
            		sessionManager.saveRequestDetails(
            				ctx.getRequestId(),
            				Status.FOLLOWUP + "-" + normalizedQuery.hashCode(),
            				" Question : " + normalizedQuery + " Ans: " + clarification);
            		sessionManager.updateUserSession(ctx.getUserSession(), clarification, Status.SUCCESS);
            		return Flux.concat(
            				Flux.just(streamEnvelope(ctx, ctx.getRequest(), "status", "followup", "Processing request", null, sequence.incrementAndGet(), null)),
            				Flux.just(streamEnvelope(ctx, ctx.getRequest(), "status", "followup", "Need more details", null, sequence.incrementAndGet(), null)),
            				Flux.just(streamEnvelope(ctx, ctx.getRequest(), "content", "followup", "", clarification, sequence.incrementAndGet(), null))
            		);
            	}
	        	return Flux.concat(
            			Flux.just(streamEnvelope(ctx, ctx.getRequest(), "status", "followup", "Processing request", null, sequence.incrementAndGet(), null)),
            	        Flux.just(streamEnvelope(ctx, ctx.getRequest(), "status", "followup", "Status: Searching database...", null, sequence.incrementAndGet(), null)),
            	        processFollowup(normalizedQuery, ctx, followupContext, sequence)
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
        String normalizedQuery = normalizeFollowupQuery(query);
        if (requestId == null || requestId.isEmpty() || normalizedQuery == null || normalizedQuery.isEmpty()) {
            response.setMessage(FOLLOWUP_INPUT_REQUIRED_MESSAGE);
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
            response.setMessage(FOLLOWUP_CAPABILITY_MESSAGE);
        } else {
            FollowupContextData followupContext = buildFollowupContext(s, steps);
            String clarification = getClarificationIfNeeded(normalizedQuery, followupContext, ctx);
            if (clarification != null) {
                sessionManager.saveRequestDetails(
                        ctx.getRequestId(),
                        Status.FOLLOWUP + "-" + normalizedQuery.hashCode(),
                        " Question : " + normalizedQuery + " Ans: " + clarification);
                sessionManager.updateUserSession(ctx.getUserSession(), clarification, Status.SUCCESS);
                response.setMessage(clarification);
                response.setRequestId(ctx.getRequestId());
                response.setStatusCode(Status.SUCCESS);
                return response;
            }

            response.setMessage("Processing request");
            response.setRequestId(ctx.getRequestId());
            response.setStatusCode(Status.PROCESSING);
            executorService.submit(() -> {
                updateSessionAndPrompt(normalizedQuery, ctx, followupContext, response);
            });

            return response;

        }
        response.setRequestId(ctx.getRequestId());
        response.setStatusCode(Status.SUCCESS);
        return response;
    }

    
    private void updateSessionAndPrompt(String query, ExecutionContext ctx, FollowupContextData followupContext, ChatResponse response) {
        sessionManager.saveRequestDetails(ctx.getRequestId(), Status.FOLLOWUP + "-" + query.hashCode(), "");
        String prompt = fileContentProvider.prepareFollowupPrompt(followupContext.stepDetails(), followupContext.finalOutput());
        String systemPrompt = templateResolver.resolvePlaceholders(prompt);
        String out = openAI.execute(new LLMRequest(systemPrompt, query, null, ctx.getModelName()));
        sessionManager.saveRequestDetails(ctx.getRequestId(), Status.FOLLOWUP + "-" + query.hashCode(), " Question : " + query + " Ans: " + out);
        sessionManager.updateUserSession(ctx.getUserSession(), out, Status.SUCCESS);
        response.setMessage(out);
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
    private Flux<String> processFollowup(String query, ExecutionContext ctx, FollowupContextData followupContext, AtomicLong sequence) {
    	try {
            logger.info("Processing follow-up request for requestId: {}", ctx.getRequestId());
            String prompt = fileContentProvider.prepareFollowupPrompt(followupContext.stepDetails(), followupContext.finalOutput());
            String systemPrompt = templateResolver.resolvePlaceholders(prompt);
         // 2. Use a local StringBuilder to capture the stream chunks
            StringBuilder responseAccumulator = new StringBuilder();
                return openAI.streamExecute(new LLMRequest(systemPrompt, query, null, ctx.getModelName()))
                    .map(chunk -> streamEnvelope(ctx, ctx.getRequest(), "content", "followup", "", chunk, sequence.incrementAndGet(), null))
                    .doOnNext(chunk -> responseAccumulator.append(extractContentFromEnvelope(chunk)))
                    .doFinally(signalType -> {
                        // 3. This runs only when the stream completes or errors out
                        String fullResponse = responseAccumulator.toString();
                        sessionManager.saveRequestDetails(
                            ctx.getRequestId(), 
                            Status.FOLLOWUP + "-" + query.hashCode(), 
                            " Question : " + query + " Ans: " + fullResponse
                        );
                        sessionManager.updateUserSession(ctx.getUserSession(), fullResponse, Status.SUCCESS);
                    logger.info("stream.complete requestId={} conversationId={} stage=followup signal={}",
                        ctx.getRequestId(),
                        ctx.getRequest() != null ? ctx.getRequest().getConversationId() : "",
                        signalType);
                    });
            } catch (Exception e) {
				logger.error("Error processing follow-up request for requestId: {}", ctx.getRequestId(), e);
				String out = "An error occurred while processing your follow-up question: " + e.getMessage();
        				return Flux.just(streamEnvelope(ctx, ctx.getRequest(), "error", "followup", out, null, sequence.incrementAndGet(), null));
            }    			
    }

    private String resolveFollowupRequestId(ChatRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getConversationId() != null && !request.getConversationId().isBlank()) {
            String conversationId = request.getConversationId();
            if (findExistingSessionQuietly(conversationId) != null) {
                return conversationId;
            }
        }
        return sessionManager.getLastRequestId(request.getSession());
    }

    private String tryResumePendingInput(ChatRequest request) {
        if (request == null) {
            return null;
        }
        String query = request.getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }

        String sessionId = request.getSession();
        String pendingRequestId = humanInLoopStateStore.getPendingRequestIdBySession(sessionId, request.getUser());
        if (pendingRequestId != null && !pendingRequestId.isBlank()
            && belongsToRequest(findExistingSessionQuietly(pendingRequestId), request)) {
            return pendingRequestId;
        }

        String conversationId = request.getConversationId();
        if (conversationId != null && !conversationId.isBlank() && humanInLoopStateStore.isAwaitingInput(conversationId)
            && belongsToRequest(findExistingSessionQuietly(conversationId), request)) {
            return conversationId;
        }
        return null;
    }

    private ChatResponse buildResumeUnavailableResponse(String requestId) {
        ChatResponse response = new ChatResponse();
        response.setRequestId(requestId);
        response.setStatusCode(Status.ERROR);
        response.setMessage("Approval could not be resumed. It may not be ready, may have expired, or may already have been handled.");
        return response;
    }

    /**
     * Resumes a paused workflow by request ID, optionally providing user input for
     * the pending Human-In-Loop step.
     *
     * @param pausedRequestId paused workflow request ID
     * @param userInput optional user input to apply before resuming
     * @return resumed workflow response, or an error response if no paused workflow exists
     */
    public ChatResponse resumePausedWorkflow(String pausedRequestId, String userInput) {
        if (pausedRequestId == null || pausedRequestId.isBlank()) {
            ChatResponse response = new ChatResponse();
            response.setStatusCode(Status.ERROR);
            response.setMessage("requestId is required");
            return response;
        }

        ExecutionContext ctx = buildResumeExecutionContext(null, pausedRequestId, userInput);
        if (ctx == null) {
            ChatResponse response = new ChatResponse();
            response.setRequestId(pausedRequestId);
            response.setStatusCode(Status.ERROR);
            response.setMessage("No paused workflow found for requestId");
            return response;
        }
        return process(ctx);
    }

    private ChatResponse resumePendingRequest(ChatRequest request, String pausedRequestId) {
        ExecutionContext ctx = buildResumeExecutionContext(request, pausedRequestId, request.getQuery());
        if (ctx == null) {
            return buildResumeUnavailableResponse(pausedRequestId);
        }
        return process(ctx);
    }

    private Flux<String> streamResumePendingRequest(ChatRequest request, String pausedRequestId) {
        ExecutionContext ctx = buildResumeExecutionContext(request, pausedRequestId, request.getQuery());
        if (ctx == null) {
            AtomicLong sequence = new AtomicLong(0);
                return Flux.just(streamEnvelope(null, request, "error", "resume",
                    buildResumeUnavailableResponse(pausedRequestId).getMessage(), null, sequence.incrementAndGet(), null));
        }
        return streamResponse(ctx);
    }

    private ExecutionContext buildResumeExecutionContext(ChatRequest request, String pausedRequestId, String answer) {
        if (pausedRequestId == null || pausedRequestId.isBlank()) {
            return null;
        }
        WorkflowCheckpoint checkpoint = workflowCheckpointStore.load(pausedRequestId);
        if (checkpoint == null || checkpoint.getKnowledgeName() == null || checkpoint.getKnowledgeName().isBlank()) {
            return null;
        }

        ExecutionContext ctx = loadContext(pausedRequestId, Status.PROCESSING);
        if (ctx == null || ctx.getUserSession() == null) {
            return null;
        }
        if (request != null && !belongsToRequest(ctx.getUserSession(), request)) {
            return null;
        }

        Knowledge knowledge = knowledgeBaserepo.GetByName(checkpoint.getKnowledgeName());
        if (knowledge == null) {
            return null;
        }

        String resumedInput = humanInLoopStateStore.claimAnswer(checkpoint, answer);
        if (resumedInput == null || resumedInput.isBlank()) {
            return null;
        }

        ChatRequest resumeRequest = ctx.getRequest();
        if (resumeRequest != null) {
            resumeRequest.setSession(ctx.getUserSession().getSessionID());
            resumeRequest.setUser(ctx.getUserSession().getUserId());
        }
        if (request != null && resumeRequest != null) {
            resumeRequest.setHeaderProvider(request.getHeaderProvider());
            ctx.setHttpHeaderProvider(request.getHeaderProvider());
        }

        ctx.setKnowledge(knowledge);
        ctx.setModelName(checkpoint.getModelName());
        ctx.setUsersQuery(ctx.getUserSession().getUserInput());
        if (resumeRequest != null) {
            resumeRequest.setQuery(ctx.getUsersQuery());
        }
        ctx.getResolvedValues().clear();
        if (checkpoint.getResolvedValues() != null) {
            ctx.getResolvedValues().putAll(checkpoint.getResolvedValues());
        }
        ctx.setVariables(ctx.getResolvedValues().getOrDefault("input", objectMapper.createObjectNode()));
        if (checkpoint.getWaitingStepKey() != null) {
            ctx.put(checkpoint.getWaitingStepKey(), resumedInput);
        }

        workflowCheckpointStore.delete(pausedRequestId);
        humanInLoopStateStore.clearPending(pausedRequestId);

        UserSession session = ctx.getUserSession();
        session.setStatus(Status.PROCESSING);
        sessionManager.updateUserSession(session);
        return ctx;
    }

    private boolean shouldHandleAsFollowupBalanced(ChatRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return false;
        }
        String previousRequestId = resolveFollowupRequestId(request);
        if (previousRequestId == null || previousRequestId.isBlank()) {
            return false;
        }
        UserSession previousSession = findExistingSessionQuietly(previousRequestId);
        if (previousSession == null || previousSession.getBotResponse() == null || previousSession.getBotResponse().isBlank()) {
            return false;
        }

        String classifierPrompt = "Classify if the user query is a follow-up to the previous assistant answer. "
                + "Use BALANCED default: avoid false positives and false negatives. "
                + "Return exactly FOLLOWUP or NEW."
                + "\\n\\nPrevious answer:\\n" + previousSession.getBotResponse()
                + "\\n\\nUser query:\\n" + request.getQuery();
        try {
            String result = openAI.evaluatePrompt(classifierPrompt);
            String normalized = result == null ? "" : result.trim();
            if ("FOLLOWUP".equalsIgnoreCase(normalized)) {
                return true;
            }
            return shouldHandleAsFollowupByAiReview(previousSession.getBotResponse(), request.getQuery());
        } catch (Exception ex) {
            logger.warn("Follow-up classification failed. Falling back to secondary AI review", ex);
            return shouldHandleAsFollowupByAiReview(previousSession.getBotResponse(), request.getQuery());
        }
    }

    private boolean shouldHandleAsFollowupByAiReview(String previousAnswer, String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return false;
        }

        String reviewPrompt = "Decide whether the user query should be handled as a FOLLOWUP to the previous assistant answer. "
                + "A FOLLOWUP can be implicit (refers to previous rows/results/entities without restating full context). "
                + "Return exactly FOLLOWUP or NEW."
                + "\\n\\nPrevious answer:\\n" + (previousAnswer == null ? "" : previousAnswer)
                + "\\n\\nUser query:\\n" + userQuery;
        try {
            String review = openAI.evaluatePrompt(reviewPrompt);
            return review != null && "FOLLOWUP".equalsIgnoreCase(review.trim());
        } catch (Exception ex) {
            logger.warn("Secondary AI follow-up review failed", ex);
            return false;
        }
    }

    private UserSession findExistingSessionQuietly(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        try {
            return sessionManager.getRequest(requestId);
        } catch (RuntimeException ex) {
            logger.debug("No existing session found for requestId={} while evaluating follow-up route", requestId);
            return null;
        }
    }

    private String normalizeFollowupQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.replaceAll("\\s+", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private FollowupContextData buildFollowupContext(UserSession session, List<UserSessionStepDetails> steps) {
        StringBuilder stepDetails = new StringBuilder();
        StringBuilder finalout = new StringBuilder();
        build(steps, stepDetails, finalout, session.getKnowldgeName());
        return new FollowupContextData(stepDetails.toString(), finalout.toString());
    }

    private String getClarificationIfNeeded(String query, FollowupContextData followupContext, ExecutionContext ctx) {
        if (query == null || query.isBlank()) {
            return FOLLOWUP_INPUT_REQUIRED_MESSAGE;
        }

        String shortQuery = query.trim();
        if (shortQuery.length() < 2) {
            return "Could you please provide a bit more detail so I can continue with the follow-up?";
        }

        if (hasPredefinedKnowledgeMatch(shortQuery, ctx)) {
            logger.info("Skipping clarification because query has predefined knowledge match: {}", shortQuery);
            return null;
        }

        String validationPrompt = "You are validating if a user follow-up question is specific enough to answer from prior context."
                + " Return only one line in one of these formats:"
                + " YES"
                + " NO|<single concise clarification question>"
                + "\n\nPrevious final output:\n" + followupContext.finalOutput()
                + "\n\nPrevious step context:\n" + followupContext.stepDetails()
                + "\n\nUser follow-up:\n" + shortQuery;

        try {
            String validation = openAI.execute(new LLMRequest(validationPrompt, shortQuery, null, ctx.getModelName()));
            if (validation == null || validation.isBlank()) {
                return null;
            }
            String result = validation.trim();
            if ("YES".equalsIgnoreCase(result)) {
                return null;
            }
            if (result.toUpperCase().startsWith("NO|")) {
                String clarification = result.substring(3).trim();
                if (!clarification.isEmpty()) {
                    return clarification;
                }
            }
            if (result.toUpperCase().startsWith("NO")) {
                return "Can you clarify what specific field, period, or entity you want me to focus on?";
            }
            return null;
        } catch (Exception ex) {
            logger.warn("Follow-up validation failed; continuing without clarification gating", ex);
            return null;
        }
    }

    private boolean hasPredefinedKnowledgeMatch(String query, ExecutionContext ctx) {
        try {
            JsonNode params = null;
            if (ctx != null && ctx.getRequest() != null) {
                params = ctx.getRequest().getVariables();
            }
            Knowledge knowledge = matchKnowledge(query, params);
            if (knowledge == null) {
                return false;
            }
            if (QueryType.AGENTIC.equals(knowledge.getType()) || QueryType.SQL.equals(knowledge.getType())) {
                return false;
            }
            return knowledge.getName() != null && !knowledge.getName().isBlank();
        } catch (Exception ex) {
            logger.debug("Predefined knowledge pre-check failed; continuing with clarification logic", ex);
            return false;
        }
    }

    private record FollowupContextData(String stepDetails, String finalOutput) {
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
    /**
     * Updates the user session for a given request.
     *
     * @param requestId the request ID
     * @param response the chat response to update with
     */
    public void updateUserSession(String requestId, ChatResponse response) {
    	UserSession reqDetails = sessionManager.getRequest(requestId);
    	if(reqDetails!=null)
    		sessionManager.updateUserSession(reqDetails, response);
    }
    private ChatResponse processRequest(ExecutionContext ctx, Knowledge knowledge, IStep step) {
        ChatResponse response;
        response = step.getResponse(ctx);
        if (knowledge != null && knowledge.getCard() != null && response.getData() != null && !response.getData().isEmpty()) {
            response.setMessage(platformHelper.generateAdaptiveCardJson(response.getData().get(0), knowledge.getCard()));
        }
        response.setRequestId(ctx.getRequestId());
        if (isAwaitingInputResponse(response)) {
            response.setStatusCode(Status.PROCESSING);
        } else if (knowledge != null && QueryType.AGENTIC.equals(knowledge.getType())
                && "ERROR".equalsIgnoreCase(response.getStatusCode())) {
            response.setStatusCode(Status.FAILED);
        } else {
            response.setStatusCode(Status.SUCCESS);
        }
        sessionManager.updateUserSession(ctx.getUserSession(), response);
        return response;
    }
    private Flux<String> processRequestStream(ExecutionContext ctx, IStep step) {
    	
		AtomicLong sequence = new AtomicLong(0);
    	StringBuilder responseAccumulator = new StringBuilder();
		final boolean[] awaitingInput = new boolean[] { false };
        final boolean[] failed = new boolean[] { false };
	    return step.getResponseStream(ctx)
	    		.map(chunk -> normalizeStreamChunk(ctx, chunk, sequence))
	    		.doOnNext(chunk -> {
	    			responseAccumulator.append(extractContentFromEnvelope(chunk));
	    			if (isAwaitingInputEnvelope(chunk)) {
	    				awaitingInput[0] = true;
	    			}
                    if (isAgenticErrorEnvelope(chunk)) {
                        failed[0] = true;
                    }
	    		})
                .doFinally(signalType -> {
                    // 3. This runs only when the stream completes or errors out
                    String fullResponse = responseAccumulator.toString();
                    sessionManager.updateUserSession(ctx.getUserSession(), fullResponse,
                            failed[0] ? Status.FAILED : awaitingInput[0] ? Status.PROCESSING : Status.SUCCESS);
                    logger.info("stream.complete requestId={} conversationId={} stage=request signal={}",
                            ctx.getRequestId(),
                            ctx.getRequest() != null ? ctx.getRequest().getConversationId() : "",
                            signalType);
                });
        
    }

    private String normalizeStreamChunk(ExecutionContext ctx, String chunk, AtomicLong sequence) {
        if (isStreamEnvelope(chunk)) {
            return chunk;
        }
        if (chunk != null) {
            String trimmed = chunk.trim();
            if ("Generating output:".equalsIgnoreCase(trimmed)) {
                return streamEnvelope(ctx, ctx.getRequest(), "status", "response", "Generating output...", null,
                        sequence.incrementAndGet(), null);
            }
            if ("Gathering information...".equalsIgnoreCase(trimmed)) {
                return streamEnvelope(ctx, ctx.getRequest(), "status", "response", "Gathering information...", null,
                        sequence.incrementAndGet(), null);
            }
        }
        return streamEnvelope(ctx, ctx.getRequest(), "content", "response", "", chunk, sequence.incrementAndGet(), null);
    }

    private String streamEnvelope(
            ExecutionContext ctx,
            ChatRequest request,
            String eventType,
            String stage,
            String message,
            String content,
            long sequence,
            Long elapsedMs) {
        var node = objectMapper.createObjectNode();
        node.put("schemaVersion", STREAM_SCHEMA_VERSION);
        node.put("eventType", eventType == null ? "status" : eventType);
        node.put("stage", stage == null ? "unknown" : stage);
        node.put("sequence", sequence);
        node.put("timestamp", java.time.Instant.now().toString());
        node.put("source", getClass().getSimpleName());
        node.put("requestId", ctx != null ? ctx.getRequestId() : "");
        String conversationId = request != null ? request.getConversationId() : "";
        node.put("conversationId", conversationId == null ? "" : conversationId);
        node.put("message", message == null ? "" : message);
        if (content != null) {
            node.put("content", content);
        }
        if (elapsedMs != null) {
            node.put("elapsedMs", elapsedMs);
        }
        return node.toString();
    }

    private boolean isStreamEnvelope(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(chunk);
            return node.isObject() && node.has("schemaVersion") && node.has("eventType") && node.has("stage");
        } catch (Exception ex) {
            return false;
        }
    }

    private String extractContentFromEnvelope(String chunk) {
        if (!isStreamEnvelope(chunk)) {
            return chunk;
        }
        try {
            JsonNode node = objectMapper.readTree(chunk);
            if (!"content".equalsIgnoreCase(node.path("eventType").asText())) {
                return "";
            }
            return node.path("content").asText("");
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean isAwaitingInputResponse(ChatResponse response) {
        if (response == null) {
            return false;
        }
        if (Status.PROCESSING.equalsIgnoreCase(response.getStatusCode())) {
            return true;
        }
        ArrayNode data = response.getData();
        if (data == null || data.isEmpty()) {
            return false;
        }
        JsonNode first = data.get(0);
        return first != null && first.isObject()
                && "AWAITING_INPUT".equalsIgnoreCase(first.path("status").asText());
    }

    private boolean isAwaitingInputEnvelope(String chunk) {
        if (!isStreamEnvelope(chunk)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(chunk);
            if (!"human_loop".equalsIgnoreCase(node.path("stage").asText())) {
                return false;
            }
            String content = node.path("content").asText("");
            if (content == null || content.isBlank()) {
                return false;
            }
            JsonNode payload = objectMapper.readTree(content);
            return "AWAITING_INPUT".equalsIgnoreCase(payload.path("status").asText());
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isAgenticErrorEnvelope(String chunk) {
        try {
            JsonNode node = objectMapper.readTree(chunk);
            return "error".equals(node.path("eventType").asText()) && "agentic".equals(node.path("stage").asText());
        } catch (JsonProcessingException ex) {
            return false;
        }
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
        Knowledge matched = matchRegisteredOrDefaultKnowledge(question, params);
        return matched != null ? matched : matchFallbackKnowledge(question, params);
    }

    private Knowledge matchRegisteredOrDefaultKnowledge(String question, JsonNode params) {
        if (question == null || question.isBlank()) {
            return null;
        }
        Knowledge matched = matchRegisteredKnowledge(question, params);
        if (matched != null) {
            return matched;
        }
        var defaults = symphonyConfig.getDefaultKnowledge();
        return defaults.isEmpty() ? null : AgenticPlanner.defaultKnowledge(defaults);
    }

    private Knowledge matchFallbackKnowledge(String question, JsonNode params) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String query = queryHandler.matchSelectQuery(question, params);
        if (query != null) {
            Knowledge knowledge = new Knowledge();
            knowledge.setType(QueryType.SQL);
            knowledge.setData(query);
            return knowledge;
        }
        Knowledge mcpKnowledge = mcpDiscoveryKnowledge();
        if (mcpKnowledge != null || !symphonyConfig.isAutonomous()) {
            return mcpKnowledge;
        }
        Knowledge agenticKnowledge = new Knowledge();
        agenticKnowledge.setType(QueryType.AGENTIC);
        agenticKnowledge.setName("dynamic-agentic");
        agenticKnowledge.setSystemPrompt("Find the appropriate agents and approach to handle the user query. If no agent is suitable, respond with 'Unable to handle the request'.");
        return agenticKnowledge;
    }

    private Knowledge matchRegisteredKnowledge(String question, JsonNode params) {
        Map<String, String> knowledgeDescCache = new HashMap<>();
        String parameters = params == null ? "{}" : params.toString();
        try {
            if(question==null || question.isBlank())
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
            boolean cacheRefreshed = false;
            synchronized (cacheLock) {
                if (knowledgeDescJsonCache == null || now - knowledgeDescCacheTimestamp > symphonyConfig.getCacheTtlMs()) {
                    knowledgeDescCache = knowledgeBaserepo.getActiveKnowledgeDescriptions();
                    knowledgeDescJsonCache = objectMapper.writeValueAsString(knowledgeDescCache);
                    knowledgeDescCacheTimestamp = now;
                    cacheRefreshed = true;
                }
                jsonString = knowledgeDescJsonCache;
            }
            // Refresh vector index when knowledge cache refreshes
            if (cacheRefreshed && vectorMatcher != null) {
                vectorMatcher.refreshIndex(knowledgeDescCache);
            }
            // Try vector-based intent matching first (avoids LLM call)
            if (vectorMatcher != null) {
                List<InMemoryVectorIntentMatcher.MatchResult> vectorMatches = vectorMatcher.findMatches(question);
                if (!vectorMatches.isEmpty()) {
                    for (InMemoryVectorIntentMatcher.MatchResult match : vectorMatches) {
                        Knowledge knowledge = knowledgeBaserepo.GetByName(match.getKnowledgeName());
                        if (knowledge != null) {
                            if (vectorMatches.size() > 1 && knowledge.getParams() != null && params != null) {
                                String matchPrompt = fileContentProvider.prepareMatchParamsPrompt(
                                        knowledge.getParams(), params.toString(), question);
                                String isMatch = openAI.evaluatePrompt(matchPrompt);
                                if ("YES".equalsIgnoreCase(isMatch)) {
                                    logger.info("Vector intent match validated: {} (score: {})",
                                            match.getKnowledgeName(), String.format("%.3f", match.getScore()));
                                    return knowledge;
                                }
                            } else {
                                logger.info("Vector intent match: {} (score: {})",
                                        match.getKnowledgeName(), String.format("%.3f", match.getScore()));
                                return knowledge;
                            }
                        }
                    }
                    logger.debug("Vector matches not validated against params, falling back to LLM matching");
                }
            }
            String prompt = fileContentProvider.prepareMatchKnowledgePrompt(jsonString, question, parameters);
            String response = openAI.evaluatePrompt(prompt);
            String responseText = response == null ? "" : response.trim();
            String[] matches = responseText.split(",");
            if (responseText.isEmpty() || Status.NONE.equalsIgnoreCase(responseText) || matches.length >= 5) {
                return null;
            }
            for (String match : matches) {
                if (match.isBlank()) {
                    continue;
                }
                Knowledge knowledge = knowledgeBaserepo.GetByName(match.trim());
                if (knowledge == null) {
                    continue;
                }
                if (knowledge.getParams() == null || knowledge.getParams().isBlank() || params == null || params.isEmpty()) {
                    return knowledge;
                }
                prompt = fileContentProvider.prepareMatchParamsPrompt(knowledge.getParams(), parameters, question);
                if ("YES".equalsIgnoreCase(openAI.evaluatePrompt(prompt))) {
                    return knowledge;
                }
            }
        } catch (JsonProcessingException e) {
            logger.warn("Exception in matchKnowledge", e);
        }
        return null;
    }

    private Knowledge mcpDiscoveryKnowledge() {
        if (!mcpChatFallback || mcpClientService == null || mcpClientService.getRegisteredServers().isEmpty()) {
            return null;
        }
        Knowledge knowledge = new Knowledge();
        knowledge.setName(MCP_DISCOVERY_KNOWLEDGE);
        knowledge.setType(QueryType.MCP);
        knowledge.setData("{\"discover\":true}");
        return knowledge;
    }



    private static final Map<String, String> parameterTranslationMap = new HashMap<>();

    /**
     * Creates and prepares ExecutionContext from ChatRequest.
     * Handles intent identification and parameter setting.
     *
     * @param request the chat request
     * @return the prepared execution context
     * @throws Exception if any step fails
     */
    public ExecutionContext prepareContext(ChatRequest request) throws Exception {
        return setParameters(identifyIntent(createContext(request)));
    }

    private ExecutionContext prepareMatchedContext(ChatRequest request, Knowledge configuredKnowledge) {
        ExecutionContext ctx = createContext(request);
        Knowledge knowledge = configuredKnowledge;
        if (knowledge == null) {
            knowledge = matchFallbackKnowledge(ctx.getUsersQuery(), request.getVariables());
            if (knowledge == null) {
                knowledge = mcpDiscoveryKnowledge();
            }
        }
        if (knowledge == null) {
            throw new IllegalStateException("No matching knowledge found");
        }
        return setParameters(assignKnowledge(ctx, knowledge));
    }
}

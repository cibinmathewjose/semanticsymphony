package org.symphonykernel.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;

import reactor.core.publisher.Flux;
/**
 * The Agent class is a Spring service responsible for processing chat requests
 * and generating responses using a knowledge graph.
 * 
 * <p>This class interacts with {@link KnowledgeGraphBuilder} to create an
 * execution context, identify the intent of the request, set parameters, and
 * generate a response.
 * 
 * <p>Usage:
 * <ul>
 *   <li>Inject an instance of {@link KnowledgeGraphBuilder} into the constructor.</li>
 *   <li>Call the {@link #process(ChatRequest)} method with a {@link ChatRequest} object
 *       to process the request and obtain a {@link ChatResponse}.</li>
 * </ul>
 * 
 * <p>Logging is performed using SLF4J to track the processing of requests.
 * 
 * @author Cibin Jose
 * @version 1.0
 * @since 1.0
 */
@Service
public class Agent {

    private static final Logger logger = LoggerFactory.getLogger(Agent.class);

    private final KnowledgeGraphBuilder knowledgeGraphBuilder;

    /**
     * Constructs an Agent instance with the specified {@link KnowledgeGraphBuilder}.
     * 
     * @param knowledgeGraphBuilder the knowledge graph builder used for processing requests
     */
    public Agent(KnowledgeGraphBuilder knowledgeGraphBuilder) {
        this.knowledgeGraphBuilder = knowledgeGraphBuilder;
    }

    /**
     * Processes a chat request and generates a response.
     * 
     * @param request the chat request containing the query
     * @return a {@link ChatResponse} containing the generated response
     */
    public ChatResponse process(ChatRequest request) {      
        long start = System.nanoTime();
        logger.debug("Processing request: {}", request != null ? request.getQuery() : "Received null request");       
        if (null == request) {
            return new ChatResponse("Request is null");
        } 
        ExecutionContext ctx = knowledgeGraphBuilder.createContext(request);
        logger.debug("ExecutionContext created");   
      
        try
        {
        ctx = knowledgeGraphBuilder.identifyIntent(ctx);
        ctx = knowledgeGraphBuilder.setParameters(ctx); 
        }
        catch(Exception ex)
        {
        	logger.warn("Error setting parameters, try to process as followup Question",ex);        	
            return knowledgeGraphBuilder.getFollowupResponse(request);
        }
        ChatResponse response = knowledgeGraphBuilder.getResponse(ctx);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        logger.info("Processed request in {} ms", durationMs);
        return response;
    }
    public Flux<String> streamProcess(ChatRequest request) {  	   
		if (null == request) {
			return Flux.just("Request is null");
		} 
		ExecutionContext ctx = knowledgeGraphBuilder.createContext(request);
		logger.debug("ExecutionContext created");   
	  
		try
		{
		ctx = knowledgeGraphBuilder.identifyIntent(ctx);
		ctx = knowledgeGraphBuilder.setParameters(ctx); 
		}
		catch(Exception ex)
		{
			logger.warn("Error setting parameters, try to process as followup Question",ex);        	
			return knowledgeGraphBuilder.streamFollowupResponse(request);
		}
		return knowledgeGraphBuilder.streamResponse(ctx);
	}
    public ChatResponse getAsyncResults(String requestId) {   
                
        return knowledgeGraphBuilder.getAsyncResponse(requestId);
    }
    public ChatResponse processFollowUp(String requestId, String query)
    {       
        return knowledgeGraphBuilder.getFollowupResponse(requestId,query);       
    }
}
package org.symphonykernel.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatRequest;
import org.symphonykernel.ChatResponse;

/**
 * The {@code Agent} class is a service responsible for processing chat requests
 * and generating appropriate responses by interacting with the {@link KnowledgeGraphBuilder}.
 * 
 * <p>This class uses a dependency-injected {@code KnowledgeGraphBuilder} to handle
 * the creation of context, intent identification, parameter setting, execution initialization,
 * and response generation for a given chat request.
 * 
 * <p>Logging is performed to track the processing of requests, including handling null requests.
 * 
 * <p>Usage:
 * <pre>
 * {@code
 * Agent agent = new Agent(knowledgeGraphBuilder);
 * ChatResponse response = agent.process(chatRequest);
 * }
 * </pre>
 * 
 * Dependencies:
 * <ul>
 *   <li>{@link KnowledgeGraphBuilder} - Handles the processing pipeline for chat requests.</li>
 * </ul>
 * 
 * Annotations:
 * <ul>
 *   <li>{@code @Service} - Marks this class as a Spring service component.</li>
 *   <li>{@code @Autowired} - Indicates that the {@code KnowledgeGraphBuilder} dependency is injected.</li>
 * </ul>
 * 
 * Methods:
 * <ul>
 *   <li>{@link #process(ChatRequest)} - Processes a chat request and returns a {@link ChatResponse}.</li>
 * </ul>
 */
@Service
public class Agent {

    private static final Logger logger = LoggerFactory.getLogger(Agent.class);

    private final KnowledgeGraphBuilder knowledgeGraphBuilder;

    @Autowired
    public Agent(KnowledgeGraphBuilder knowledgeGraphBuilder) {
        this.knowledgeGraphBuilder = knowledgeGraphBuilder;
    }

    public ChatResponse process(ChatRequest request) {
        if (logger.isInfoEnabled()) {
            // Log the request query or indicate if the request is null
            logger.info("Processing request: {}", request != null ? request.getQuery() : "Received null request");
        }
        if (null == request) {
            return new ChatResponse("Request is null");
        }   
        return knowledgeGraphBuilder
                .createContext(request)
                .identifyIntent()
                .setParameters()
                .initExecuter()
                .getResponse();
    }

}

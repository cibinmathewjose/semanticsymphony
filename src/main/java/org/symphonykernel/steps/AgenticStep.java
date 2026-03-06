package org.symphonykernel.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.agentic.AgenticPlanner;

import com.fasterxml.jackson.databind.node.ArrayNode;

import reactor.core.publisher.Flux;

/**
 * AgenticStep is a step implementation that delegates to the AgenticPlanner
 * for dynamic, LLM-driven plan-and-execute workflows. Instead of following
 * a predefined flow, the LLM decides which tools to call and in what order.
 * <p>
 * Register a Knowledge entry with QueryType.AGENTIC to use this step.
 * The Knowledge data field can contain an optional system prompt to guide
 * the agent's behavior.
 * </p>
 */
@Service
public class AgenticStep extends BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(AgenticStep.class);

    @Autowired
    private AgenticPlanner agenticPlanner;

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {
        logger.info("Executing AgenticStep for: {}", ctx.getName());
        ChatResponse response = agenticPlanner.execute(ctx);
        if (response.getData() != null) {
            saveStepData(ctx, response.getData());
        } else if (response.getMessage() != null) {
            saveStepData(ctx, response.getMessage());
        }
        return response;
    }

    @Override
    public Flux<String> getResponseStream(ExecutionContext ctx) {
        logger.info("Executing AgenticStep stream for: {}", ctx.getName());
        StringBuilder responseAccumulator = new StringBuilder();
        return agenticPlanner.executeStream(ctx)
            .doOnNext(responseAccumulator::append)
            .doFinally(signalType -> {
                saveStepData(ctx, responseAccumulator.toString());
            });
    }
}

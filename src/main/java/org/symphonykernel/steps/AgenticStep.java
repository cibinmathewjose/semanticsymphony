package org.symphonykernel.steps;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.agentic.AgenticPlanner;

import reactor.core.publisher.Flux;

/**
 * AgenticStep is a step implementation that delegates to the AgenticPlanner
 * for dynamic, LLM-driven plan-and-execute workflows. Instead of following
 * a predefined flow, the LLM decides which tools to call and in what order.
 * <p>
 * Register a Knowledge entry with QueryType.AGENTIC to use this step.
 * Only eligible read-only tools can be invoked, subject to host and agent restrictions.
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
        saveFinalResult(ctx, response);
        return response;
    }

    private void saveFinalResult(ExecutionContext ctx, ChatResponse response) {
        if (!"SUCCESS".equals(response.getStatusCode())) return;
        if (response.getData() != null) {
            saveStepData(ctx, response.getData());
        } else if (response.getMessage() != null) {
            saveStepData(ctx, response.getMessage());
        }
    }

    @Override
    public Flux<String> getResponseStream(ExecutionContext ctx) {
        logger.info("Executing AgenticStep stream for: {}", ctx.getName());
        return Flux.defer(() -> {
            var sequence = new AtomicLong();
            return agenticPlanner.streamEvents(ctx).map(event -> {
                long position = sequence.incrementAndGet();
                if (event.result() == null) {
                    String message = event.message().strip();
                    return streamStatus(ctx, "agentic", "Generating output:".equals(message)
                            ? "Generating output..." : message, position);
                }
                saveFinalResult(ctx, event.result());
                if ("ERROR".equals(event.result().getStatusCode())) {
                    return streamError(ctx, "agentic", event.message(), position);
                }
                return streamContent(ctx, "response", event.message(), position);
            });
        });
    }
}

package org.symphonykernel.core;

import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * IStep defines the contract for steps in the Symphony Kernel workflow.
 * It provides methods for generating responses and executing queries.
 */
public interface IStep {

    /**
     * Generates a chat response based on the provided execution context.
     *
     * @param context the execution context containing input data and parameters
     * @return a ChatResponse object containing the generated response
     */
    ChatResponse getResponse(ExecutionContext context);

    /**
     * Executes a query by name using the provided execution context.
     *
     * @param context the execution context containing query details and parameters
     * @return a JsonNode containing the query results
     */
    JsonNode executeQueryByName(ExecutionContext context);
}

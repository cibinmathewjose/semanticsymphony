package org.symphonykernel.steps;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.RestRequestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A step implementation for executing GraphQL queries.
 * 
 * <p>This class extends {@link RESTStep} to provide functionality for handling
 * GraphQL-specific requests and responses.
 * 
 * @version 1.0
 * @since 1.0
 */
@Service("GraphQLStep")
public class GraphQLStep extends RESTStep {

    private static final String QUERY_KEY = "query";
    private static final String VARIABLES_KEY = "variables";

    /**
     * Returns the default {@link RestRequestTemplate} for the execution context.
     * 
     * @param ctx the execution context
     * @return the default request template
     */
    @Override
    protected RestRequestTemplate getTemplate(ExecutionContext ctx) {
        return RestRequestTemplate.getDefault();
    }

    /**
     * Creates the request body for a GraphQL query.
     * 
     * @param ctx the execution context
     * @return the request body as an {@link ObjectNode}
     */
    @Override
    protected ObjectNode createRequestBody(ExecutionContext ctx) {
        JsonNode variables = ctx.getVariables();
        String data = ctx.getKnowledge().getData();
        ObjectNode body = objectMapper.createObjectNode();
        // body.put("operationName", null);
        body.put(QUERY_KEY, data);
        body.set(VARIABLES_KEY, variables);
        return body;
    }
    
    /**
     * Processes the GraphQL response and extracts the "data" field.
     * 
     * @param ctx  the execution context
     * @param root the root JSON node of the response
     * @return the extracted "data" field as a {@link JsonNode}
     */
    @Override
    protected JsonNode processResponse(ExecutionContext ctx, JsonNode root) {
        JsonNode res = root.path("data");
        return res;
    }

    /**
     * Executes a GraphQL query using a resource file.
     * 
     * @param url       the GraphQL endpoint URL
     * @param resource  the resource containing the GraphQL query
     * @param variables the variables for the query
     * @param headers   the HTTP headers for the request
     * @return the response data as a {@link JsonNode}
     * @throws Exception if an error occurs during query execution
     */
    public JsonNode executeGraphqlQueryByResource(String url, Resource resource, JsonNode variables, HttpHeaders headers)
            throws Exception {
        InputStream inputStream = resource.getInputStream();
        String query = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            query = reader.lines().collect(Collectors.joining("\n"));
        }
        ExecutionContext context = new ExecutionContext();      
        context.setHeaders(headers);
        context.setVariables(variables);
        Knowledge kb = new Knowledge();
        kb.setName("GraphQLQuery");
        kb.setUrl(url);
        kb.setData(query);
        context.setKnowledge(kb);
        return getResponse(context).getData();
    }

}
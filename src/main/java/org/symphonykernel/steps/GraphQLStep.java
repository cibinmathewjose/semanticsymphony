package org.symphonykernel.steps;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.RestRequestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service("GraphQLStep")
public class GraphQLStep extends RESTStep {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLStep.class);
        private static final String QUERY_KEY = "query";
    private static final String VARIABLES_KEY = "variables";
    @Override
     protected RestRequestTemplate getTemplate(ExecutionContext ctx) {
        return RestRequestTemplate.getDefault();
     }

    @Override
    protected ObjectNode createRequestBody(ExecutionContext ctx) {
        JsonNode variables = ctx.getVariables();
        String data = ctx.getKnowledge().getData();
        ObjectNode body = objectMapper.createObjectNode();
        // body.put("operationName", null);
        body.put(QUERY_KEY, data);
        body.put(VARIABLES_KEY, variables);
        return body;
    }
    
    @Override
    protected JsonNode processResponse(ExecutionContext ctx,JsonNode root) {
        JsonNode res = root.path("data");
        return res;
    }
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

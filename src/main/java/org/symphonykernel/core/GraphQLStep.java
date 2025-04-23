package org.symphonykernel.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class GraphQLStep implements IStep {

    @Autowired
    PlatformHelper platformHelper;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    IknowledgeBase knowledgeBase;

    @Override
    public ArrayNode getResponse(ExecutionContext ctx) {

        ArrayNode jsonArray = objectMapper.createArrayNode();
        JsonNode variables = ctx.getVariables();
        Knowledge kb = ctx.getKnowledge();
        try {
            if (kb != null && kb.getUrl() != null && !kb.getUrl().isEmpty()) {

                try {

                    System.out.println("Executing GQL " + kb.getName() + " with " + variables);
                    HttpHeaders header = ctx.getHttpHeaderProvider() != null ? ctx.getHttpHeaderProvider().getHeader() : null;
                    JsonNode root = executeGraphqlQuery(kb.getUrl(), kb.getData(), variables, header);
                    JsonNode res = root.path("data");
                    jsonArray.add(res);
                    System.out.println("Data " + res);
                } catch (Exception e) {
                    ObjectNode err = objectMapper.createObjectNode();
                    err.put("errors", e.getMessage());
                    jsonArray.add(err);
                }

            }

        } catch (Exception e) {
            ObjectNode err = objectMapper.createObjectNode();
            err.put("errors", e.getMessage());
            jsonArray.add(err);
        }

        return jsonArray;
    }

    @Override
    public JsonNode executeQueryByName(ExecutionContext context) {
        final ArrayNode[] array = new ArrayNode[1];
        Knowledge kb = knowledgeBase.GetByName(context.getName());
        if (kb != null && kb.getUrl() != null && !kb.getUrl().isEmpty()) {

            try {
                JsonNode var = context.getVariables();
                if (context.getConvert()) {
                    JsonTransformer transformer = new JsonTransformer();
                    var = transformer.compareAndReplaceJson(kb.getParams(), context.getVariables());
                    context.setVariables(var);
                    context.setKnowledge(kb);
                    //var=platformHelper.compareAndReplaceJsonv2(kb.getParams(), variables);
                }
                array[0] = getResponse(context);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
        return array[0];
    }

    @Cacheable(
            value = "cSCPCache",
            key = "T(org.apache.commons.codec.digest.DigestUtils).sha256Hex(#query) + '_' + #variables"
    )
    private JsonNode executeGraphqlQuery(String url, String query, JsonNode variables, HttpHeaders headers) {

        if (headers == null) {
            headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        // body.put("operationName", null);
        body.put("query", query);
        body.put("variables", variables);

        final HttpEntity<String> requestEntity = new HttpEntity<String>(body.toString(), headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, requestEntity, JsonNode.class);

        JsonNode responseBody = response.getBody();
        return responseBody;
    }

    private JsonNode executeGraphqlQueryByResource(String url, Resource resource, JsonNode variables, HttpHeaders headers)
            throws Exception {
        InputStream inputStream = resource.getInputStream();
        String query = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            query = reader.lines().collect(Collectors.joining("\n"));
        }
        return executeGraphqlQuery(url, query, variables, headers);
    }

}

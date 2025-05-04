package org.symphonykernel.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.RestRequestTemplate;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.transformer.JsonTransformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service("RESTStep")
public class RESTStep implements IStep {

    private static final Logger logger = LoggerFactory.getLogger(RESTStep.class);

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    IknowledgeBase knowledgeBase;

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {
        ArrayNode jsonArray = objectMapper.createArrayNode();

        Knowledge kb = ctx.getKnowledge();
        try {
            if (kb != null && kb.getUrl() != null && !kb.getUrl().isEmpty()) {
                try {
                    logger.info("Executing REST " + kb.getName() + " with " + ctx.getVariables());
                    ctx.setTmplate(getTemplate(ctx))
                            .setBody(createRequestBody(ctx))
                            .setHeaders(createRequestHeader(ctx))
                            .setMethod(ctx.getTmplate().getMethod())
                            .setUrl(createUrl(ctx));
                    JsonNode root = invokeAPI(ctx);
                    JsonNode res = processResponse(ctx, root);
                    jsonArray.add(res);
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
        ChatResponse a = new ChatResponse();
        a.setData(jsonArray);
        return a;
    }

    private String createUrl(ExecutionContext ctx) {
        if (ctx.getTmplate() == null || ctx.getTmplate().getUrlParams() == null) {
            return ctx.getKnowledge().getUrl();
        } else {
            String inputString = ctx.getTmplate().getUrlParams();
            JsonNode jsonNode = ctx.getVariables();
            StringBuilder output = new StringBuilder(ctx.getKnowledge().getUrl());
            processPlaceholders(inputString, jsonNode, output);
            return output.toString();
        }
    }

    private void processPlaceholders(String inputString, JsonNode jsonNode, StringBuilder output) {
        int i = 0;
        while (i < inputString.length()) {
            if (inputString.charAt(i) == '{') {
                int j = i + 1;
                StringBuilder keyBuilder = new StringBuilder();
                while (j < inputString.length() && inputString.charAt(j) != '}' && inputString.charAt(j) != '?' && inputString.charAt(j) != '&' && inputString.charAt(j) != '=') {
                    keyBuilder.append(inputString.charAt(j));
                    j++;
                }
                String key = keyBuilder.toString();
                if (jsonNode.has(key)) {
                    output.append(jsonNode.get(key).asText());
                } else {
                    output.append("{").append(key).append("}");
                }
                i = j;
            } else {
                output.append(inputString.charAt(i));
                i++;
            }
        }
    }
    protected JsonNode invokeAPI(ExecutionContext ctx) {

        String url = ctx.getUrl();
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        String body = ctx.getBody() != null ? ctx.getBody().toString() : null;
        final HttpEntity<String> requestEntity = new HttpEntity<>(body, ctx.getHeaders());
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<JsonNode> response;
        HttpMethod method = ctx.getMethod();
        if (method == HttpMethod.POST) {
            response = restTemplate.postForEntity(url, requestEntity, JsonNode.class);
        } else if (method == HttpMethod.GET) {
            response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, JsonNode.class);
        } else if (method == HttpMethod.PUT) {
            response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, JsonNode.class);
        } else if (method == HttpMethod.DELETE) {
            response = restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, JsonNode.class);
        } else {
            throw new UnsupportedOperationException("HTTP method not supported: " + method);
        }

        JsonNode root = response.getBody();
        logger.info("url: {} method: {} responseBody: {}", url, method, root);
        return root;
    }

    protected JsonNode processResponse(ExecutionContext ctx,JsonNode root) {
        if (ctx.getTmplate().getResultSelectors() != null) {
            String selector= ctx.getTmplate().getResultSelectors();
                JsonNode node = root.path(selector);
                if (node.isMissingNode()) {
                    throw new IllegalArgumentException("Invalid selector: " + selector);
                }
                return  node;
        }
        return root;
    }

    protected HttpHeaders createRequestHeader(ExecutionContext ctx) {
        HttpHeaders headers = ctx.getHttpHeaderProvider() != null ? ctx.getHttpHeaderProvider().getHeader() : null;
        if (headers == null) {
            headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return headers;
    }

    protected JsonNode createRequestBody(ExecutionContext ctx) {

        if (ctx.getTmplate().isIncludeRequestBody()) {
            if (ctx.getTmplate().getBodyTemplate() != null) {
                return ctx.getTmplate().getBodyTemplate();
            } else {
                return ctx.getVariables();
            }
        } else {
            return objectMapper.createObjectNode();
        }
    }

    protected RestRequestTemplate getTemplate(ExecutionContext ctx) throws JsonProcessingException {
        String data = ctx.getKnowledge().getData();
        RestRequestTemplate tmp = RestRequestTemplate.getDefault();
        if (data != null && !data.isEmpty()) {
            if (data.contains("{{$params}}")) {
                if (ctx.getVariables() == null) {
                    throw new IllegalArgumentException("Variables cannot be null");
                } else {
                    data = data.replace("{{$params}}", ctx.getVariables().toString());
                }
            }
            
            JsonNode templateNode = objectMapper.readTree(data);
            tmp = objectMapper.convertValue(templateNode, RestRequestTemplate.class);
            
        }
        return tmp;
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
                }
                array[0] = getResponse(context).getData();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return array[0];
    }

}

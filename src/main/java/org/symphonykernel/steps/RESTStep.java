package org.symphonykernel.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
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
import org.symphonykernel.config.Constants;
import org.symphonykernel.core.IknowledgeBase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * RESTStep is a step implementation for executing REST API calls.
 * It provides methods to create request headers, process API responses, and invoke APIs.
 */
@Service("RESTStep")
public class RESTStep extends BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(RESTStep.class);


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
                    saveStepData(ctx, jsonArray);
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
            String output = replacePlaceholders(inputString, jsonNode);
            return ctx.getKnowledge().getUrl() + output;
        }
    }

    private String replacePlaceholders(String inputString, JsonNode jsonNode) {
        StringBuilder output = new StringBuilder();
        int i = 0;
        while (i < inputString.length()) {
            if (inputString.charAt(i) == '{') {
                int j = i + 1;
                StringBuilder keyBuilder = new StringBuilder();
                while (j < inputString.length() && inputString.charAt(j) != '}') {
                    keyBuilder.append(inputString.charAt(j));
                    j++;
                }
                String key = keyBuilder.toString();
                if (jsonNode.has(key)) {
                    output.append(jsonNode.get(key).asText());
                } else {
                    output.append("{").append(key).append("}");
                    logger.warn("Key not found in JSON: {}", key);
                }
                i = j + 1; // Skip the closing '}'
            } else {
                output.append(inputString.charAt(i));
                i++;
            }
        }
        return output.toString();
    }

    /**
     * Invokes the API based on the execution context.
     *
     * @param ctx the execution context containing the API details
     * @return the JSON node representing the API response
     */
    @Cacheable(value = Constants.CACHE_NAME, key = "T(org.apache.commons.codec.digest.DigestUtils).sha256Hex(#ctx.toString())")
    protected JsonNode invokeAPI(ExecutionContext ctx) {

        String url = ctx.getUrl();
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        String body = ctx.getBody() != null ? ctx.getBody().toString() : null;
        HttpHeaders headers = ctx.getHeaders();
        HttpMethod method = ctx.getMethod();
        JsonNode root = call(url, body, headers, method);
        return root;
    }
    
    
	private JsonNode call(String url, String body, HttpHeaders headers, HttpMethod method) {
		final HttpEntity<String> requestEntity = new HttpEntity<>(body,headers );
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<JsonNode> response;
        ;
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
        
        logger.info("url: {} method: {} responseBody: {}", url, method, root!=null?root.toString():"null");
		return root;
	}

    /**
     * Processes the response from the API based on the result selectors in the template.
     *
     * @param ctx  the execution context containing the template and variables
     * @param root the root JSON node of the API response
     * @return the processed JSON node based on the result selectors
     */
    protected JsonNode processResponse(ExecutionContext ctx, JsonNode root) {
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

    /**
     * Creates the request headers for the API call.
     *
     * @param ctx the execution context containing the template and variables
     * @return the HTTP headers for the API call
     */
    protected HttpHeaders createRequestHeader(ExecutionContext ctx) {
        HttpHeaders headers = ctx.getHttpHeaderProvider() != null ? ctx.getHttpHeaderProvider().getHeader() : null;
        if (headers == null) {
            headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return headers;
    }

    /**
     * Creates the request body for the API call based on the template and variables.
     *
     * @param ctx the execution context containing the template and variables
     * @return the JSON node representing the request body
     */
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

    /**
     * Retrieves the REST request template for the given execution context.
     *
     * @param ctx the execution context containing knowledge and variables
     * @return the REST request template
     * @throws JsonProcessingException if there is an error processing the JSON data
     */
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
}

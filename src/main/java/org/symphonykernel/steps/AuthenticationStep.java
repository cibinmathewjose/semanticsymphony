/*
 * The MIT License
 *
 * Copyright 2025 cjose.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.symphonykernel.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IHttpHeaderProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * AuthenticationStep acquires an OAuth2 access token from a token endpoint
 * and sets it in the execution context for use by downstream API steps.
 *
 * <p>The Knowledge entry for this step should have:
 * <ul>
 *   <li><b>url</b> – the token endpoint (e.g. {@code https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token})</li>
 *   <li><b>data</b> – a JSON object with authentication parameters:
 *     <pre>{
 *   "grant_type": "client_credentials",
 *   "client_id": "...",
 *   "client_secret": "...",
 *   "scope": "...",
 *   "token_field": "access_token",
 *   "token_type": "Bearer",
 *   "header_name": "Authorization"
 * }</pre>
 *   </li>
 * </ul>
 *
 * <p>Optional fields in the data JSON:
 * <ul>
 *   <li><b>token_field</b> – the JSON field in the token response that holds the token (default: {@code access_token})</li>
 *   <li><b>token_type</b> – the prefix for the Authorization header value (default: {@code Bearer})</li>
 *   <li><b>header_name</b> – the HTTP header name to set (default: {@code Authorization})</li>
 * </ul>
 */
@Service("AuthenticationStep")
public class AuthenticationStep extends BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationStep.class);

    private static final String DEFAULT_TOKEN_FIELD = "access_token";
    private static final String DEFAULT_TOKEN_TYPE = "Bearer";
    private static final String DEFAULT_HEADER_NAME = "Authorization";

    private final RestTemplate restTemplate;

    /** Constructs an AuthenticationStep with a default RestTemplate. */
    public AuthenticationStep() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {
        ArrayNode jsonArray = objectMapper.createArrayNode();
        Knowledge kb = ctx.getKnowledge();

        try {
            if (kb == null || kb.getUrl() == null || kb.getUrl().isEmpty()) {
                throw new IllegalArgumentException("AuthenticationStep requires a token endpoint URL in the Knowledge entry");
            }

            JsonNode config = parseAuthConfig(kb);
            String tokenUrl = kb.getUrl();

            logger.info("Requesting token from {}", tokenUrl);

            String token = requestToken(tokenUrl, config);
            String tokenType = getConfigValue(config, "token_type", DEFAULT_TOKEN_TYPE);
            String headerName = getConfigValue(config, "header_name", DEFAULT_HEADER_NAME);
            String headerValue = tokenType + " " + token;

            // Set the token in the context's HTTP header provider for downstream steps
            setTokenInContext(ctx, headerName, headerValue);

            // Return the token info as step output (without the raw secret)
            ObjectNode result = objectMapper.createObjectNode();
            result.put("authenticated", true);
            result.put("token_type", tokenType);
            result.put("header_name", headerName);
            jsonArray.add(result);

            saveStepData(ctx, jsonArray);
            logger.info("AuthenticationStep completed successfully for {}", kb.getName());

        } catch (Exception e) {
            logger.error("AuthenticationStep failed: {}", e.getMessage());
            ObjectNode err = objectMapper.createObjectNode();
            err.put("authenticated", false);
            err.put("errors", e.getMessage());
            jsonArray.add(err);
        }

        ChatResponse response = new ChatResponse();
        response.setData(jsonArray);
        return response;
    }

    private JsonNode parseAuthConfig(Knowledge kb) {
        String data = kb.getData();
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("AuthenticationStep requires auth configuration in Knowledge data field");
        }
        return getParamNode(data);
    }

    private String requestToken(String tokenUrl, JsonNode config) {

        /*
        {
        "name": "GetApiToken",
        "type": "AUTH",
        "url": "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token",
        "data": "{\"grant_type\":\"client_credentials\",\"client_id\":\"...\",\"client_secret\":\"...\",\"scope\":\"api://.../.default\"}"
        }
        */
        String grantType = getConfigValue(config, "grant_type", "client_credentials");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("grant_type", grantType);

        addIfPresent(formParams, config, "client_id");
        addIfPresent(formParams, config, "client_secret");
        addIfPresent(formParams, config, "scope");
        addIfPresent(formParams, config, "resource");
        addIfPresent(formParams, config, "username");
        addIfPresent(formParams, config, "password");

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(formParams, headers);
        ResponseEntity<JsonNode> response = restTemplate.exchange(tokenUrl, HttpMethod.POST, requestEntity, JsonNode.class);

        JsonNode responseBody = response.getBody();
        if (responseBody == null) {
            throw new RuntimeException("Empty response from token endpoint");
        }

        String tokenField = getConfigValue(config, "token_field", DEFAULT_TOKEN_FIELD);
        JsonNode tokenNode = responseBody.get(tokenField);
        if (tokenNode == null || tokenNode.asText().isEmpty()) {
            throw new RuntimeException("Token field '" + tokenField + "' not found in response");
        }

        return tokenNode.asText();
    }

    private void setTokenInContext(ExecutionContext ctx, String headerName, String headerValue) {
        IHttpHeaderProvider headerProvider = ctx.getHttpHeaderProvider();
        if (headerProvider != null) {
            headerProvider.add(headerName, headerValue);
        } else {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(headerName, headerValue);
            ctx.setHeaders(headers);
            ctx.setHttpHeaderProvider(new IHttpHeaderProvider() {
                private final HttpHeaders httpHeaders = headers;

                @Override
                public HttpHeaders getHeader() {
                    return httpHeaders;
                }

                @Override
                public void add(String key, String value) {
                    httpHeaders.set(key, value);
                }
            });
        }
    }

    private String getConfigValue(JsonNode config, String field, String defaultValue) {
        if (config.has(field) && !config.get(field).asText().isEmpty()) {
            return config.get(field).asText();
        }
        return defaultValue;
    }

    private void addIfPresent(MultiValueMap<String, String> formParams, JsonNode config, String field) {
        if (config.has(field) && !config.get(field).asText().isEmpty()) {
            formParams.add(field, config.get(field).asText());
        }
    }
}

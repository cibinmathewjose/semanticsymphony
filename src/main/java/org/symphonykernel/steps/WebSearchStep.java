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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.core.IAIClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Flux;

/**
 * WebSearchStep searches the internet using a configured search provider
 * and optionally summarises the results using an LLM.
 *
 * <p><strong>Supported providers</strong></p>
 * <ul>
 *   <li><b>bing</b> – Bing Web Search API v7</li>
 *   <li><b>google</b> – Google Custom Search JSON API</li>
 *   <li><b>serpapi</b> – SerpAPI (Google, Bing, etc.)</li>
 * </ul>
 *
 * <p><strong>Knowledge entry configuration</strong></p>
 * <p>The {@code data} field of the Knowledge entry should contain a JSON object:</p>
 * <pre>{@code
 * {
 *   "provider": "bing",
 *   "api_key": "...",
 *   "count": 5,
 *   "SystemPrompt": "You are a research assistant. Based on the search results below, provide a concise answer.",
 *   "google_cx": "...",
 *   "google_api_key": "..."
 * }
 * }</pre>
 *
 * <p>Alternatively, API keys can be set via Spring properties:</p>
 * <ul>
 *   <li>{@code symphony.websearch.bing.api-key}</li>
 *   <li>{@code symphony.websearch.google.api-key}</li>
 *   <li>{@code symphony.websearch.google.cx}</li>
 *   <li>{@code symphony.websearch.serpapi.api-key}</li>
 * </ul>
 *
 * <p>The search query is taken from the user's query ({@code ExecutionContext.getUsersQuery()})
 * or from the variables field {@code query}.</p>
 */
@Service("WebSearchStep")
public class WebSearchStep extends BaseStep {

    private static final Logger logger = LoggerFactory.getLogger(WebSearchStep.class);

    private static final String DEFAULT_PROVIDER = "bing";
    private static final int DEFAULT_RESULT_COUNT = 5;

    private static final String BING_ENDPOINT = "https://api.bing.microsoft.com/v7.0/search";
    private static final String GOOGLE_ENDPOINT = "https://www.googleapis.com/customsearch/v1";
    private static final String SERPAPI_ENDPOINT = "https://serpapi.com/search.json";

    @Autowired
    private IAIClient aiClient;

    @Autowired
    private Environment environment;

    private final RestTemplate restTemplate;

    /**
     * Constructs a new {@code WebSearchStep} with a default {@link RestTemplate}
     * configured with connect and read timeouts.
     */
    public WebSearchStep() {
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
            JsonNode config = getConfig(kb);
            String provider = getConfigValue(config, "provider", DEFAULT_PROVIDER).toLowerCase();
            int count = getIntConfig(config, "count", DEFAULT_RESULT_COUNT);
            String query = resolveQuery(ctx);

            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("WebSearchStep requires a search query");
            }

            logger.info("WebSearchStep: searching '{}' via {} (count={})", query, provider, count);

            JsonNode searchResults = executeSearch(provider, query, count, config);
            ArrayNode formattedResults = formatResults(provider, searchResults, count);

            // Optionally summarise with LLM
            String systemPrompt = getConfigValue(config, "SystemPrompt", null);
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                String summary = summariseWithLlm(systemPrompt, query, formattedResults, ctx);
                ObjectNode resultNode = objectMapper.createObjectNode();
                resultNode.put("query", query);
                resultNode.put("summary", summary);
                resultNode.set("sources", formattedResults);
                jsonArray.add(resultNode);
            } else {
                ObjectNode resultNode = objectMapper.createObjectNode();
                resultNode.put("query", query);
                resultNode.set("results", formattedResults);
                jsonArray.add(resultNode);
            }

            saveStepData(ctx, jsonArray);
            logger.info("WebSearchStep completed for query '{}'", query);

        } catch (Exception e) {
            logger.error("WebSearchStep failed: {}", e.getMessage());
            ObjectNode err = objectMapper.createObjectNode();
            err.put("errors", e.getMessage());
            jsonArray.add(err);
        }

        ChatResponse response = new ChatResponse();
        response.setData(jsonArray);
        return response;
    }

    @Override
    public Flux<String> getResponseStream(ExecutionContext ctx) {
        Knowledge kb = ctx.getKnowledge();
        try {
            JsonNode config = getConfig(kb);
            String provider = getConfigValue(config, "provider", DEFAULT_PROVIDER).toLowerCase();
            int count = getIntConfig(config, "count", DEFAULT_RESULT_COUNT);
            String query = resolveQuery(ctx);

            if (query == null || query.isBlank()) {
                return Flux.just("Error: No search query provided");
            }

            JsonNode searchResults = executeSearch(provider, query, count, config);
            ArrayNode formattedResults = formatResults(provider, searchResults, count);

            String systemPrompt = getConfigValue(config, "SystemPrompt", null);
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                String searchContext = buildSearchContext(formattedResults);
                String userPrompt = "Question: " + query + "\n\nSearch Results:\n" + searchContext;
                StringBuilder responseAccumulator = new StringBuilder();
                return aiClient.streamExecute(new LLMRequest(systemPrompt, userPrompt, null, ctx.getModelName()))
                    .doOnNext(responseAccumulator::append)
                    .doFinally(signalType -> saveStepData(ctx, responseAccumulator.toString()));
            } else {
                ObjectNode resultNode = objectMapper.createObjectNode();
                resultNode.put("query", query);
                resultNode.set("results", formattedResults);
                return Flux.just(resultNode.toString());
            }
        } catch (Exception e) {
            return Flux.just("Error: " + e.getMessage());
        }
    }

    // ==================== SEARCH EXECUTION ====================

    private JsonNode executeSearch(String provider, String query, int count, JsonNode config) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        return switch (provider) {
            case "bing" -> searchBing(encodedQuery, count, config);
            case "google" -> searchGoogle(encodedQuery, count, config);
            case "serpapi" -> searchSerpApi(encodedQuery, count, config);
            default -> throw new IllegalArgumentException("Unsupported search provider: " + provider);
        };
    }

    private JsonNode searchBing(String encodedQuery, int count, JsonNode config) {
        String apiKey = resolveApiKey(config, "api_key", "symphony.websearch.bing.api-key");
        String url = BING_ENDPOINT + "?q=" + encodedQuery + "&count=" + count;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
        return response.getBody();
    }

    private JsonNode searchGoogle(String encodedQuery, int count, JsonNode config) {
        String apiKey = resolveApiKey(config, "google_api_key", "symphony.websearch.google.api-key");
        String cx = getConfigValue(config, "google_cx",
                environment.getProperty("symphony.websearch.google.cx", ""));

        if (cx.isEmpty()) {
            throw new IllegalArgumentException("Google Custom Search requires 'google_cx' (search engine ID)");
        }

        String url = GOOGLE_ENDPOINT + "?key=" + apiKey + "&cx=" + cx
                + "&q=" + encodedQuery + "&num=" + Math.min(count, 10);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
        return response.getBody();
    }

    private JsonNode searchSerpApi(String encodedQuery, int count, JsonNode config) {
        String apiKey = resolveApiKey(config, "api_key", "symphony.websearch.serpapi.api-key");
        String url = SERPAPI_ENDPOINT + "?q=" + encodedQuery + "&num=" + count + "&api_key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
        return response.getBody();
    }

    // ==================== RESULT FORMATTING ====================

    private ArrayNode formatResults(String provider, JsonNode rawResults, int count) {
        ArrayNode formatted = objectMapper.createArrayNode();
        if (rawResults == null) {
            return formatted;
        }

        return switch (provider) {
            case "bing" -> formatBingResults(rawResults, count);
            case "google" -> formatGoogleResults(rawResults, count);
            case "serpapi" -> formatSerpApiResults(rawResults, count);
            default -> formatted;
        };
    }

    private ArrayNode formatBingResults(JsonNode rawResults, int count) {
        ArrayNode formatted = objectMapper.createArrayNode();
        JsonNode webPages = rawResults.path("webPages").path("value");
        if (webPages.isArray()) {
            int added = 0;
            for (JsonNode page : webPages) {
                if (added >= count) break;
                ObjectNode item = objectMapper.createObjectNode();
                item.put("title", page.path("name").asText(""));
                item.put("url", page.path("url").asText(""));
                item.put("snippet", page.path("snippet").asText(""));
                formatted.add(item);
                added++;
            }
        }
        return formatted;
    }

    private ArrayNode formatGoogleResults(JsonNode rawResults, int count) {
        ArrayNode formatted = objectMapper.createArrayNode();
        JsonNode items = rawResults.path("items");
        if (items.isArray()) {
            int added = 0;
            for (JsonNode item : items) {
                if (added >= count) break;
                ObjectNode entry = objectMapper.createObjectNode();
                entry.put("title", item.path("title").asText(""));
                entry.put("url", item.path("link").asText(""));
                entry.put("snippet", item.path("snippet").asText(""));
                formatted.add(entry);
                added++;
            }
        }
        return formatted;
    }

    private ArrayNode formatSerpApiResults(JsonNode rawResults, int count) {
        ArrayNode formatted = objectMapper.createArrayNode();
        JsonNode organicResults = rawResults.path("organic_results");
        if (organicResults.isArray()) {
            int added = 0;
            for (JsonNode item : organicResults) {
                if (added >= count) break;
                ObjectNode entry = objectMapper.createObjectNode();
                entry.put("title", item.path("title").asText(""));
                entry.put("url", item.path("link").asText(""));
                entry.put("snippet", item.path("snippet").asText(""));
                formatted.add(entry);
                added++;
            }
        }
        return formatted;
    }

    // ==================== LLM SUMMARISATION ====================

    private String summariseWithLlm(String systemPrompt, String query, ArrayNode results, ExecutionContext ctx) {
        String searchContext = buildSearchContext(results);
        String userPrompt = "Question: " + query + "\n\nSearch Results:\n" + searchContext;
        return aiClient.execute(new LLMRequest(systemPrompt, userPrompt, null, ctx.getModelName()));
    }

    private String buildSearchContext(ArrayNode results) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (JsonNode result : results) {
            sb.append("[").append(index++).append("] ")
              .append(result.path("title").asText("")).append("\n")
              .append("URL: ").append(result.path("url").asText("")).append("\n")
              .append(result.path("snippet").asText("")).append("\n\n");
        }
        return sb.toString();
    }

    // ==================== HELPERS ====================

    private String resolveQuery(ExecutionContext ctx) {
        // First try from variables
        JsonNode variables = ctx.getVariables();
        if (variables != null && variables.has("query") && !variables.get("query").asText().isBlank()) {
            return variables.get("query").asText();
        }
        // Fall back to user's query
        return ctx.getUsersQuery();
    }

    private JsonNode getConfig(Knowledge kb) {
        if (kb != null && kb.getData() != null && !kb.getData().isEmpty()) {
            return getParamNode(kb.getData());
        }
        return objectMapper.createObjectNode();
    }

    private String resolveApiKey(JsonNode config, String configField, String propertyName) {
        // First try from config JSON
        String key = getConfigValue(config, configField, null);
        if (key != null && !key.isBlank()) {
            return key;
        }
        // Then try from Spring environment property
        key = environment.getProperty(propertyName);
        if (key != null && !key.isBlank()) {
            return key;
        }
        throw new IllegalArgumentException("API key not configured. Set '" + configField
                + "' in Knowledge data or '" + propertyName + "' in application properties");
    }

    private String getConfigValue(JsonNode config, String field, String defaultValue) {
        if (config != null && config.has(field) && !config.get(field).asText().isEmpty()) {
            return config.get(field).asText();
        }
        return defaultValue;
    }

    private int getIntConfig(JsonNode config, String field, int defaultValue) {
        if (config != null && config.has(field)) {
            return config.get(field).asInt(defaultValue);
        }
        return defaultValue;
    }
}

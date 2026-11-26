package org.symphonykernel.steps;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.exception.HttpResponseException;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.SearchDocument;
import com.azure.search.documents.models.QueryType;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.SearchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Runs a read-only keyword search on an existing Azure AI Search index. */
@Service
public class AzureAISearchStep extends BaseStep {
    private static final String PREFIX = "symphony.azure-ai-search.";
    private static final String INVALID_CONFIG = "Azure AI Search Knowledge data must be a JSON object.";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_TOP = 20;
    private static final int MAX_RESULT_CHARS = 128 * 1024;
    private static final Pattern INDEX = Pattern.compile("[a-z0-9][a-z0-9_-]{0,126}[a-z0-9]");
    private static final Pattern FIELD = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(/[A-Za-z][A-Za-z0-9_]*)*");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}]+");
    private final SearchGateway gateway;

    /** Creates a step using an optional named Entra credential or a read-only query key. */
    @Autowired
    public AzureAISearchStep(Environment settings,
            @Qualifier("azureAISearchStepCredential") ObjectProvider<TokenCredential> credentials) {
        this((index, query, options) -> {
            String endpoint = endpoint(settings.getProperty(PREFIX + "endpoint", ""));
            var builder = new SearchClientBuilder().endpoint(endpoint).indexName(index);
            TokenCredential credential = credentials.getIfAvailable();
            if (credential != null) {
                builder.credential(credential);
            } else {
                String key = settings.getProperty(PREFIX + "query-key", "").strip();
                if (key.isEmpty()) {
                    throw new SearchFailure("Configure an Azure AI Search Entra credential or query key.");
                }
                builder.credential(new AzureKeyCredential(key));
            }
            return builder.buildAsyncClient().search(query, options);
        });
    }

    AzureAISearchStep(SearchGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public ChatResponse getResponse(ExecutionContext ctx) {
        ArrayNode data = objectMapper.createArrayNode();
        try {
            data.add(retrieve(ctx));
            saveStepData(ctx, data);
        } catch (SearchFailure ex) {
            data.addObject().put("errors", ex.getMessage());
        } catch (HttpResponseException ex) {
            int status = ex.getResponse() == null ? 0 : ex.getResponse().getStatusCode();
            data.addObject().put("errors", serviceError(status));
        } catch (RuntimeException ex) {
            data.addObject().put("errors", "Azure AI Search request failed. Check connectivity and configuration.");
        }
        ChatResponse response = new ChatResponse();
        response.setData(data);
        return response;
    }

    @Override
    public Flux<String> getResponseStream(ExecutionContext ctx) {
        return Mono.fromCallable(() -> getResponse(ctx).getData().toString())
                .subscribeOn(Schedulers.boundedElastic()).flux();
    }

    private ObjectNode retrieve(ExecutionContext ctx) {
        JsonNode config = configuration(ctx);
        String index = config.path("index").asText("");
        if (!INDEX.matcher(index).matches()) {
            throw new SearchFailure("Configure a valid Azure AI Search index in Knowledge data.");
        }
        JsonNode top = config.path("top");
        int count = top.isMissingNode() ? 5 : top.intValue();
        if ((!top.isMissingNode() && (!top.isIntegralNumber() || !top.canConvertToInt())) || count < 1 || count > MAX_TOP) {
            throw new SearchFailure("Azure AI Search top must be an integer from 1 to 20.");
        }
        var options = new SearchOptions().setQueryType(QueryType.SIMPLE).setTop(count)
            .setSelect(fields(config.path("select"), "select"));
        if (config.has("searchFields")) {
            options.setSearchFields(fields(config.path("searchFields"), "searchFields"));
        }
        JsonNode variables = ctx.getVariables();
        String input = variables == null ? "" : variables.path("query").asText("");
        String question = (input.isBlank() ? Objects.toString(ctx.getUsersQuery(), "") : input).strip();
        String query = keywordQuery(question);
        Hits hits = gateway.search(index, query, options).take(count)
            .collect(Hits::new, (accumulator, result) -> accumulator.add(result)).block(TIMEOUT);
        ObjectNode result = objectMapper.createObjectNode().put("query", question).put("index", index);
        return result.set("hits", Objects.requireNonNull(hits).documents);
    }

    private JsonNode configuration(ExecutionContext ctx) {
        String data = ctx.getKnowledge() == null ? null : ctx.getKnowledge().getData();
        try {
            JsonNode config = data == null ? null : objectMapper.readTree(data);
            if (config != null && config.isObject()) {
                return config;
            }
        } catch (JsonProcessingException ex) {
            throw new SearchFailure(INVALID_CONFIG, ex);
        }
        throw new SearchFailure(INVALID_CONFIG);
    }

    private static String[] fields(JsonNode config, String label) {
        if (!config.isArray() || config.isEmpty() || config.size() > 16) {
            throw new SearchFailure("Azure AI Search " + label + " must contain 1 to 16 field names.");
        }
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode field : config) {
            String name = field.asText("");
            if (!field.isTextual() || name.length() > 128 || !FIELD.matcher(name).matches()) {
                throw new SearchFailure("Azure AI Search " + label + " contains an invalid field name.");
            }
            names.add(name);
        }
        return names.toArray(String[]::new);
    }

    private static String keywordQuery(String question) {
        if (question.length() > 2_000) {
            throw new SearchFailure("Azure AI Search query must not exceed 2,000 characters.");
        }
        var matcher = WORD.matcher(question);
        var query = new StringJoiner(" ");
        while (matcher.find()) {
            query.add(matcher.group());
        }
        if (query.length() == 0) {
            throw new SearchFailure("Azure AI Search requires a query with searchable terms.");
        }
        return query.toString();
    }

    static String endpoint(String value) {
        try {
            URI uri = URI.create(value.strip());
            String host = Objects.toString(uri.getHost(), "").toLowerCase(Locale.ROOT);
            if ("https".equalsIgnoreCase(uri.getScheme()) && host.matches("[a-z0-9-]+\\.search\\.windows\\.net")
                    && uri.getRawUserInfo() == null && uri.getPort() == -1 && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && Set.of("", "/").contains(uri.getRawPath())) {
                return "https://" + host;
            }
        } catch (IllegalArgumentException ex) {
            throw invalidEndpoint(ex);
        }
        throw invalidEndpoint(null);
    }

    private static SearchFailure invalidEndpoint(IllegalArgumentException cause) {
        return new SearchFailure("Configure an Azure AI Search HTTPS service origin in " + PREFIX + "endpoint.", cause);
    }

    private static String serviceError(int status) {
        return switch (status) {
            case 400 -> "Azure AI Search rejected the query. Check the configured fields and index schema.";
            case 401, 403 -> "Azure AI Search denied the query. Check the credential and index permissions.";
            case 404 -> "Azure AI Search index was not found.";
            case 429 -> "Azure AI Search throttled the query. Try again later.";
            default -> "Azure AI Search request failed.";
        };
    }

    // Keep the service projection as-is, including nested and collection field values.
    private final class Hits {
        private final ArrayNode documents = objectMapper.createArrayNode();
        private int size;

        void add(SearchResult result) {
            ObjectNode hit = objectMapper.createObjectNode().put("score", result.getScore());
            hit.set("document", objectMapper.valueToTree(result.getDocument(SearchDocument.class)));
            size += hit.toString().length();
            if (size > MAX_RESULT_CHARS) {
                throw new SearchFailure("Azure AI Search results are too large. Narrow select or lower top.");
            }
            documents.add(hit);
        }
    }

    @FunctionalInterface
    interface SearchGateway {
        Flux<SearchResult> search(String index, String query, SearchOptions options);
    }

    private static final class SearchFailure extends RuntimeException {
        SearchFailure(String message) {
            super(message);
        }

        SearchFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
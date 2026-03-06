package org.symphonykernel.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory vector-based intent matcher that uses embedding cosine similarity
 * to match user queries against knowledge descriptions without an LLM call.
 *
 * <p>Enable via {@code symphony.intent.vector.enabled=true} in application properties.
 * Requires an {@link EmbeddingModel} bean (e.g., configured via
 * {@code spring.ai.azure.openai.embedding.options.deployment-name}).</p>
 *
 * <p>Configuration properties:</p>
 * <ul>
 *   <li>{@code symphony.intent.vector.enabled} &mdash; enable/disable (default: false)</li>
 *   <li>{@code symphony.intent.vector.similarity-threshold} &mdash; minimum cosine similarity to accept a match (default: 0.78)</li>
 *   <li>{@code symphony.intent.vector.top-k} &mdash; maximum candidate matches returned (default: 3)</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "symphony.intent.vector.enabled", havingValue = "true")
@ConditionalOnBean(EmbeddingModel.class)
public class InMemoryVectorIntentMatcher {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryVectorIntentMatcher.class);

    private final EmbeddingModel embeddingModel;

    @Value("${symphony.intent.vector.similarity-threshold:0.78}")
    private double similarityThreshold;

    @Value("${symphony.intent.vector.top-k:3}")
    private int topK;

    private final ConcurrentHashMap<String, float[]> embeddingIndex = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock indexLock = new ReentrantReadWriteLock();

    /**
     * Constructs the matcher with the given embedding model.
     *
     * @param embeddingModel the model used to generate embeddings
     */
    public InMemoryVectorIntentMatcher(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        logger.info("InMemoryVectorIntentMatcher initialized");
    }

    /**
     * Result of a vector similarity match.
     */
    public static class MatchResult {
        private final String knowledgeName;
        private final double score;

        /**
         * Constructs a MatchResult.
         *
         * @param knowledgeName the matched knowledge name
         * @param score the similarity score
         */
        public MatchResult(String knowledgeName, double score) {
            this.knowledgeName = knowledgeName;
            this.score = score;
        }

        /** @return the matched knowledge name */
        public String getKnowledgeName() {
            return knowledgeName;
        }

        /** @return the similarity score */
        public double getScore() {
            return score;
        }
    }

    /**
     * Rebuilds the in-memory vector index from the supplied knowledge descriptions.
     * Each entry is embedded using the configured {@link EmbeddingModel} and stored
     * for later cosine-similarity comparisons.
     *
     * @param knowledgeDescriptions map of knowledge name to human-readable description
     */
    public void refreshIndex(Map<String, String> knowledgeDescriptions) {
        if (knowledgeDescriptions == null || knowledgeDescriptions.isEmpty()) {
            return;
        }

        long start = System.currentTimeMillis();
        ConcurrentHashMap<String, float[]> newIndex = new ConcurrentHashMap<>();

        for (Map.Entry<String, String> entry : knowledgeDescriptions.entrySet()) {
            String name = entry.getKey();
            String description = entry.getValue();
            if (description != null && !description.isBlank()) {
                try {
                    float[] embedding = embeddingModel.embed(name + ": " + description);
                    newIndex.put(name, embedding);
                } catch (Exception e) {
                    logger.warn("Failed to embed knowledge '{}': {}", name, e.getMessage());
                }
            }
        }

        indexLock.writeLock().lock();
        try {
            embeddingIndex.clear();
            embeddingIndex.putAll(newIndex);
        } finally {
            indexLock.writeLock().unlock();
        }
        logger.info("Vector intent index refreshed with {} entries in {} ms",
                embeddingIndex.size(), System.currentTimeMillis() - start);
    }

    /**
     * Finds knowledge entries whose descriptions are semantically similar to the query.
     * Only entries exceeding the configured similarity threshold are returned.
     *
     * @param query the user query text
     * @return matches sorted by descending cosine similarity, capped at top-K
     */
    public List<MatchResult> findMatches(String query) {
        if (embeddingIndex.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }

        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingModel.embed(query);
        } catch (Exception e) {
            logger.warn("Failed to embed query: {}", e.getMessage());
            return List.of();
        }

        List<MatchResult> results = new ArrayList<>();
        indexLock.readLock().lock();
        try {
            for (Map.Entry<String, float[]> entry : embeddingIndex.entrySet()) {
                double similarity = cosineSimilarity(queryEmbedding, entry.getValue());
                if (similarity >= similarityThreshold) {
                    results.add(new MatchResult(entry.getKey(), similarity));
                }
            }
        } finally {
            indexLock.readLock().unlock();
        }

        results.sort(Comparator.comparingDouble(MatchResult::getScore).reversed());
        if (results.size() > topK) {
            return new ArrayList<>(results.subList(0, topK));
        }
        return results;
    }

    /**
     * Returns {@code true} if the vector index has been populated.
     *
     * @return true if the index is ready
     */
    public boolean isIndexReady() {
        return !embeddingIndex.isEmpty();
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0.0 ? 0.0 : dotProduct / denominator;
    }
}

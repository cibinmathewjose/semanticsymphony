package org.symphonykernel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import com.azure.core.credential.AzureKeyCredential;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.UnifiedJedis;

/**
 * SymphonyKernelAutoConfiguration is a configuration class that defines beans for various services
 * such as database connections, Redis, Azure AI Search, and task scheduling.
 */
@Component
public class SymphonyKernelAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SymphonyKernelAutoConfiguration.class);


    /**
     * Creates a {@link UnifiedJedis} instance for Redis operations using the provided
     * {@link RedisConnectionProperties}.
     *
     * @param connectionProperties the {@link RedisConnectionProperties} containing Redis connection details
     * @return the {@link UnifiedJedis} instance
     */
    @Bean
    @ConditionalOnClass(UnifiedJedis.class)
    @ConditionalOnMissingBean
    public UnifiedJedis createUnifiedJedis(RedisConnectionProperties connectionProperties) {
       
        // Configure client with password and SSL
        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .password(connectionProperties.getPassword())
                .ssl(connectionProperties.getSSL())
                .build();

        // Wrap JedisCluster in UnifiedJedis
        return new UnifiedJedis(connectionProperties.getUrl(),clientConfig);
    }

    /**
     * Creates a {@link SearchIndexClient} for Azure AI Search using the provided
     * {@link AzureAISearchConnectionProperties}.
     *
     * @param connectionProperties the {@link AzureAISearchConnectionProperties} containing search connection details
     * @return the {@link SearchIndexClient}
     */
    @Bean
    @ConditionalOnClass(SearchIndexClient.class)
    @ConditionalOnMissingBean
    public SearchIndexClient createAiSearch(AzureAISearchConnectionProperties connectionProperties) {       
        return new SearchIndexClientBuilder()
        .endpoint(connectionProperties.getEndpoint())
        .credential( new AzureKeyCredential(connectionProperties.getKey()))
        .buildClient();
    }

    /**
     * Creates a {@link ThreadPoolTaskScheduler} for scheduling tasks.
     *
     * @return the {@link ThreadPoolTaskScheduler}
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10); // Adjust pool size as needed
        scheduler.setThreadNamePrefix("DynamicScheduler-");
        return scheduler;
    }
}

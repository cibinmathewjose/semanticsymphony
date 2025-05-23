package org.symphonykernel.config;

import java.sql.Connection;
import java.sql.DriverManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.UnifiedJedis;

/**
 * SymphonyKernelAutoConfiguration is a configuration class that defines beans for various services
 * such as OpenAIAsyncClient, Kernel, database connections, Redis, Azure AI Search, and task scheduling.
 */
@Component
public class SymphonyKernelAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SymphonyKernelAutoConfiguration.class);


    /**
     * Creates a {@link OpenAIAsyncClient} with the endpoint and key specified
     * in the {@link AzureOpenAIConnectionProperties}.
     *
     * @param connectionProperties the {@link AzureOpenAIConnectionProperties}
     * to use
     * @return the {@link OpenAIAsyncClient}
     */
    @Bean
    @ConditionalOnClass(OpenAIAsyncClient.class)
    @ConditionalOnMissingBean
    public OpenAIAsyncClient openAIAsyncClient(
            AzureOpenAIConnectionProperties connectionProperties) {
        Assert.hasText(connectionProperties.getEndpoint(), "Azure OpenAI endpoint must be set");
        Assert.hasText(connectionProperties.getKey(), "Azure OpenAI key must be set");
        return new OpenAIClientBuilder()
                .endpoint(connectionProperties.getEndpoint())
                .credential(new AzureKeyCredential(connectionProperties.getKey()))
                .buildAsyncClient();
    }

    /**
     * Creates a {@link Kernel} with a default
     * {@link com.microsoft.semantickernel.services.AIService} that uses the
     * {@link com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion} with the model id
     * specified in the {@link AzureOpenAIConnectionProperties} as DeploymentName.
     *
     * @param client the {@link OpenAIAsyncClient} to use
     * @param connectionProperties the {@link AzureOpenAIConnectionProperties} containing configuration details
     * @return the {@link Kernel}
     */
    @Bean
    @ConditionalOnClass(Kernel.class)
    @ConditionalOnMissingBean
    public Kernel semanticKernel(OpenAIAsyncClient client,
            AzureOpenAIConnectionProperties connectionProperties) {
        return Kernel.builder()
                .withAIService(ChatCompletionService.class,OpenAIChatCompletion.builder()//TextGenerationService.class,  OpenAITextGenerationService.builder()
                                .withModelId(connectionProperties.getDeploymentName())
                                .withOpenAIAsyncClient(client)
                                .build())
                .build();
    }

    /**
     * Creates a database connection using the provided {@link DBConnectionProperties}.
     *
     * @param con the {@link DBConnectionProperties} containing database connection details
     * @return the {@link Connection}
     */
    @Bean
    @ConditionalOnClass(Connection.class)
    @ConditionalOnMissingBean
    public Connection getConnection(DBConnectionProperties con) {       
        try {
            // Load JDBC driver
            Class.forName(con.getDriverClassName());
            // Get connection
            return DriverManager.getConnection(con.getUrl(), con.getUsername(), con.getPassword());
        } catch (Exception e) {
            LOGGER.error("JDBC Driver class not found: {}", con.getDriverClassName(), e);
            throw new RuntimeException("Failed to load JDBC driver", e);
        }
    }

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

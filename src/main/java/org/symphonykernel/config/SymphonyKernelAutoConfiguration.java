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
     * specified in the {@link AzureOpenAIConnectionProperties} as
     * DeploymentName.
     *
     * @param client the {@link OpenAIAsyncClient} to use
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
    @Bean
    @ConditionalOnClass(SearchIndexClient.class)
    @ConditionalOnMissingBean
        public SearchIndexClient createAiSearch(AzureAISearchConnectionProperties connectionProperties) {       
        return new SearchIndexClientBuilder()
        .endpoint(connectionProperties.getEndpoint())
        .credential( new AzureKeyCredential(connectionProperties.getKey()))
        .buildClient();
    }
     @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10); // Adjust pool size as needed
        scheduler.setThreadNamePrefix("DynamicScheduler-");
        return scheduler;
    }
}

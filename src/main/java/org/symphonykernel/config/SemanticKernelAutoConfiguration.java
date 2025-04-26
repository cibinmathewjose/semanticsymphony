package org.symphonykernel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;

@Component
public class SemanticKernelAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SemanticKernelAutoConfiguration.class);


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
     * {@link com.microsoft.semantickernel.chatcompletion;} with the model id
     * specified in the {@link AzureOpenAIConnectionProperties} as
     * DeploymentName.
     *
     * @param client the {@link OpenAIAsyncClient} to use
     * @return the {@link Kernel}
     */
    @Bean
    public Kernel semanticKernel(OpenAIAsyncClient client,
            AzureOpenAIConnectionProperties connectionProperties) {
        return Kernel.builder()
                .withAIService(ChatCompletionService.class,OpenAIChatCompletion.builder()//TextGenerationService.class,  OpenAITextGenerationService.builder()
                                .withModelId(connectionProperties.getDeploymentName())
                                .withOpenAIAsyncClient(client)
                                .build())
                .build();
    }
}

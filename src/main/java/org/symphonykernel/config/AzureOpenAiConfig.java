package org.symphonykernel.config;

import java.time.Duration;

import org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAIClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.core.http.HttpClient;
import com.azure.core.util.HttpClientOptions;

/** Configuration class for Azure OpenAI client customization. */
@Configuration
public class AzureOpenAiConfig {

	/** Customizes the Azure OpenAI client with a 5-minute response timeout. 
	 * @return the customizer bean
	 */
	@Bean
	public AzureOpenAIClientBuilderCustomizer responseTimeoutCustomizer() {
		return openAiClientBuilder -> {
			HttpClientOptions clientOptions = new HttpClientOptions()
					.setResponseTimeout(Duration.ofMinutes(5));
			openAiClientBuilder.httpClient(HttpClient.createDefault(clientOptions));
		};
	}

}

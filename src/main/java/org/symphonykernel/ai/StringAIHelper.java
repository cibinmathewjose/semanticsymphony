package org.symphonykernel.ai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.config.AzureOpenAIConnectionProperties;
import org.symphonykernel.core.IAIClient;

import reactor.core.publisher.Flux;

/**
 * StringAI implementation of the AI client interface.
 * <p>
 * This service provides AI capabilities using Spring AI's ChatModel for
 * processing text prompts and generating responses. It is conditionally loaded
 * when the ai.client.library.type property is set to "StringAI".
 * </p>
 */
@Service
@ConditionalOnProperty(
        value = "ai.client.library.type",
        havingValue = "StringAI"
)
public class StringAIHelper extends AIClientBase implements IAIClient {

    private static final Logger logger = LoggerFactory.getLogger(StringAIHelper.class);
    static String DEFAULT_MODEL = "default";
    @Autowired
    private ChatModel myChatModel;
    RetryTemplate retryTemplate;

    /**
     * Constructs a StringAIHelper with the specified connection properties.
     *
     * @param connectionProperties the Azure OpenAI connection configuration
     */
    public StringAIHelper(AzureOpenAIConnectionProperties connectionProperties) {
        super(connectionProperties);
        logger.info("AI processor : StringAI");
    }

    /**
     * Evaluates a prompt without a system message.
     *
     * @param prompt the user prompt to evaluate
     * @return the AI-generated response text
     */
    @Override
    public String evaluatePrompt(String prompt) {
        return execute(new LLMRequest(prompt, "", null, null));
    }

    @Override
    public Flux<String> streamEvaluatePrompt(String prompt) {

        return streamExecute(new LLMRequest(prompt, "", null, null));
    }

    /**
     * Executes the given LLM request by building a prompt and invoking the chat
     * model.
     *
     * @param request the LLM request containing system message, user prompt,
     *                tools, and model information
     * @return the AI-generated response text
     * @throws RuntimeException if the prompt cannot be created or the chat model
     *                          call fails
     */
    @Override
    @SuppressWarnings("unchecked")
    public String execute(LLMRequest request) {

        return (String) processPromptString(request, this::callLLM);
    }

    private String callLLM(LLMRequest request) {
         ChatClientRequestSpec client = getClient(request);
        return client.call().content();      

    }

    private Flux<String> callLLMAsync(LLMRequest request) {
         ChatClientRequestSpec client = getClient(request);
        return client.stream().content();      

    }
    @Override
    public Flux<String> streamExecute(LLMRequest request) {
        return (Flux<String>) processPromptString(request, this::callLLMAsync);
    }

    private Prompt createPrompt(String systemPrompt, String userInput, String model) {

        List<Message> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }

        if (userInput != null && !userInput.isBlank()) {
            messages.add(new UserMessage(userInput));
        }

        if (messages.isEmpty()) {
            logger.warn("Both system prompt and user input are empty");
            throw new RuntimeException("Cannot create prompt with empty messages");
        }

        var openAiChatOptions = resolveOptions(model);
        return new Prompt(messages, openAiChatOptions);

    }

    private ChatClientRequestSpec getClient(LLMRequest request) {
        Prompt prompt = createPrompt(request.getSystemMessage(), request.getUserPrompt(), request.getModelName());
        var client = getClient(prompt, request.getTools());
        return client;
    }

    private ChatClientRequestSpec getClient(Prompt prompt, Object[] tools) {
        if (myChatModel == null) {
            throw new IllegalStateException("ChatModel is not initialized");
        }
        var client = ChatClient.create(myChatModel).prompt(prompt);
        if (tools != null && tools.length > 0) {
            logger.info("Processing with tools: {}", tools.length);
            client = client.tools(tools);
        }
        return client;
    }

    private AzureOpenAiChatOptions resolveOptions(String modelName) {
        if (modelName == null || modelName.isBlank() || modelName.equalsIgnoreCase(DEFAULT_MODEL)) {
            // no override – use model & options configured on the ChatModel bean
            modelName = conProperties.getDeploymentName();
        }

        Integer maxCompletionTokens = conProperties.getMaxCompletionTokens(modelName);
        Integer maxTokens = conProperties.getMaxTokens(modelName);
        Double temperature = conProperties.getTemperature(modelName);
        var builder = AzureOpenAiChatOptions.builder().deploymentName(modelName);

        if (maxCompletionTokens != null) {
            builder.maxCompletionTokens(maxCompletionTokens);
        } else if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }

        if (temperature != null) {
            builder.temperature(temperature);
        }

        return builder.build();
    }

    /**
     * Processes image input with a system message.
     * <p>
     * Note: This is a placeholder implementation. Image processing
     * functionality needs to be implemented based on StringAI capabilities.
     * </p>
     *
     * @param systemMessage the system-level instructions
     * @param base64Image the base64-encoded image data
     * @return a placeholder response message
     */
    @Override
    public String processImage(String systemMessage, String base64Image) {

        byte[] imageBytes = Base64.getDecoder().decode(base64Image);

        var userMessage = UserMessage.builder()
                .text(systemMessage)
                .media(new Media(MimeTypeUtils.APPLICATION_OCTET_STREAM, new ByteArrayResource(imageBytes)))
                .build();

        var client = getClient(new Prompt(userMessage), null);
        return client.call().content();
    }

}
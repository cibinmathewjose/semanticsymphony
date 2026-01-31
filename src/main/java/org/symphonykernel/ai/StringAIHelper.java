package org.symphonykernel.ai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.symphonykernel.config.AzureOpenAIConnectionProperties;
import org.symphonykernel.core.IAIClient;

import io.micrometer.observation.ObservationRegistry;

/**
 * StringAI implementation of the AI client interface.
 * <p>
 * This service provides AI capabilities using Spring AI's ChatModel
 * for processing text prompts and generating responses. It is conditionally
 * loaded when the ai.client.library.type property is set to "StringAI".
 * </p>
 */
@Service
@ConditionalOnProperty(
    value = "ai.client.library.type",
    havingValue = "StringAI"
)
public class StringAIHelper extends AIClientBase implements IAIClient {

    private static final Logger logger = LoggerFactory.getLogger(StringAIHelper.class);
    static String DEFAULT_MODEL="default";
    @Autowired
    private ChatModel myChatModel;
    RetryTemplate retryTemplate ;   
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
        return execute(null, prompt);
    }
    
    public String execute(String systemPrompt, String userInput) {
       
        return processPromptString(systemPrompt, userInput,null, null);
    }
    
    @Override
    public String execute(String systemMessage, String userPrompt, Object[] tools) {         
        return processPromptString(systemMessage, userPrompt, tools, null);
    }
    /**
     * Executes an AI query with optional system prompt and user input.
     *
     * @param systemPrompt the system-level instructions (can be null)
     * @param userInput the user's input message
     * @return the AI-generated response text
     * @throws IllegalStateException if the chat model is not initialized
     * @throws RuntimeException if the AI service call fails
     */
    public String execute(String systemPrompt, String userInput,String model) {
       
        return processPromptString(systemPrompt, userInput,null, model);
    }

   

    /**
     * Processes the prompt by building a message list and calling the chat model.
     *
     * @param systemPrompt the system-level instructions (can be null or empty)
     * @param userInput the user's input message (can be null or empty)
     * @return the AI-generated response text
     * @throws RuntimeException if the chat model call fails
     */
    @Override
    public String execute(String systemPrompt, String userInput,  Object[] tools, String model) {     
       
        Prompt prompt = createPrompt(systemPrompt, userInput, model);
        return processWithTools(prompt,tools);
          
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
           
                var openAiChatOptions = resolveOptions( model);
                return new Prompt(messages, openAiChatOptions);
           
       
    }
    
    

    private String processWithTools(Prompt prompt, Object[] tools) {
        if (myChatModel == null) {
            throw new IllegalStateException("ChatModel is not initialized");
        }
        var client = ChatClient.create(myChatModel).prompt(prompt);
        if(tools != null && tools.length > 0) {
            logger.info("Processing with tools: {}", tools.length); 
            client = client.tools(tools);
        }
            return client.call() .content();
    }
    

   

    private AzureOpenAiChatOptions resolveOptions(String modelName) {
        if (modelName == null || modelName.isBlank()|| modelName.equalsIgnoreCase(DEFAULT_MODEL)) {
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

        return processWithTools(new Prompt(userMessage), null);
    }
}

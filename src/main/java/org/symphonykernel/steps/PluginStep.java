package org.symphonykernel.steps;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.QueryType;
import org.symphonykernel.ai.AzureOpenAIHelper;
import org.symphonykernel.config.AzureOpenAIConnectionProperties;
import org.symphonykernel.core.IPluginLoader;
import org.symphonykernel.core.IStep;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.ExponentialBackoffOptions;
import com.azure.core.http.policy.RetryOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.implementation.CollectionUtil;
import com.microsoft.semantickernel.orchestration.InvocationContext;
import com.microsoft.semantickernel.orchestration.ToolCallBehavior;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;
import com.microsoft.semantickernel.services.chatcompletion.ChatMessageContent;

/**
 * PluginStep is responsible for executing plugins using Azure OpenAI services.
 * It integrates with the Symphony Kernel to process chat responses and queries.
 */
@Component
public class PluginStep implements IStep {

    private static final Logger logger = LoggerFactory.getLogger(PluginStep.class);

    private static final String PROMPT = "PROMPT";
    private final AzureOpenAIHelper openAI;

    @Autowired
    IPluginLoader pluginLoader;

    @Autowired
    ObjectMapper objectMapper;

    ChatCompletionService chat;

    /**
     * Constructs a PluginStep instance with Azure OpenAI helper and connection properties.
     *
     * @param openAI the Azure OpenAI helper instance
     * @param connectionProperties the connection properties for Azure OpenAI
     */
    public PluginStep(AzureOpenAIHelper openAI, AzureOpenAIConnectionProperties connectionProperties) {
        this.openAI = openAI;
        OpenAIAsyncClient client;
        ExponentialBackoffOptions exponentialBackoffOptions = new ExponentialBackoffOptions();
        exponentialBackoffOptions.setMaxRetries(0);
        RetryOptions retryOptions = new RetryOptions(exponentialBackoffOptions);
        
        client = new OpenAIClientBuilder()
                .retryOptions(retryOptions)
                .credential(new AzureKeyCredential(connectionProperties.getKey()))
                .endpoint(connectionProperties.getEndpoint())
                .buildAsyncClient();
        chat = OpenAIChatCompletion.builder()
                .withModelId(connectionProperties.getDeploymentName())
                .withOpenAIAsyncClient(client)
                .build();
    }
    
    


    @Override
    public ChatResponse getResponse(ExecutionContext context) {
    	logger.info("Executing Plugin " + context.getKnowledge().getName() );
         ChatResponse a = new ChatResponse();           
        
        ChatHistory chatHistory = context.getChatHistory();
        Knowledge kb= context.getKnowledge();
        JsonNode paramNode = getParamNode( context.getKnowledge().getData());
        String plugin = paramNode.get("Plugin").asText();
        String systemPrompt = paramNode.get("SystemPrompt").asText();
        logger.info("Parsed Plugin: " + plugin );
       
        if (chatHistory!=null && kb != null) {          
            String params="consider context parameters: "+context.getVariables();
            if( systemPrompt != null)
                 params += ", SystemPrompt:" + systemPrompt;
             logger.info(params);
            chatHistory.addUserMessage(params);
        }      
        else
            throw new IllegalArgumentException("Chat history or knowledge base is null");
        Kernel kernel = pluginLoader.load(chat,plugin);
        if (kernel != null) {
            InvocationContext invocationContext = InvocationContext.builder()
                    .withToolCallBehavior(ToolCallBehavior.allowAllKernelFunctions(true))
                    .build();

            List<ChatMessageContent<?>> messages = chat
                    .getChatMessageContentsAsync(chatHistory, kernel, invocationContext)
                    .block();

            ChatMessageContent<?> result = CollectionUtil.getLastOrNull(messages);

            String msg= result.getContent();
            a.setMessage(msg);
            a.setStatusCode(PROMPT);
        } else {
            return null;
        }
        return a;
    }




    private JsonNode getParamNode(String plugindef) {
        JsonNode paramNode;
        try {
            paramNode = objectMapper.readTree(plugindef);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing plugin definition JSON", e);
            throw new IllegalArgumentException("Invalid plugin definition JSON", e);
        }
        return paramNode;
    }

    @Override
    public JsonNode executeQueryByName(ExecutionContext context) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'executeQueryByName'");
    }
}

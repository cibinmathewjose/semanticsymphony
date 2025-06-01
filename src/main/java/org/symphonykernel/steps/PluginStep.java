
package org.symphonykernel.steps;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.ai.AzureOpenAIHelper;
import org.symphonykernel.config.AzureOpenAIConnectionProperties;
import org.symphonykernel.core.IPluginLoader;
import org.symphonykernel.core.IStep;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.implementation.CollectionUtil;
import com.microsoft.semantickernel.orchestration.InvocationContext;
import com.microsoft.semantickernel.orchestration.ToolCallBehavior;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.chatcompletion.ChatMessageContent;

@Component
public class PluginStep implements IStep {

    private static final Logger logger = LoggerFactory.getLogger(PluginStep.class);

    private static final String PROMPT = "PROMPT";
    private final AzureOpenAIHelper openAI;

    @Autowired
    IPluginLoader pluginLoader;
    
    ChatCompletionService chat;

    public PluginStep(AzureOpenAIHelper openAI,AzureOpenAIConnectionProperties connectionProperties) {
        this.openAI = openAI;
        OpenAIAsyncClient client;
        client = new OpenAIClientBuilder()
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
    	logger.info("Executing Plugin " + context.getKnowledge().getName() + " with " + context.getVariables());
         ChatResponse a = new ChatResponse();           
        Kernel kernel = pluginLoader.load(chat,context.getKnowledge().getData());
        if (kernel != null) {
            InvocationContext invocationContext = InvocationContext.builder()
                    .withToolCallBehavior(ToolCallBehavior.allowAllKernelFunctions(true))
                    .build();

            List<ChatMessageContent<?>> messages = chat
                    .getChatMessageContentsAsync(context.getChatHistory(), kernel, invocationContext)
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

    @Override
    public JsonNode executeQueryByName(ExecutionContext context) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'executeQueryByName'");
    }
}

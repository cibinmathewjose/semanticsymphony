package org.symphonykernel.core;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.implementation.CollectionUtil;
import com.microsoft.semantickernel.orchestration.InvocationContext;
import com.microsoft.semantickernel.orchestration.ToolCallBehavior;
import com.microsoft.semantickernel.plugin.KernelPlugin;
import com.microsoft.semantickernel.plugin.KernelPluginFactory;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;
import com.microsoft.semantickernel.services.chatcompletion.ChatMessageContent;
import java.util.List;

import org.springframework.stereotype.Service;
import org.symphonykernel.plugins.SamplePlugin;
import org.symphonykernel.starter.AzureOpenAIConnectionProperties;

@Service
public class JavaAgent {
	 // Config for OpenAI
   
    private static final String MODEL_ID = System.getenv()
        .getOrDefault("MODEL_ID", "gpt-4o");
  
    ChatCompletionService chat;
    Kernel kernel ;
    public JavaAgent (AzureOpenAIConnectionProperties connectionProperties)
    {
    	OpenAIAsyncClient client;
        client = new OpenAIClientBuilder()
            .credential(new AzureKeyCredential(connectionProperties.getKey()))
            .endpoint(connectionProperties.getEndpoint())
            .buildAsyncClient();
        
    KernelPlugin plugin = KernelPluginFactory.createFromObject(new SamplePlugin(), "RestaurantBooking");
    
   //String pluginDirectory2 = "./plugins";
   // String pluginName2 = "PluginTwo";
   // KernelPlugin plugin2 = KernelPluginFactory.importPluginFromDirectory(pluginDirectory2, pluginName2, null);

    /*
    String yaml = EmbeddedResourceLoader.readFile("petstore.yaml", ExamplePetstoreImporter.class);

    KernelPlugin plugin = SemanticKernelOpenAPIImporter
       .builder()
       .withPluginName("petstore")
       .withSchema(yaml)
       .withServer("http://localhost:8090/api/v3")
       .build();

    */
   chat = OpenAIChatCompletion.builder()
        .withModelId(MODEL_ID)
        .withOpenAIAsyncClient(client)
        .build();

    kernel = Kernel.builder()
        .withPlugin(plugin)
        .withAIService(ChatCompletionService.class, chat)
        .build();
    }
    public String chat(ChatHistory chatHistory, String question) {
    	
    	if(chatHistory==null)
    		chatHistory=new ChatHistory();
        chatHistory.addUserMessage(question);
        InvocationContext invocationContext = InvocationContext.builder()
            .withToolCallBehavior(ToolCallBehavior.allowAllKernelFunctions(true))
            .build();

        List<ChatMessageContent<?>> messages = chat
            .getChatMessageContentsAsync(chatHistory, kernel, invocationContext)
            .block();

        ChatMessageContent<?> result = CollectionUtil.getLastOrNull(messages);

//        chatHistory.addAssistantMessage(result.getContent());

        return result.getContent();
    }


}

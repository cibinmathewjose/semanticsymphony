package org.symphonykernel.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.symphonykernel.config.AzureOpenAIConnectionProperties;
import org.symphonykernel.config.Constants;
import org.symphonykernel.core.IAIClient;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.assistants.AssistantsClient;
import com.azure.ai.openai.assistants.AssistantsClientBuilder;
import com.azure.ai.openai.assistants.models.Assistant;
import com.azure.ai.openai.assistants.models.AssistantCreationOptions;
import com.azure.ai.openai.assistants.models.AssistantThread;
import com.azure.ai.openai.assistants.models.AssistantThreadCreationOptions;
import com.azure.ai.openai.assistants.models.CreateRunOptions;
import com.azure.ai.openai.assistants.models.MessageContent;
import com.azure.ai.openai.assistants.models.MessageRole;
import com.azure.ai.openai.assistants.models.MessageTextContent;
import com.azure.ai.openai.assistants.models.PageableList;
import com.azure.ai.openai.assistants.models.RunStatus;
import com.azure.ai.openai.assistants.models.ThreadMessage;
import com.azure.ai.openai.assistants.models.ThreadMessageOptions;
import com.azure.ai.openai.assistants.models.ThreadRun;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.KeyCredential;
import com.microsoft.semantickernel.services.chatcompletion.ChatMessageContent;
import com.microsoft.semantickernel.services.chatcompletion.message.ChatMessageImageContent;

@Service

@Component
@ConditionalOnProperty(
    value = "ai.client.library.type",
    havingValue = "AzureOpenAI"
)
public class AzureOpenAIHelper extends AIClientBase implements IAIClient {

    private static final Logger logger = LoggerFactory.getLogger(AzureOpenAIHelper.class);


     double temperature;
    String name;
    private static final String NONE = "NONE";
    private AssistantsClient client;
    OpenAIClient opnAIClient;
    private String deploymentOrModelId = "gpt-4o";
    String defaultsystemPrompt="You are a helpful AI assistant that helps people find prepare well formatted response strictly based on the provided data and the context information as the user prompt. strictly answer the question based on the provided context including any json data provided. Never skip any data available in the context or json data provided. If the context does not contain the information needed to answer the question, respond with \"I do not have enough information to process this request.\"";
    
    private int maxProcessingTime=300;

    /**
     * Constructs an AzureOpenAIHelper instance with the specified connection properties.
     *
     * @param connectionProperties the Azure OpenAI connection properties, including API key, endpoint, and other settings
     */
    public AzureOpenAIHelper( AzureOpenAIConnectionProperties connectionProperties) {
        super(connectionProperties);
        logger.info("AI processor : AzureOpenAI");
        temperature=connectionProperties.getTemperature();
        name=connectionProperties.getName();//"Assistant394"
        maxProcessingTime=connectionProperties.getMaxProcessingTime();
        if(maxProcessingTime<20)
        	maxProcessingTime=20;
       
        if(connectionProperties.getDeploymentName()!=null && !connectionProperties.getDeploymentName().isEmpty())
        deploymentOrModelId=connectionProperties.getDeploymentName();//"gpt-4o"

       
        if(temperature<0||temperature >2)
        	temperature=0.0; 
        client = new AssistantsClientBuilder()
                .credential(new KeyCredential(connectionProperties.getKey()))
                .endpoint(connectionProperties.getEndpoint())
                .buildClient();

        opnAIClient = new OpenAIClientBuilder()
                 .endpoint(connectionProperties.getEndpoint())
                 .credential(new AzureKeyCredential(connectionProperties.getKey()))
                 .buildClient();       
    }

    /**
     * Processes an image along with a prompt using the OpenAI API.
     *
     * @param prompt      the text prompt to send to the OpenAI API
     * @param base64Image the base64-encoded image to include in the request
     * @return the response from the OpenAI API as a string
     */
    public String processImage(String prompt, String base64Image) {
         // Retrieve endpoint and API key from environment variables
        
         String deploymentName = "gpt-4o";
 
         // Simulate chat interaction
         List<ChatRequestMessage> prompts = new ArrayList<>();
         prompts.add(new ChatRequestUserMessage(prompt));
         ChatMessageImageContent<String> imageContent = new ChatMessageImageContent<>(
                 "IMAGE_URL", "data:image/png;base64," + base64Image, null);
        List<ChatMessageContent<?>> imageContents = new ArrayList<>();
         imageContents.add(imageContent);
         prompts.add(new ChatRequestUserMessage(imageContent.getContent()));
         // Add any additional messages as needed
 
        ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
        //.maxCompletionTokens(500)  // Required for reasoning models
        .setMaxTokens(800)
        .setTemperature(temperature)
        .setTopP(0.95)
        .setFrequencyPenalty((double)0)
        .setPresencePenalty((double)0)
        .setStop(null);

    // Print the response
    try {
        ChatCompletions chatCompletions = opnAIClient.getChatCompletions(deploymentName, options);
        return chatCompletions.toJsonString();
      }  catch (Exception e) {
        logger.error("Error: " + e.getMessage());
      }
    return null;
    }
    /**
     * Processes a system prompt and user prompt.
     * 
     * @param systemPrompt the system prompt to provide context for the assistant
     * @param userPrompt   the user prompt containing the question or task
     * @return the response from the assistant
     */
    @Override
    public String execute (String systemPrompt,String userPrompt, Object[] tools,String model) {        

        Assistant assistant = getAssistant(systemPrompt);
        logger.info("Assistant created : "+assistant.getName());
        try {
            // Emulating user request
            ThreadRun threadAndRun = createThreadAndRun(assistant.getId(),userPrompt);
            try {
                waitOnRun(threadAndRun, threadAndRun.getThreadId());
            } catch (InterruptedException e) {
                logger.warn("InterruptedException", e);
            }
            return getJsonArrayString(assistant, threadAndRun);
           
        }
        catch (Exception e) {
            // Handle any exceptions that occurred during the Mono execution        	
            logger.error("Error occurred while executing {}", e.getMessage(), e);
            return  e.getMessage(); // Or throw the exception if appropriate
        }
    }    
    

    /**
     * Executes a system prompt and user prompt.
     */
    public String execute(String systemPrompt, String question) {
        if ((question == null || question.trim().isEmpty()) && (systemPrompt != null && !systemPrompt.trim().isEmpty())) {
            question = systemPrompt;
        }
        return execute(systemPrompt, question, null, null);
    }

    /**
     * Evaluates a prompt with the given dataset and question.
     * 
     * @param prompt the prompt to evaluate
     * @return the evaluation result
     */
    public String evaluatePrompt(String prompt) {
        try {
           
            // Get response from OpenAI

            String response = execute(null,prompt, null,null);

            // Return the response if valid
            if (response != null && !NONE.equalsIgnoreCase(response.trim())) {
                return response.trim();
            }
        } catch (Exception e) {
            // Log the exception for debugging
            logger.error("Error occurred while evaluating prompt: {}", e.getMessage(), e);
        }
        return null;
    }
   
    /**
     * Asynchronously evaluates a prompt with the given dataset and question.
     * 
     * @param prompt the prompt to evaluate
     * @return a CompletableFuture with the evaluation result
     */
    @Async
    private CompletableFuture<String> evaluatePromptAsync(String prompt) {
        String traceId = MDC.get(Constants.LOGGER_TRACE_ID);
        return CompletableFuture.supplyAsync(() -> {
            MDC.put(Constants.LOGGER_TRACE_ID, traceId);
            try {
            return evaluatePrompt(prompt);
            } finally {
            MDC.clear();
            }
        });
    }
    
    private String getJsonArrayString(Assistant assistant, ThreadRun threadAndRun) {
        StringBuilder stringBuilder = new StringBuilder();
         PageableList<ThreadMessage> messages = client.listMessages(threadAndRun.getThreadId());
         List<ThreadMessage> data = messages.getData();
         for (int i = 0; i < data.size(); i++) {
             ThreadMessage dataMessage = data.get(i);
             MessageRole role = dataMessage.getRole();
             if(role.equals(role.ASSISTANT))
             {
             for (MessageContent messageContent : dataMessage.getContent()) {
                 MessageTextContent messageTextContent = (MessageTextContent) messageContent;
                 logger.info(i + ": Role = " + role + ", content = " + messageTextContent.getText().getValue());                
                 stringBuilder.append(messageTextContent.getText().getValue());
                 stringBuilder.append(System.lineSeparator());
             }
             }
             else
             {
            	logger.info(i + ": Role = " + role + ", content = " +dataMessage.getContent());   
             }
             stringBuilder.append(System.lineSeparator());
         }

         // Clean up
         client.deleteAssistant(assistant.getId());
         return stringBuilder.toString();
    }

    private Assistant getAssistant(String systemPrompt) {
        if(systemPrompt==null || systemPrompt.trim().isEmpty())
           return createAssistant(defaultsystemPrompt);;    
       return createAssistant(systemPrompt);
    }

    private Assistant createAssistant(String systemPrompt) {
        AssistantCreationOptions assistantCreationOptions = new AssistantCreationOptions(deploymentOrModelId)
                 .setName(name)
                 .setToolResources(null)
                 .setInstructions(systemPrompt) 
                 .setTemperature(temperature)
                 .setTopP(0.1);

         Assistant assistant = client.createAssistant(assistantCreationOptions);
        return assistant;
    }
   
    private ThreadRun createThreadAndRun(String assistantId, String userMessage) {
        AssistantThread thread = client.createThread(new AssistantThreadCreationOptions());
        return submitMessage(assistantId, thread.getId(), userMessage);
    }
    
    private ThreadRun createThreadAndRun(String assistantId, String userMessage,String imageBase64) {
        AssistantThread thread = client.createThread(new AssistantThreadCreationOptions());
        return submitImageMessage(assistantId, thread.getId(), userMessage,imageBase64);
    }
   
    private ThreadRun waitOnRun(ThreadRun run, String threadId) throws InterruptedException {
        // Poll the Run in a loop
    	int sec=0;
        while (run.getStatus() == RunStatus.QUEUED || run.getStatus() == RunStatus.IN_PROGRESS) {
            String runId = run.getId();
            run = client.getRun(threadId, runId);
           if(sec>maxProcessingTime)
           {
             logger.warn("Max processing time exceeded. Exiting wait loop.");   
             break;
          }
         logger.info("Run Sec : "+sec++ +"  ID: " + runId + ", Status: " + run.getStatus());
         Thread.sleep(1000); // Sleep for 1 second before polling again
        }
        return run;
    }

    private ThreadRun submitMessage(String assistantId, String threadId, String userMessage) {
        // Add the Message to the thread
        ThreadMessage threadMessage = client.createMessage(threadId,
                new ThreadMessageOptions(MessageRole.USER, userMessage));
        // Create a Run. You must specify both the Assistant and the Thread.
        return client.createRun(threadId, new CreateRunOptions(assistantId));
    }

    private ThreadRun submitImageMessage(String assistantId, String threadId, String userMessage, String imageBase64) {
        // Add the Message to the thread
        ThreadMessage threadMessage = client.createMessage(threadId,
                new ThreadMessageOptions(MessageRole.USER, userMessage));
        // Process the image data
        ThreadMessage imageMessage = client.createMessage(threadId,
                new ThreadMessageOptions(MessageRole.USER, "Image Data (Base64): " + imageBase64));
        // Create a Run. You must specify both the Assistant and the Thread.
        return client.createRun(threadId, new CreateRunOptions(assistantId));
    }

    @Override
    public String execute(String systemMessage, String userPrompt, Object[] tools) {
        return execute(systemMessage, userPrompt, tools, null);
    }

    @Override
    public String execute(String systemMessage, String userPrompt, String model) {
        return execute(systemMessage, userPrompt, null, model);
    }
}
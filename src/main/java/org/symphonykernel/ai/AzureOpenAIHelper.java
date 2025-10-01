package org.symphonykernel.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.symphonykernel.config.AzureOpenAIConnectionProperties;
import org.symphonykernel.config.Constants;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.orchestration.InvocationContext;
import com.microsoft.semantickernel.orchestration.PromptExecutionSettings;
import com.microsoft.semantickernel.services.ServiceNotFoundException;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;
import com.microsoft.semantickernel.services.chatcompletion.ChatMessageContent;
import com.microsoft.semantickernel.services.chatcompletion.message.ChatMessageImageContent;

import reactor.core.publisher.Mono;
/**
 * AzureOpenAIHelper is a service class that interacts with the Azure OpenAI API
 * using the Microsoft Semantic Kernel library. It provides methods to send prompts
 * and retrieve responses from the OpenAI service.
 * 
 * <p>This class includes synchronous and asynchronous methods for interacting with
 * the OpenAI API, as well as utilities for preparing and evaluating prompts.
 * 
 * <p>Key Features:
 * <ul>
 *   <li>Send questions to the OpenAI service and retrieve responses.</li>
 *   <li>Support for asynchronous operations using CompletableFuture.</li>
 *   <li>Dynamic prompt evaluation with placeholder replacement.</li>
 * </ul>
 * 
 * <p>Dependencies:
 * <ul>
 *   <li>Microsoft Semantic Kernel library for OpenAI integration.</li>
 *   <li>Spring Framework for asynchronous processing and dependency injection.</li>
 *   <li>SLF4J for logging.</li>
 * </ul>
 * 
 * <p>Usage:
 * <pre>
 * {@code
 * Kernel kernel = ...; // Initialize the Semantic Kernel
 * AzureOpenAIHelper helper = new AzureOpenAIHelper(kernel);
 * String response = helper.ask("What is the capital of France?");
 * }
 * </pre>
 * 
 * <p>Thread Safety:
 * <ul>
 *   <li>This class is designed to be used as a Spring-managed singleton bean.</li>
 *   <li>Asynchronous methods are thread-safe and leverage CompletableFuture for concurrency.</li>
 * </ul>
 * 
 * <p>Note:
 * <ul>
 *   <li>Ensure that the Kernel instance is properly configured with the required services.</li>
 *   <li>Handle null or empty responses appropriately in the client code.</li>
 * </ul>
 * 
 * <p>Author: Cibin Jose
 * <p>Version: 1.0
 */
@Service
public class AzureOpenAIHelper {

    private static final Logger logger = LoggerFactory.getLogger(AzureOpenAIHelper.class);

     Kernel kernel;
     int maxtokens;
     double temperature;
     int maxInputLength;
    String name;
    private static final String NONE = "NONE";
    private static  String key = "";
    private static  String endpoint = "";
    private AssistantsClient client;
    OpenAIClient opnAIClient;
    private String deploymentOrModelId = "gpt-4o";
    String defaultsystemPrompt="You are a helpful AI assistant that helps people find prepare well formatted response strictly based on the provided data and the context information as the user prompt. strictly answer the question based on the provided context including any json data provided. Never skip any data available in the context or json data provided. If the context does not contain the information needed to answer the question, respond with \"I do not have enough information to process this request.\"";
    
    /**
     * Constructs an AzureOpenAIHelper instance with the specified kernel and connection properties.
     *
     * @param kernel               the kernel instance used for Semantic Kernel operations
     * @param connectionProperties the Azure OpenAI connection properties, including API key, endpoint, and other settings
     */
    public AzureOpenAIHelper(Kernel kernel, AzureOpenAIConnectionProperties connectionProperties) {
        this.kernel = kernel;
        maxtokens=connectionProperties.getMaxTokens();
        temperature=connectionProperties.getTemperature();
        maxInputLength=connectionProperties.getMaxInputLength();
        name=connectionProperties.getName();//"Assistant394"
        if(connectionProperties.getDeploymentName()!=null && !connectionProperties.getDeploymentName().isEmpty())
        deploymentOrModelId=connectionProperties.getDeploymentName();//"gpt-4o"

        if(maxtokens<160||maxtokens>16000)
        	maxtokens=1000;
        if(temperature<0||temperature >2)
        	temperature=0.0; 
            key=connectionProperties.getKey();
            endpoint=connectionProperties.getEndpoint();
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
     * Asks a question and retrieves a response from the OpenAI service.
     *
     * @param question the question to ask
     * @return a Mono containing a list of ChatMessageContent objects representing the response
     */
    private Mono<List<ChatMessageContent<?>>> askQuestion(String question ) {

        ChatHistory chathistory = new ChatHistory();
        chathistory.addUserMessage(question);        
        try {
            return kernel
                    .getService(ChatCompletionService.class)
                    .getChatMessageContentsAsync(chathistory, kernel,
                            InvocationContext.builder()
                                    .withPromptExecutionSettings(
                                            PromptExecutionSettings.builder()
                                                    .withMaxTokens(maxtokens)
                                                    .withTemperature(temperature)
                                                    .withTopP(.1)
                                                    .build())
                                    .build());

        } catch (ServiceNotFoundException e) {
            logger.error("Error occurred while executing prompt: {}", e.getMessage(), e);
            return Mono.empty(); // Return an empty Mono in case of an exception
        }
        
    }
    
    /**
     * Calls the OpenAI API with a prompt and an optional base64-encoded image.
     *
     * @param prompt      the text prompt to send to the OpenAI API
     * @param base64Image the base64-encoded image to include in the request (optional)
     * @return the response from the OpenAI API as a string
     */
    public String callOpenAIAPI(String prompt, String base64Image) {
        try {
            // Define the API endpoint
            String apiUrl = endpoint+"openai/deployments/gpt-4o/chat/completions?api-version=2025-01-01-preview";

            // Prepare the JSON payload
            String payload = String.format(
                    "{\"messages\":[{\"role\":\"system\",\"content\":[{\"type\":\"text\",\"text\":\"You are an AI assistant that helps people find information.\"}]},{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"\\n\"},{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,%s\"}},{\"type\":\"text\",\"text\":\"%s\"}]}],\"temperature\":0.7,\"top_p\":0.95,\"frequency_penalty\":0,\"presence_penalty\":0,\"max_tokens\":800,\"stop\":null,\"stream\":true}",
                    base64Image,
                    prompt
            );

            // Create the HTTP client
            HttpClient client = HttpClient.newHttpClient();

            // Build the HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("accept", "application/json")
                    .header("accept-language", "en-US,en;q=0.9")
                    .header("api-key", key)
                    .header("content-type", "application/json")
                    .header("priority", "u=1, i")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            // Send the request and get the response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Return the response body
            String result = response.body();
            if (result == null || result.isEmpty()) {
                return "No response received from OpenAI API.";
            }
            String[] dataItems = result.split("data:");
            ObjectMapper mapper = new ObjectMapper();
            StringBuilder parsedContent = new StringBuilder();

            StringBuilder contentBuilder = new StringBuilder();
            for (String item : dataItems) {
                try {
                    JsonNode node = mapper.readTree(item);

                    node.get("choices").forEach(choice -> {
                        JsonNode delta = choice.get("delta");
                        if (delta != null && delta.has("content")) {
                            contentBuilder.append(delta.get("content").asText());
                        }
                    });
                } catch (Exception e) {
                    logger.error("Error parsing JSON data: {}", e.getMessage());
                    continue;
                }

            }

            return contentBuilder.toString().replace("```json", "").replace("```", "");

        } catch (Exception e) {
            e.printStackTrace();
            return "Error occurred: " + e.getMessage();
        }
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
    private String processPrompt(String systemPrompt,String userPrompt) {
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
    private String process(String systemPrompt,String userPrompt) {
        
        int len =systemPrompt.length();
        int len2 =userPrompt.length();
      
        if(maxInputLength>4000 && (len>maxInputLength || len2>maxInputLength))
            {
            	return ("Data length execeeded "+maxInputLength+" chars limit");            	 
            }        
        logger.info("Processing data with LLM <!PromptHead!>\r\n {} \r\n  <!SplitPromptHere!> {}\r\n",systemPrompt,userPrompt);
     
        return processPrompt(systemPrompt,userPrompt);
    }
    /**
     * Asks a question and gets a response.
     * 
     * @param question the question to ask
     * @return the response
     */
    public String ask(String question) 
    {
       return execute("",question);
    }



    /**
     * Executes a system prompt and user prompt.
     *
     * @param systemPrompt the system prompt to provide context for the assistant
     * @param question     the user prompt containing the question or task
     * @return the execution result as a string
     */
    public String execute(String systemPrompt, String question) {
        if (question == null || question.trim().isEmpty())
            return "Please provide a valid question. if multiple prompts in one go, please start with <!PromptHead!> and use <!SplitPromptHere!> to split each section";
        String splitter = "<!SplitPromptHere!>";
        String head = "<!PromptHead!>";
        String finalFormating = "<!FinalResultFormat!>";
        if (question.indexOf(splitter) > 0) {
            String[] parts = question.split(splitter);
            StringBuilder finalResponse = new StringBuilder();
            int i = 0;
            final String basePrompt;
            String finalFormatingPrompt = "";
            if (parts[0].indexOf(head) >= 0) {
                i = 1;
                String header = parts[0];
                if (parts[0].indexOf(finalFormating) >= 0) {
                    basePrompt = header.substring(header.indexOf(head) + head.length(), header.indexOf(finalFormating)) + System.lineSeparator();
                    finalFormatingPrompt = header.substring(header.indexOf(finalFormating) + finalFormating.length()) + System.lineSeparator();
                } else
                    basePrompt = parts[0].substring(header.indexOf(head) + head.length()) + System.lineSeparator();
            } else
                basePrompt = systemPrompt;

            // Create a list to hold the futures
            List<CompletableFuture<String>> futures = new ArrayList<>();
            String traceId = MDC.get(Constants.LOGGER_TRACE_ID);
            logger.info("Processing {} prompts in parallel with traceId {}", parts.length, traceId);

            // Process all parts in parallel
            for (; i < parts.length; i++) {
                String part = parts[i];
                // Submit each part for parallel processing
                futures.add(CompletableFuture.supplyAsync(() -> {
                    MDC.put(Constants.LOGGER_TRACE_ID, traceId);
                    try {
                        return process(basePrompt, part);
                    } finally {
                        MDC.clear();
                    }
                }));
            }

            // Wait for all futures to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Collect all results in order
            for (CompletableFuture<String> future : futures) {
                try {
                    String response = future.get();
                    if (response != null) {
                        finalResponse.append(response);
                        finalResponse.append(System.lineSeparator());
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Error processing part in parallel: {}", e.getMessage(), e);
                }
            }
            if (finalFormatingPrompt != null && !finalFormatingPrompt.isEmpty()) {
                return process(finalFormatingPrompt, finalResponse.toString());
            }
            return finalResponse.toString();
        } else {
            return process(systemPrompt, question);
        }
    }

    /**
     * Asynchronously asks a question and gets a response.
     * 
     * @param question the question to ask
     * @return a CompletableFuture with the response
     */
    @Async
    public CompletableFuture<String> askAsync(String question) {
        String traceId = MDC.get(Constants.LOGGER_TRACE_ID);
        return CompletableFuture.supplyAsync(() -> {
            MDC.put(Constants.LOGGER_TRACE_ID, traceId);
            try {
            return ask(question);
            } finally {
            MDC.clear();
            }
        });
    }

    /**
     * Asynchronously evaluates a prompt with the given dataset and question.
     * 
     * @param prompt the prompt to evaluate
     * @return a CompletableFuture with the evaluation result
     */
    @Async
    public CompletableFuture<String> evaluatePromptAsync(String prompt) {
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

    /**
     * Evaluates a prompt with the given dataset and question.
     * 
     * @param prompt the prompt to evaluate
     * @return the evaluation result
     */
    public String evaluatePrompt(String prompt) {
        try {
           
            // Get response from OpenAI

            String response = processPrompt(null,prompt);

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
    
}

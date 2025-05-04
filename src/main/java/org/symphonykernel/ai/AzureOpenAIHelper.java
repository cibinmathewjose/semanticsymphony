
package org.symphonykernel.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.orchestration.InvocationContext;
import com.microsoft.semantickernel.orchestration.PromptExecutionSettings;
import com.microsoft.semantickernel.services.ServiceNotFoundException;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;
import com.microsoft.semantickernel.services.chatcompletion.ChatMessageContent;

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
     
    private static final String DATA_SET = "{{$DATA_SET}}";
    private static final String QUESTION = "{{$QUESTION}}";
    private static final String NONE = "NONE";
    
    /**
     * Constructs an AzureOpenAIHelper instance with the specified kernel.
     * 
     * @param kernel the kernel instance
     */
    public AzureOpenAIHelper(Kernel kernel) {
        this.kernel = kernel;
    }

    private Mono<List<ChatMessageContent<?>>> askQuestion(String question) {

        ChatHistory chathistory = new ChatHistory();
        chathistory.addUserMessage(question);

        try {
            return kernel
                    .getService(ChatCompletionService.class)
                    .getChatMessageContentsAsync(chathistory, kernel,
                            InvocationContext.builder()
                                    .withPromptExecutionSettings(
                                            PromptExecutionSettings.builder()
                                                    .withMaxTokens(2048)
                                                    .withTemperature(0.5)
                                                    .build())
                                    .build());

        } catch (ServiceNotFoundException e) {
            logger.error("Error occurred while executing prompt: {}", e.getMessage(), e);
            return Mono.empty(); // Return an empty Mono in case of an exception
        }
    }

    /**
     * Asks a question and gets a response.
     * 
     * @param question the question to ask
     * @return the response
     */
    public String ask(String question) {
        try {
            List<ChatMessageContent<?>> responses = askQuestion(question).block();
            if (responses != null && !responses.isEmpty()) {
                ChatMessageContent<?> lastResponse = responses.get(responses.size() - 1);
                return lastResponse != null ? lastResponse.getContent() : null; // Handle potential null content
            } else {
                return null; // Or handle the empty response case as needed
            }
        } catch (RuntimeException e) {
            // Handle any exceptions that occurred during the Mono execution
            logger.error("Error occurred while executing Mono: {}", e.getMessage(), e);
            return null; // Or throw the exception if appropriate
        }
    }

    /**
     * Executes a system prompt and user prompt.
     * 
     * @param systemPrompt the system prompt
     * @param userPrompt the user prompt
     * @return the execution result
     */
    public String execute(String systemPrompt, String userPrompt) {
        return ask(systemPrompt + " " + userPrompt);
    }

    /**
     * Asynchronously asks a question and gets a response.
     * 
     * @param question the question to ask
     * @return a CompletableFuture with the response
     */
    @Async
    public CompletableFuture<String> askAsync(String question) {
        return CompletableFuture.supplyAsync(() -> ask(question));
    }

    /**
     * Asynchronously evaluates a prompt with the given dataset and question.
     * 
     * @param prompt the prompt to evaluate
     * @param jsonString the dataset in JSON format
     * @param question the question to ask
     * @return a CompletableFuture with the evaluation result
     */
    @Async
    public CompletableFuture<String> evaluatePromptAsync(String prompt, String jsonString, String question) {
        return CompletableFuture.supplyAsync(() -> evaluatePrompt(prompt, jsonString, question));
    }

    /**
     * Evaluates a prompt with the given dataset and question.
     * 
     * @param promptToEval the prompt to evaluate
     * @param dataSet the dataset to use
     * @param question the question to ask
     * @return the evaluation result
     */
    public String evaluatePrompt(String promptToEval,String dataSet, String question) {
        try {
            // Prepare the prompt by replacing placeholders
            String prompt = promptToEval
                    .replace(DATA_SET, dataSet)
                    .replace(QUESTION, question);

            // Get response from OpenAI
            String response = ask(prompt);

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

}

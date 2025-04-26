package org.symphonykernel.ai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.orchestration.InvocationContext;
import com.microsoft.semantickernel.orchestration.PromptExecutionSettings;
import com.microsoft.semantickernel.services.ServiceNotFoundException;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;
import com.microsoft.semantickernel.services.chatcompletion.ChatMessageContent;

import reactor.core.publisher.Mono;

@Service
public class AzureOpenAIHelper {

    private static final Logger logger = LoggerFactory.getLogger(AzureOpenAIHelper.class);

     Kernel kernel;
     
    private static final String DATA_SET = "{{$DATA_SET}}";
    private static final String QUESTION = "{{$QUESTION}}";
    private static final String NONE = "NONE";
    
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
            throw new RuntimeException(e);
        }
    }

    public String Ask(String question) {
        try {
            List<ChatMessageContent<?>> responses = askQuestion(question).block();
            if (responses != null && !responses.isEmpty()) {
                ChatMessageContent<?> lastResponse = responses.get(responses.size() - 1);
                return lastResponse.getContent().toString(); // Assuming getContent() returns the message
            } else {
                return null; // Or handle the empty response case as needed
            }
        } catch (RuntimeException e) {
            // Handle any exceptions that occurred during the Mono execution
            e.printStackTrace();
            return null; // Or throw the exception if appropriate
        }
    }

    public String execute(String systemPrompt, String userPrompt) {
        return Ask(systemPrompt + " " + userPrompt);
    }
    public String evaluatePrompt(String promptToEval,String dataSet, String question) {
        try {
            // Prepare the prompt by replacing placeholders
            String prompt = promptToEval
                    .replace(DATA_SET, dataSet)
                    .replace(QUESTION, question);

            // Get response from OpenAI
            String response = Ask(prompt);

            // Return the response if valid
            if (response != null && !NONE.equalsIgnoreCase(response.trim())) {
                return response.trim();
            }
        } catch (Exception e) {
            // Log the exception for debugging
            e.printStackTrace();
        }
        return null;
    }

}

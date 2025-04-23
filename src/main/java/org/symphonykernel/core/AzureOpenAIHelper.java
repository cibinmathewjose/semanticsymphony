package org.symphonykernel.core;

import java.util.List;

import org.springframework.stereotype.Service;
import org.symphonykernel.starter.AzureOpenAIConnectionProperties;

import com.azure.ai.openai.OpenAIAsyncClient;
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
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.KeyCredential;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.orchestration.InvocationContext;
import com.microsoft.semantickernel.orchestration.PromptExecutionSettings;
import com.microsoft.semantickernel.services.ServiceNotFoundException;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.chatcompletion.ChatHistory;
import com.microsoft.semantickernel.services.chatcompletion.ChatMessageContent;

import reactor.core.publisher.Mono;

@Service
public class AzureOpenAIHelper {

    private AssistantsClient client;
    private String deploymentOrModelId = "gpt-4o";

    Kernel kernel;

    public AzureOpenAIHelper(AzureOpenAIConnectionProperties connectionProperties) {
        deploymentOrModelId = connectionProperties.getDeploymentName();
        client = new AssistantsClientBuilder()
                .credential(new KeyCredential(connectionProperties.getKey()))
                .endpoint(connectionProperties.getEndpoint())
                .buildClient();

        OpenAIAsyncClient aClient = new OpenAIClientBuilder()
                .credential(new AzureKeyCredential(connectionProperties.getKey()))
                .endpoint(connectionProperties.getEndpoint())
                .buildAsyncClient();

        ChatCompletionService chat = OpenAIChatCompletion.builder()
                .withModelId(connectionProperties.getDeploymentName())
                .withOpenAIAsyncClient(aClient)
                .build();

        kernel = Kernel
                .builder()
                .withAIService(ChatCompletionService.class, chat)
                .build();

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

    public String getLastMessageSynchronous(String question) {
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
        return getLastMessageSynchronous(systemPrompt + " " + userPrompt);
    }

    public String executeOld(String systemPrompt, String userPrompt) {
        StringBuilder stringBuilder = new StringBuilder();
        AssistantCreationOptions assistantCreationOptions = new AssistantCreationOptions(deploymentOrModelId)
                .setName("Assistant394")
                .setInstructions(systemPrompt)
                .setToolResources(null)
                .setTemperature(1d)
                .setTopP(0.25);

        Assistant assistant = client.createAssistant(assistantCreationOptions);

        // Emulating user request
        ThreadRun threadAndRun = createThreadAndRun(assistant.getId(), userPrompt);
        try {
            waitOnRun(threadAndRun, threadAndRun.getThreadId());
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        PageableList<ThreadMessage> messages = client.listMessages(threadAndRun.getThreadId());
        List<ThreadMessage> data = messages.getData();
        for (int i = 0; i < data.size(); i++) {
            ThreadMessage dataMessage = data.get(i);
            MessageRole role = dataMessage.getRole();
            if (role.equals(role.ASSISTANT)) {
                for (MessageContent messageContent : dataMessage.getContent()) {
                    MessageTextContent messageTextContent = (MessageTextContent) messageContent;

                    String jsonArrayString = messageTextContent.getText().getValue();
                    System.out.println(i + ": Role = " + role + ", content = " + messageTextContent.getText().getValue());
                    stringBuilder.append(messageTextContent.getText().getValue());
                    stringBuilder.append(System.lineSeparator());
                }
            } else {
                System.out.println(i + ": Role = " + role + ", content = " + dataMessage.getContent());
            }
            stringBuilder.append(System.lineSeparator());
        }

        // Clean up
        client.deleteAssistant(assistant.getId());
        return stringBuilder.toString();
    }

    private ThreadRun createThreadAndRun(String assistantId, String userMessage) {
        AssistantThread thread = client.createThread(new AssistantThreadCreationOptions());
        return submitMessage(assistantId, thread.getId(), userMessage);
    }

    private ThreadRun waitOnRun(ThreadRun run, String threadId) throws InterruptedException {
        // Poll the Run in a loop
        while (run.getStatus() == RunStatus.QUEUED || run.getStatus() == RunStatus.IN_PROGRESS) {
            String runId = run.getId();
            run = client.getRun(threadId, runId);
            System.out.println("Run ID: " + runId + ", Run Status: " + run.getStatus());
            Thread.sleep(1000); // Sleep for 1 second before polling again
        }
        return run;
    }

    private ThreadRun submitMessage(String assistantId, String threadId, String userMessage) {
        // Add the Message to the thread
        ThreadMessage threadMessage = client.createMessage(threadId,
                new ThreadMessageOptions(MessageRole.USER, userMessage));
        System.out.printf("Thread Message ID = \"%s\" is created at %s.%n", threadMessage.getId(),
                threadMessage.getCreatedAt());
        System.out.println(
                "Message Content: " + ((MessageTextContent) threadMessage.getContent().get(0)).getText().getValue());

        // Create a Run. You must specify both the Assistant and the Thread.
        return client.createRun(threadId, new CreateRunOptions(assistantId));
    }

}

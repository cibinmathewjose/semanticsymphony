package org.symphonykernel.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;
import org.symphonykernel.starter.AzureOpenAIConnectionProperties;

import java.net.URL;

import com.azure.ai.openai.assistants.*;
import com.azure.ai.openai.assistants.models.*;
import com.azure.core.credential.KeyCredential;
import com.azure.core.util.Configuration;

@Service
public class AzureOpenAIHelper {

    private AssistantsClient client;
    private String deploymentOrModelId = "gpt-4o";
    
    public AzureOpenAIHelper(AzureOpenAIConnectionProperties connectionProperties) {      
    	deploymentOrModelId=connectionProperties.getDeploymentName();
        client = new AssistantsClientBuilder()
                .credential(new KeyCredential(connectionProperties.getKey()))
                .endpoint(connectionProperties.getEndpoint())
                .buildClient();
    }
    public String execute(String systemPrompt,String userPrompt)
    {
    	StringBuilder stringBuilder = new StringBuilder();
    	AssistantCreationOptions assistantCreationOptions = new AssistantCreationOptions(deploymentOrModelId)
                 .setName("Assistant394")
                 .setInstructions(systemPrompt)
                 .setToolResources(null)
                 .setTemperature(1d)
                 .setTopP(0.25);

         Assistant assistant = client.createAssistant(assistantCreationOptions);

         // Emulating user request
         ThreadRun threadAndRun = createThreadAndRun(assistant.getId(),userPrompt);
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
             if(role.equals(role.ASSISTANT))
             {
             for (MessageContent messageContent : dataMessage.getContent()) {
                 MessageTextContent messageTextContent = (MessageTextContent) messageContent;

                 String jsonArrayString = messageTextContent.getText().getValue();
                 System.out.println(i + ": Role = " + role + ", content = " + messageTextContent.getText().getValue());                
                 stringBuilder.append(messageTextContent.getText().getValue());
                 stringBuilder.append(System.lineSeparator());
             }
             }
             else
             {
            	 System.out.println(i + ": Role = " + role + ", content = " +dataMessage.getContent());   
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

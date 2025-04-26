package org.symphonykernel.ai;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.steps.SqlStep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class QueryHandler {

    private static final Logger logger = LoggerFactory.getLogger(QueryHandler.class);

    private final IknowledgeBase knowledgeBaseRepo;
    private final ObjectMapper objectMapper;
    private final AzureOpenAIHelper openAIHelper;
    
    @Autowired
    SqlStep sqlAssistant;

    
    @Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/matchSelectQueryPrompt.text') }")
    private String matchSelectQueryPrompt;

    @Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/getQueryPrompt.text') }")
    private String getQueryPrompt;

    public QueryHandler(IknowledgeBase knowledgeBaseRepo, ObjectMapper objectMapper, AzureOpenAIHelper openAIHelper) {
        this.knowledgeBaseRepo = knowledgeBaseRepo;
        this.objectMapper = objectMapper;
        this.openAIHelper = openAIHelper;
    }

   

    public String matchSelectQuery(String question) {
        try {
            // Fetch knowledge descriptions and convert to JSON string
            Map<String, String> knowledgeDesc = knowledgeBaseRepo.getAllVewDescriptions();
            String jsonString = objectMapper.writeValueAsString(knowledgeDesc);
          
            // Get response from OpenAI
            String response = openAIHelper.evaluatePrompt(matchSelectQueryPrompt,jsonString,question);

            // Process the response if valid
            if (response != null ) {
                String vDef = knowledgeBaseRepo.GetViewDefByName(response);
                if (vDef != null) {
                    return openAIHelper.evaluatePrompt(getQueryPrompt,vDef,question);
                }
            }
        } catch (JsonProcessingException e) {
            // Log the exception for debugging
            e.printStackTrace();
        }
        return null;
    }

}

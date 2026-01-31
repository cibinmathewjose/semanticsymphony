package org.symphonykernel.ai;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.providers.FileContentProvider;
import org.symphonykernel.steps.SqlStep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The QueryHandler class is a Spring Component responsible for handling queries
 * by interacting with a knowledge base and utilizing Azure OpenAI for processing.
 * It integrates with various components such as a knowledge base repository,
 * a file content provider, and an SQL assistant to process and generate responses
 * for user queries.
 * 
 * <p>Key Responsibilities:</p>
 * <ul>
 *   <li>Fetches knowledge descriptions from the knowledge base and converts them to JSON.</li>
 *   <li>Uses Azure OpenAI to evaluate prompts and generate responses based on the input question.</li>
 *   <li>Processes the response to retrieve view definitions and generate SQL queries.</li>
 * </ul>
 * 
 * <p>Dependencies:</p>
 * <ul>
 *   <li>{@link IknowledgeBase} - Interface for interacting with the knowledge base repository.</li>
 *   <li>{@link ObjectMapper} - Used for JSON serialization and deserialization.</li>
 *   <li>{@link IAIClient} - Helper class for interacting with Azure OpenAI services.</li>
 *   <li>{@link SqlStep} - SQL assistant for additional processing.</li>
 *   <li>{@link FileContentProvider} - Provides file content for prompts.</li>
 * </ul>
 * 
 * <p>Methods:</p>
 * <ul>
 *   <li>{@code matchSelectQuery(String question)} - Processes a user question to generate a SQL query
 *       by interacting with the knowledge base and Azure OpenAI.</li>
 * </ul>
 * 
 * <p>Exception Handling:</p>
 * <ul>
 *   <li>Handles {@link JsonProcessingException} during JSON serialization.</li>
 * </ul>
 * 
 * <p>Logging:</p>
 * <ul>
 *   <li>Uses SLF4J Logger for logging debug and error information.</li>
 * </ul>
 */
@Component
public class QueryHandler {

    private static final Logger logger = LoggerFactory.getLogger(QueryHandler.class);

    private final IknowledgeBase knowledgeBaseRepo;
    private final ObjectMapper objectMapper;
    private final IAIClient openAIHelper;
    
    @Autowired
    SqlStep sqlAssistant;


    @Autowired
    private FileContentProvider fileContentProvider;
    //@Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/matchSelectQueryPrompt.text') }")
    //private String matchSelectQueryPrompt;

   // @Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/getQueryPrompt.text') }")
   // private String getQueryPrompt;

    /**
     * Constructs a QueryHandler with the specified dependencies.
     *
     * @param knowledgeBaseRepo the knowledge base repository for fetching knowledge descriptions
     * @param objectMapper the object mapper for JSON serialization and deserialization
     * @param openAIHelper the Azure OpenAI helper for processing prompts and generating responses
     */
    public QueryHandler(IknowledgeBase knowledgeBaseRepo, ObjectMapper objectMapper, IAIClient openAIHelper) {
        this.knowledgeBaseRepo = knowledgeBaseRepo;
        this.objectMapper = objectMapper;
        this.openAIHelper = openAIHelper;
    }

    /**
     * Processes a user question to generate a SQL query by interacting with the knowledge base
     * and Azure OpenAI. It fetches knowledge descriptions, evaluates prompts, and retrieves
     * view definitions to construct the SQL query.
     *
     * @param question the user question to process
     * @param params additional parameters to consider while processing the question
     * @return the generated SQL query, or {@code null} if processing fails
     */
    public String matchSelectQuery(String question, JsonNode params) {
        try {
            // Fetch knowledge descriptions and convert to JSON string
            Map<String, String> knowledgeDesc = knowledgeBaseRepo.getAllVewDescriptions();
            String jsonString = objectMapper.writeValueAsString(knowledgeDesc);          
            
            // Get response from OpenAI
            String prompt = fileContentProvider.prepareMatchSelectQueryPrompt(jsonString, question);
            String response = openAIHelper.evaluatePrompt(prompt);

            // Process the response if valid
            if (response != null ) {
                String vDef = knowledgeBaseRepo.GetViewDefByName(response);
                if (vDef != null) {
                	if(params!=null)
                    	question = "Consider the availabe variables "+params.toString()+question;
                        prompt = fileContentProvider.prepareQueryPrompt(vDef, question);
                    return openAIHelper.evaluatePrompt(prompt);
                }
            }
        } catch (JsonProcessingException e) {
            // Log the exception for debugging
            e.printStackTrace();
        }
        return null;
    }

}

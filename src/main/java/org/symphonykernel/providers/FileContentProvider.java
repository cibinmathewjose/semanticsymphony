package org.symphonykernel.providers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.symphonykernel.ai.KnowledgeGraphBuilder;

import jakarta.annotation.PostConstruct;

/**
 * Provides methods to load file content from the classpath.
 */
@Component
public class FileContentProvider {

	/**
     * Placeholder for dataset in prompts.
     */
    public static final String DATA_SET = "{{$DATA_SET}}";
    /**
     * Placeholder for context variables in prompts.
     */

    public static final String CTX_VAR= "{{$CTX_VAR}}";

    /**
     * Placeholder for question in prompts.
     */
    public static final String QUESTION = "{{$QUESTION}}";

    /**
     * Placeholder for parameter definitions in prompts.
     */
    public static final String PARAM_DEF = "{{$PARAM_DEF}}";

	private static final Logger logger = LoggerFactory.getLogger(FileContentProvider.class);

	
	private String matchKnowledgePromptPath ="prompts/matchKnowledgePrompt.text";
	private String paramParserPromptPath = "prompts/paramParserPrompt.text";
	//@Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/matchSelectQueryPrompt.text') }")
    private String matchSelectQueryPromptPath="prompts/matchSelectQueryPrompt.text";

   // @Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/getQueryPrompt.text') }")
    private String getQueryPromptPath="prompts/getQueryPrompt.text";
	
    private String matchParamsPath = "prompts/matchParams.text";
	/**
	 * Represents a provider for file content.
	 * 
	 * This class provides methods and properties for handling file content prompts.
	 */
	public String paramParserPrompt;	
	
	/**
	 * Prompt for matching knowledge.
	 */
	public String matchKnowledgePrompt;
	
	/**
	 * Prompt for matching select queries.
	 */
	public String matchSelectQueryPrompt;
	
	/**
	 * Prompt for getting query information.
	 */
	public String getQueryPrompt;
	
	/**
	 * Prompt for matching parameters.
	 */

	public String matchParamsPrompt;
	
	/**
     * Initializes the file content provider by loading necessary resources.
     *
     * @throws IOException if an error occurs while loading resources.
     */
	@PostConstruct
	public void initialize() throws IOException {
	    this.matchKnowledgePrompt = loadFileContent(matchKnowledgePromptPath);
	    this.paramParserPrompt= loadFileContent(paramParserPromptPath);
	    this.matchSelectQueryPrompt = loadFileContent(matchSelectQueryPromptPath);
	    this.getQueryPrompt= loadFileContent(getQueryPromptPath);
	    this.getQueryPrompt= loadFileContent(getQueryPromptPath);
		this.matchParamsPrompt= loadFileContent(matchParamsPath);
	}
	
	/**
     * Loads the content of a file from the classpath.
     *
     * @param resourcePath the path to the resource file.
     * @return the content of the file as a string.
     * @throws IOException if the resource is not found or cannot be read.
     */
    public String loadFileContent(String resourcePath) throws IOException {
    	 ClassLoader classLoader = getClass().getClassLoader();
         InputStream inputStream = classLoader.getResourceAsStream(resourcePath);

         if (inputStream == null) {
             throw new IOException("Resource not found on classpath: " + resourcePath);
         }

         try {
             return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
         } finally {
             try {
                 inputStream.close();
             } catch (IOException e) {
                 logger.warn("Error closing input stream for resource: {}", resourcePath, e);
             }
         }
    }
	public String prepareMatchParamsPrompt(String paramDef,String dataset,String question) {
		return matchParamsPrompt.replace(PARAM_DEF, paramDef)
                            .replace(DATA_SET,dataset)
                            .replace(QUESTION, question);
	}
	public String prepareMatchKnowledgePrompt(String jsonString,String question) {
		return matchKnowledgePrompt.replace(DATA_SET, jsonString)
            .replace(QUESTION, question);
	}
	
	public String prepareParamParserPrompt(String jsonString,String question) {
		return paramParserPrompt
                .replace(DATA_SET, jsonString)
                 .replace(QUESTION, question);
	}
	public String prepareMatchSelectQueryPrompt(String jsonString,String question) {
		return matchSelectQueryPrompt
		.replace(DATA_SET, jsonString)
		 .replace(QUESTION, question);
	}
	public String prepareQueryPrompt(String jsonString,String question) {
		return getQueryPrompt
		.replace(DATA_SET, jsonString)
		 .replace(QUESTION, question);
	}
}

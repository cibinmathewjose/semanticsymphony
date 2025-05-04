package org.symphonykernel.providers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Provides methods to load file content from the classpath.
 */
@Component
public class FileContentProvider {
	private static final Logger logger = LoggerFactory.getLogger(FileContentProvider.class);

	
	private String matchKnowledgePromptPath ="prompts/matchKnowledgePrompt.text";
	private String paramParserPromptPath = "prompts/paramParserPrompt.text";
	//@Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/matchSelectQueryPrompt.text') }")
    private String matchSelectQueryPromptPath="prompts/matchSelectQueryPrompt.text";

   // @Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/getQueryPrompt.text') }")
    private String getQueryPromptPath="prompts/getQueryPrompt.text";
	
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
}

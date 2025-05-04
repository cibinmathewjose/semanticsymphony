package org.symphonykernel.providers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import jakarta.annotation.PostConstruct;


@Component
public class FileContentProvider {
	private static final Logger logger = LoggerFactory.getLogger(FileContentProvider.class);

	
	private String matchKnowledgePromptPath ="prompts/matchKnowledgePrompt.text";
	private String paramParserPromptPath = "prompts/paramParserPrompt.text";
	//@Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/matchSelectQueryPrompt.text') }")
    private String matchSelectQueryPromptPath="prompts/matchSelectQueryPrompt.text";

   // @Value("#{ @fileContentProvider.loadFileContent('classpath:prompts/getQueryPrompt.text') }")
    private String getQueryPromptPath="prompts/getQueryPrompt.text";
	
	public String paramParserPrompt;	
	public String matchKnowledgePrompt;
	public String matchSelectQueryPrompt;
	public String getQueryPrompt;
	@PostConstruct
	public void initialize() throws IOException {
	    this.matchKnowledgePrompt = loadFileContent(matchKnowledgePromptPath);
	    this.paramParserPrompt= loadFileContent(paramParserPromptPath);
	    this.matchSelectQueryPrompt = loadFileContent(matchSelectQueryPromptPath);
	    this.getQueryPrompt= loadFileContent(getQueryPromptPath);
	}
	
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

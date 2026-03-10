package org.symphonykernel.providers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	/** Map of prompt key to file path. */
	private Map<String, String> promptPathsMap = new HashMap<>();
	/** Map of prompt key to loaded content. */
	protected Map<String, String> promptsContentMap = new HashMap<>();

	/** Prompt key for matching knowledge. */
	protected static final String MATCH_KNOWLEDGE_PROMPT = "matchKnowledgePrompt";
	/** Prompt key for parameter parsing. */
	protected static final String PARAM_PARSER_PROMPT = "paramParserPrompt";
	/** Prompt key for matching select queries. */
	protected static final String MATCH_SELECT_QUERY_PROMPT = "matchSelectQueryPrompt";
	/** Prompt key for getting queries. */
	protected static final String GET_QUERY_PROMPT = "getQueryPrompt";
	/** Prompt key for matching parameters. */
	protected static final String MATCH_PARAMS_PROMPT = "matchParamsPrompt";
	/** Prompt key for follow-up prompts. */
	protected static final String FOLLOWUP_PROMPT = "followupPrompt";

	/** Ordered list of all prompt keys. */
	protected static List<String> promptKeys = List.of(
		MATCH_KNOWLEDGE_PROMPT,
		PARAM_PARSER_PROMPT,
		MATCH_SELECT_QUERY_PROMPT,
		GET_QUERY_PROMPT,
		MATCH_PARAMS_PROMPT,
		FOLLOWUP_PROMPT
	);

	
	/** @return the prompt paths map */
	public Map<String, String> getPromptPathsMap() {
	    return promptPathsMap;
	}

	/** @param promptPathsMap the prompt paths map to set */
	public void setPromptPathsMap(Map<String, String> promptPathsMap) {
	    this.promptPathsMap = promptPathsMap;
	}

	/** @return the prompts content map */
	public Map<String, String> getPromptsContentMap() {
	    return promptsContentMap;
	}

	/** @param promptsContentMap the prompts content map to set */
	public void setPromptsContentMap(Map<String, String> promptsContentMap) {
	    this.promptsContentMap = promptsContentMap;
	}

	
	
	/**
     * Initializes the file content provider by loading necessary resources.
     *
     * @throws IOException if an error occurs while loading resources.
     */
	@PostConstruct
	public void initialize() throws IOException {
	    for (String key : promptKeys) {
	        String path = "prompts/" + key + ".text";
	        promptPathsMap.put(key, path);
	        promptsContentMap.put(key, loadFileContent(path));
	    }
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
		
	/**
	 * Prepares the match-params prompt with the given values.
	 *
	 * @param paramDef the parameter definition
	 * @param dataset the dataset
	 * @param question the user question
	 * @return the prepared prompt
	 */
	public String prepareMatchParamsPrompt(String paramDef,String dataset,String question) {
		return promptsContentMap.get(MATCH_PARAMS_PROMPT).replace(PARAM_DEF, paramDef)
                            .replace(DATA_SET,dataset)
                            .replace(QUESTION, question);
	}
	/**
	 * Prepares the match-knowledge prompt with the given values.
	 *
	 * @param jsonString the knowledge descriptions JSON
	 * @param question the user question
	 * @param context the context variables
	 * @return the prepared prompt
	 */
	public String prepareMatchKnowledgePrompt(String jsonString,String question, String context) {
		return promptsContentMap.get(MATCH_KNOWLEDGE_PROMPT).replace(DATA_SET, jsonString)
            .replace(QUESTION, question)
			.replace(CTX_VAR, context);
	}
	
	/**
	 * Prepares the param-parser prompt.
	 *
	 * @param jsonString the dataset JSON
	 * @param question the user question
	 * @return the prepared prompt
	 */
	public String prepareParamParserPrompt(String jsonString,String question) {
		return  promptsContentMap.get(PARAM_PARSER_PROMPT) 
                .replace(DATA_SET, jsonString)
                 .replace(QUESTION, question);
	}
	/**
	 * Prepares the match-select-query prompt.
	 *
	 * @param jsonString the dataset JSON
	 * @param question the user question
	 * @return the prepared prompt
	 */
	public String prepareMatchSelectQueryPrompt(String jsonString,String question) {
		return  promptsContentMap.get(MATCH_SELECT_QUERY_PROMPT) 
		.replace(DATA_SET, jsonString)
		 .replace(QUESTION, question);
	}
	/**
	 * Prepares the get-query prompt.
	 *
	 * @param jsonString the dataset JSON
	 * @param question the user question
	 * @return the prepared prompt
	 */
	public String prepareQueryPrompt(String jsonString,String question) {
		return  promptsContentMap.get(GET_QUERY_PROMPT) 
		.replace(DATA_SET, jsonString)
		 .replace(QUESTION, question);
	}
	/**
	 * Prepares the follow-up prompt.
	 *
	 * @param contextData the context data from previous steps
	 * @param lastAnswer the last response
	 * @return the prepared prompt
	 */
	public String prepareFollowupPrompt(String contextData, String lastAnswer) {
		return  promptsContentMap.get(FOLLOWUP_PROMPT).replace(DATA_SET, lastAnswer)		
			.replace(CTX_VAR, contextData);
	}
}

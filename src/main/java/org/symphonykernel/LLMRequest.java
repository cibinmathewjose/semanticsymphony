package org.symphonykernel;

/**
 * Represents a request to a large language model containing a system message,
 * user prompt, optional tools, and model configuration.
 */
public class LLMRequest   {

	/** The system message providing context. */
	String systemMessage;
	/** The user prompt text. */
	String userPrompt;
	/** Optional tool definitions. */
	Object[] tools;
	/** The model name to use. */
	String modelName;

	/** Default constructor. */
	public LLMRequest()
	{}
	

	/**
	 * Constructs an LLMRequest with the specified parameters.
	 *
	 * @param systemMessage the system message
	 * @param userPrompt the user prompt
	 * @param tools optional tool definitions
	 * @param modelName the model name
	 */
	public LLMRequest(String systemMessage, String userPrompt, Object[] tools, String modelName) {
		this.systemMessage = systemMessage;
		this.userPrompt = userPrompt;
		this.tools = tools;
		this.modelName = modelName;
	}


	/** @param systemMessage the system message to set */
	public void setSystemMessage(String systemMessage) {
		if(systemMessage==null)
			this.systemMessage="";
		else
			this.systemMessage = systemMessage;
	}
	
	/** @return the system message */
	public String getSystemMessage() {
		return systemMessage;
	}

	/** @param userPrompt the user prompt to set */
	public void setUserPrompt(String userPrompt) {
		if(userPrompt==null)
			this.userPrompt="";
		else
			this.userPrompt = userPrompt;
	}
	
	/** @return the user prompt */
	public String getUserPrompt() {
		return userPrompt;
	}

	/** @param tools the tool definitions to set */
	public void setTools(Object[] tools) {
		this.tools = tools;
	}
	
	/** @return the tool definitions */
	public Object[] getTools() {
		return tools;
	}

	/** @param modelName the model name to set */
	public void setModelName(String modelName) {
		this.modelName = modelName;
	}

	/** @return the model name */
	public String getModelName() {
		return modelName;
	}
}
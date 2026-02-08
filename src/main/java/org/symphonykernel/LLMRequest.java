package org.symphonykernel;

/**
 * Represents a knowledge entity with a description and data.
 */
public class LLMRequest   {

	String systemMessage;
	String userPrompt;
	Object[] tools;
	String modelName;

	public LLMRequest()
	{}
	
	

	public LLMRequest(String systemMessage, String userPrompt, Object[] tools, String modelName) {
		this.systemMessage = systemMessage;
		this.userPrompt = userPrompt;
		this.tools = tools;
		this.modelName = modelName;
	}


	public void setSystemMessage(String systemMessage) {
		if(systemMessage==null)
			this.systemMessage="";
		else
			this.systemMessage = systemMessage;
	}
	
	public String getSystemMessage() {
		return systemMessage;
	}

	public void setUserPrompt(String userPrompt) {
		if(userPrompt==null)
			this.userPrompt="";
		else
			this.userPrompt = userPrompt;
	}
	
	public String getUserPrompt() {
		return userPrompt;
	}

	public void setTools(Object[] tools) {
		this.tools = tools;
	}
	
	public Object[] getTools() {
		return tools;
	}

	public void setModelName(String modelName) {
		this.modelName = modelName;
	}

	public String getModelName() {
		return modelName;
	}
}
package org.symphonykernel.core;

public interface IAIClient {
    public String evaluatePrompt(String prompt);
    public String execute(String systemMessage, String userPrompt);
    public String execute(String systemMessage, String userPrompt, Object[] tools);
    public String execute(String systemMessage, String userPrompt, Object[] tools, String modelName);
     public String execute(String systemMessage, String userPrompt, String modelName);
    public String processImage(String systemMessage, String base64Image);
}

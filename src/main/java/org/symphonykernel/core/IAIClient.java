package org.symphonykernel.core;

import org.symphonykernel.LLMRequest;

import reactor.core.publisher.Flux;

public interface IAIClient {
    public String evaluatePrompt(String prompt);
    Flux<String> streamEvaluatePrompt(String prompt);
    public String execute(LLMRequest request);
    Flux<String> streamExecute(LLMRequest request);
    public String processImage(String systemMessage, String base64Image);
}

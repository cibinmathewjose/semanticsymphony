package org.symphonykernel.core;

import org.symphonykernel.LLMRequest;

import reactor.core.publisher.Flux;

public interface IAIClient {
    String evaluatePrompt(String prompt);
    Flux<String> streamEvaluatePrompt(String prompt);
    
    String execute(LLMRequest request);
    Flux<String> streamExecute(LLMRequest request);
    
    String processImage(String systemMessage, String base64Image);
}

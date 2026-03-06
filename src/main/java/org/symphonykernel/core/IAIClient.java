package org.symphonykernel.core;

import org.symphonykernel.LLMRequest;

import reactor.core.publisher.Flux;

/**
 * Interface for AI client implementations supporting prompt evaluation,
 * LLM request execution, and image processing.
 */
public interface IAIClient {
    /**
     * Evaluates a prompt and returns the result.
     *
     * @param prompt the prompt text
     * @return the LLM response
     */
    String evaluatePrompt(String prompt);
    /**
     * Evaluates a prompt and streams the result.
     *
     * @param prompt the prompt text
     * @return a Flux emitting response chunks
     */
    Flux<String> streamEvaluatePrompt(String prompt);
    
    /**
     * Executes an LLM request and returns the result.
     *
     * @param request the LLM request
     * @return the response string
     */
    String execute(LLMRequest request);
    /**
     * Executes an LLM request and streams the result.
     *
     * @param request the LLM request
     * @return a Flux emitting response chunks
     */
    Flux<String> streamExecute(LLMRequest request);
    
    /**
     * Processes an image with a system message.
     *
     * @param systemMessage the system message for context
     * @param base64Image the base64-encoded image
     * @return the LLM response
     */
    String processImage(String systemMessage, String base64Image);
}

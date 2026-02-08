package org.symphonykernel.steps;


import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import org.symphonykernel.Knowledge;
import org.symphonykernel.LLMRequest;
import org.symphonykernel.core.IAIClient;
import org.symphonykernel.core.IPluginLoader;
import org.symphonykernel.core.IStep;
import org.symphonykernel.core.IknowledgeBase;
import org.symphonykernel.transformer.TemplateResolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;

import reactor.core.publisher.Flux;

/**
 * PluginStep is responsible for executing plugins using Azure OpenAI services.
 * It integrates with the Symphony Kernel to process chat responses and queries.
 */
@Component
public class ToolStep extends BaseStep {

    @Autowired
    IPluginLoader pluginLoader;

    @Autowired
    TemplateResolver templateResolver;

    @Autowired
    IknowledgeBase knowledgeBase;

    ChatCompletionService chat;
    
    @Autowired
    IAIClient azureOpenAIHelper;

    @Override
    public ChatResponse getResponse(ExecutionContext context) {

        ChatResponse a = new ChatResponse();
        Knowledge kb = context.getKnowledge();
        if (kb == null && context.getName() != null) {
            kb = knowledgeBase.GetByName(context.getName());
            context.setKnowledge(kb);
        }
        logger.info("Executing Tool " + context.getKnowledge().getName());
        JsonNode paramNode = getParamNode(context.getKnowledge().getData());
        String plugin = paramNode.get("Tool").asText();
        
        if (plugin == null || plugin.isEmpty()) {
            String systemPrompt = null;
            if (paramNode.has("SystemPrompt") && !paramNode.get("SystemPrompt").isNull()) {
                systemPrompt = paramNode.get("SystemPrompt").asText();
            }
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                if (TemplateResolver.hasPlaceholders(systemPrompt)) {
                    Map<String, JsonNode> resolvedValues = context.getResolvedValues();
                    systemPrompt = templateResolver.resolvePlaceholders(systemPrompt, resolvedValues);
                }
                String result = azureOpenAIHelper.execute(new LLMRequest(systemPrompt, "", null, context.getModelName()));
                a = makeResponseObject(result);
            } else {
                throw new IllegalArgumentException("Tool or system prompt not specified in knowledge data");
            }
        } else {
            logger.info("Using Tool: " + plugin);
            IStep step = pluginLoader.createObject(plugin, IStep.class);
            if (step == null) {
                logger.error("Plugin of type IStep not found: " + plugin);
                throw new IllegalArgumentException("Plugin not found: " + plugin);
            }
            a = step.getResponse(context);
        }
        saveStepData(context, a.getData());
        return a;
    }
    @Override
    public Flux<String> getResponseStream(ExecutionContext context) {
    	 Knowledge kb = context.getKnowledge();
         if (kb == null && context.getName() != null) {
             kb = knowledgeBase.GetByName(context.getName());
             context.setKnowledge(kb);
         }
         logger.info("Executing Tool " + context.getKnowledge().getName());
         JsonNode paramNode = getParamNode(context.getKnowledge().getData());
         String plugin = paramNode.get("Tool").asText();
         StringBuilder responseAccumulator = new StringBuilder();
         if (plugin == null || plugin.isEmpty()) {
             String systemPrompt = null;
             if (paramNode.has("SystemPrompt") && !paramNode.get("SystemPrompt").isNull()) {
                 systemPrompt = paramNode.get("SystemPrompt").asText();
             }
             if (systemPrompt != null && !systemPrompt.isEmpty()) {
                 if (TemplateResolver.hasPlaceholders(systemPrompt)) {
                     Map<String, JsonNode> resolvedValues = context.getResolvedValues();
                     systemPrompt = templateResolver.resolvePlaceholders(systemPrompt, resolvedValues);
                 }
                 return azureOpenAIHelper.streamExecute(new LLMRequest(systemPrompt, "", null, context.getModelName())).doOnNext(responseAccumulator::append)
                         .doFinally(signalType -> {
                             saveStepData(context, responseAccumulator.toString());
                         });
             } else {
                 throw new IllegalArgumentException("Tool or system prompt not specified in knowledge data");
             }
         } else {
             logger.info("Using Tool: " + plugin);
             IStep step = pluginLoader.createObject(plugin, IStep.class);
             if (step == null) {
                 logger.error("Plugin of type IStep not found: " + plugin);
                 throw new IllegalArgumentException("Plugin not found: " + plugin);
             }
             return step.getResponseStream(context).doOnNext(responseAccumulator::append) // Capture each chunk as it flies by
            		 .doFinally(signalType -> {
            	 saveStepData(context, responseAccumulator.toString());
             });
         }
        
	} 	
   
}